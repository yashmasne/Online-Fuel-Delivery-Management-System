package util;

import dao.FuelTypeDAO;
import model.FuelType;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pulls today's Pune petrol/diesel/CNG retail price from a public fuel-price
 * page and writes it into the fuel_types table, so "Today's Fuel Prices" on
 * the homepage reflects real pump prices instead of a number someone typed
 * in once and forgot about.
 *
 * There is no free, official, key-free API for Indian retail fuel prices
 * (the ones that exist are paid), so this reads the same figure a visitor
 * would see on a public fuel-price page - specifically its SEO meta
 * description, which is the most stable part of the page to parse and is
 * present in the raw HTML without needing to run any JavaScript.
 *
 * If the source page is ever unreachable, slow, or changes shape, the last
 * successfully fetched price (and its timestamp) is simply left in the
 * database - the site always shows *a* real price, just possibly a little
 * older, never a broken page. LPG has no public retail feed like this and
 * stays admin-managed, same as before.
 */
public final class LiveFuelPriceFetcher {

    private static final String SOURCE_URL = "https://www.carchhe.com/petrol-price/maharashtra/pune";

    // Don't hit the source page more than once per window - prices only change once a day
    // (usually 6 AM IST) and this keeps the fetch considerate of the source site.
    private static final long STALE_AFTER_MS = 6L * 60 * 60 * 1000;

    private static final Pattern META_DESCRIPTION = Pattern.compile(
            "<meta[^>]+name=[\"']description[\"'][^>]+content=[\"']([^\"']*)[\"']",
            Pattern.CASE_INSENSITIVE);

    // Matches "...Petrol - <curr> 111.90 /L, Diesel - <curr> 98.55 /L & CNG - <curr> 98.50 /KG..."
    // tolerating "&" vs "&amp;", extra spaces, and either currency symbol or none.
    private static final Pattern PRICE_TRIPLE = Pattern.compile(
            "Petrol\\s*-\\s*[^0-9]*([0-9]+(?:\\.[0-9]+)?)\\s*/\\s*L.*?"
                    + "Diesel\\s*-\\s*[^0-9]*([0-9]+(?:\\.[0-9]+)?)\\s*/\\s*L.*?"
                    + "CNG\\s*-\\s*[^0-9]*([0-9]+(?:\\.[0-9]+)?)\\s*/\\s*KG",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private LiveFuelPriceFetcher() {
    }

    /**
     * Refreshes petrol/diesel/CNG entries in {@code fuelTypes} (in place) and in the database
     * if the cached price is missing or older than the refresh window. Never throws - any
     * problem fetching or parsing the source page just means today's already-stored prices
     * are left exactly as they were.
     */
    public static void refreshIfStale(FuelTypeDAO dao, List<FuelType> fuelTypes) {
        FuelType petrol = findByKeyword(fuelTypes, "petrol");
        boolean stale = petrol == null
                || petrol.getPriceUpdatedAt() == null
                || System.currentTimeMillis() - petrol.getPriceUpdatedAt().getTime() > STALE_AFTER_MS;
        if (!stale) {
            return;
        }

        try {
            LivePrices live = fetchLivePrices();
            Timestamp fetchedAt = new Timestamp(System.currentTimeMillis());

            applyIfPresent(dao, fuelTypes, "petrol", live.petrolPerLitre, fetchedAt);
            applyIfPresent(dao, fuelTypes, "diesel", live.dieselPerLitre, fetchedAt);
            applyIfPresent(dao, fuelTypes, "cng", live.cngPerKg, fetchedAt);
        } catch (Exception e) {
            // Source page unreachable / slow / layout changed - keep serving the last known price.
            System.err.println("LiveFuelPriceFetcher: could not refresh live prices - " + e.getMessage());
        }
    }

    private static void applyIfPresent(FuelTypeDAO dao, List<FuelType> fuelTypes, String keyword,
                                        BigDecimal newPrice, Timestamp fetchedAt) throws SQLException {
        FuelType match = findByKeyword(fuelTypes, keyword);
        if (match == null) {
            return; // this catalog doesn't have that fuel type (yet) - nothing to update
        }
        dao.updateLivePrice(match.getFuelId(), newPrice, fetchedAt);
        match.setPricePerLitre(newPrice);
        match.setPriceUpdatedAt(fetchedAt);
    }

    private static FuelType findByKeyword(List<FuelType> fuelTypes, String keyword) {
        for (FuelType f : fuelTypes) {
            if (f.getFuelName() != null && f.getFuelName().toLowerCase().contains(keyword)) {
                return f;
            }
        }
        return null;
    }

    private static LivePrices fetchLivePrices() throws IOException, InterruptedException {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(8))
                .build();
        HttpRequest request = HttpRequest.newBuilder(URI.create(SOURCE_URL))
                .header("User-Agent", "Mozilla/5.0 (compatible; FuelExpressPriceBot/1.0)")
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("Unexpected HTTP status " + response.statusCode() + " from price source");
        }

        Matcher descriptionMatcher = META_DESCRIPTION.matcher(response.body());
        String haystack = descriptionMatcher.find() ? descriptionMatcher.group(1) : response.body();

        Matcher priceMatcher = PRICE_TRIPLE.matcher(haystack);
        if (!priceMatcher.find()) {
            throw new IOException("Could not find fuel prices on the source page (layout may have changed)");
        }

        return new LivePrices(
                new BigDecimal(priceMatcher.group(1)),
                new BigDecimal(priceMatcher.group(2)),
                new BigDecimal(priceMatcher.group(3))
        );
    }

    private static final class LivePrices {
        final BigDecimal petrolPerLitre;
        final BigDecimal dieselPerLitre;
        final BigDecimal cngPerKg;

        LivePrices(BigDecimal petrolPerLitre, BigDecimal dieselPerLitre, BigDecimal cngPerKg) {
            this.petrolPerLitre = petrolPerLitre;
            this.dieselPerLitre = dieselPerLitre;
            this.cngPerKg = cngPerKg;
        }
    }
}
