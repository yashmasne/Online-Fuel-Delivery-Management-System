package util;

import java.math.BigDecimal;
import java.sql.Timestamp;

/**
 * Minimal, dependency-free JSON string-building helpers.
 *
 * Pages are static HTML (not JSP), so servlets return JSON and let the
 * page's own JavaScript render it (fetch + DOM update), rather than
 * forwarding to a JSP view.
 */
public class JsonUtil {
    private JsonUtil() {
    }

    /** Escapes a value for safe placement inside a JSON string literal. */
    public static String escape(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (char c : value.toCharArray()) {
            switch (c) {
                case '"':  sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n");  break;
                case '\r': sb.append("\\r");  break;
                case '\t': sb.append("\\t");  break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }

    /** Renders "key":"value" with the value escaped and quoted; null -> "key":null */
    public static String kv(String key, String value) {
        return "\"" + key + "\":" + (value == null ? "null" : "\"" + escape(value) + "\"");
    }

    /** Renders "key":123 style pairs for numbers; null -> "key":null */
    public static String kv(String key, Number value) {
        return "\"" + key + "\":" + (value == null ? "null" : value.toString());
    }

    /** Renders "key":true / "key":false */
    public static String kv(String key, boolean value) {
        return "\"" + key + "\":" + value;
    }

    /** Renders "key":"2026-07-20 09:15:00" for a nullable Timestamp, or "key":null */
    public static String kv(String key, Timestamp value) {
        return "\"" + key + "\":" + (value == null ? "null" : "\"" + value.toString() + "\"");
    }

    /** Renders "key":123.45 for a nullable BigDecimal, or "key":null */
    public static String kvDecimal(String key, BigDecimal value) {
        return "\"" + key + "\":" + (value == null ? "null" : value.toPlainString());
    }

    /** Wraps a comma-joined list of kv() fragments into a single JSON object string. */
    public static String object(String... fields) {
        return "{" + String.join(",", fields) + "}";
    }

    /** Wraps a comma-joined list of object() fragments into a single JSON array string. */
    public static String array(Iterable<String> objects) {
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (String obj : objects) {
            if (!first) {
                sb.append(",");
            }
            sb.append(obj);
            first = false;
        }
        sb.append("]");
        return sb.toString();
    }
}
