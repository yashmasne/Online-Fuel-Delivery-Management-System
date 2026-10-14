package util;

import java.math.BigDecimal;

/**
 * Straight-line distance between two GPS points, used to pick the nearest
 * available delivery agent to a customer's shared location. This is "as the
 * crow flies", not actual road/route distance - good enough for choosing
 * between a handful of agents without needing a paid routing API.
 */
public final class GeoUtils {

    private static final double EARTH_RADIUS_KM = 6371.0;

    private GeoUtils() {
    }

    public static double distanceKm(BigDecimal lat1, BigDecimal lon1, BigDecimal lat2, BigDecimal lon2) {
        double la1 = Math.toRadians(lat1.doubleValue());
        double la2 = Math.toRadians(lat2.doubleValue());
        double dLat = la2 - la1;
        double dLon = Math.toRadians(lon2.doubleValue() - lon1.doubleValue());

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(la1) * Math.cos(la2) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.asin(Math.min(1, Math.sqrt(a)));
        return EARTH_RADIUS_KM * c;
    }
}
