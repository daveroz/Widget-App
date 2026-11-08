package app.currencyexchange.widget;

import java.text.NumberFormat;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Pure helpers (no Android dependencies). */
final class Util {
    private Util() {}

    private static final Map<String, String> CC = new HashMap<>();

    static {
        String[] p = {
                "USD", "us", "EUR", "eu", "GBP", "gb", "COP", "co", "DKK", "dk", "RON", "ro",
                "MXN", "mx", "CAD", "ca", "AUD", "au", "NZD", "nz", "CHF", "ch", "SEK", "se",
                "NOK", "no", "PLN", "pl", "CZK", "cz", "HUF", "hu", "BGN", "bg", "JPY", "jp",
                "CNY", "cn", "INR", "in", "BRL", "br", "ARS", "ar", "CLP", "cl", "PEN", "pe",
                "UYU", "uy", "AED", "ae", "TRY", "tr", "ZAR", "za", "KRW", "kr", "THB", "th",
                "SGD", "sg", "HKD", "hk", "ILS", "il", "ISK", "is", "RUB", "ru", "UAH", "ua",
                "PHP", "ph", "IDR", "id", "MYR", "my", "EGP", "eg", "CRC", "cr", "DOP", "do",
                "PAB", "pa", "BOB", "bo", "PYG", "py", "GTQ", "gt", "VES", "ve", "SAR", "sa"
        };
        for (int i = 0; i + 1 < p.length; i += 2) CC.put(p[i], p[i + 1]);
    }

    /** ISO country code (lowercase) whose flag represents a currency. */
    static String countryOfCurrency(String cur) {
        if (cur == null) return "";
        String u = cur.trim().toUpperCase(Locale.ROOT);
        String cc = CC.get(u);
        if (cc != null) return cc;
        return u.length() >= 2 ? u.substring(0, 2).toLowerCase(Locale.ROOT) : "";
    }

    /** Lenient number parser: accepts "3,350.12", " 12 ", etc. NaN on failure. */
    static double parseNum(String s) {
        if (s == null) return Double.NaN;
        String t = s.replaceAll("[^0-9.\\-]", "");
        if (t.isEmpty() || t.equals("-") || t.equals(".")) return Double.NaN;
        try {
            return Double.parseDouble(t);
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }

    /** "2.0" -> "2", "2.50" -> "2.5", "2,5" -> "2.5", "" -> "2" (default fee). Plain text, no forced decimals. */
    static String cleanNum(String s) {
        if (s == null) return "2";
        String t = s.trim().replace(',', '.');
        if (t.isEmpty()) return "2";
        if (t.indexOf('.') >= 0) {
            t = t.replaceAll("0+$", "");
            if (t.endsWith(".")) t = t.substring(0, t.length() - 1);
        }
        return t.isEmpty() ? "0" : t;
    }

    /** "3,281" style, no decimals; "--" when unknown. */
    static String fmt0(double v) {
        if (Double.isNaN(v) || Double.isInfinite(v)) return "--";
        return NumberFormat.getIntegerInstance(Locale.US).format(Math.round(v));
    }

    /** "mexico city" -> "Mexico City". */
    static String capWords(String s) {
        if (s == null) return "";
        StringBuilder b = new StringBuilder();
        boolean up = true;
        for (char ch : s.trim().toCharArray()) {
            b.append(up ? Character.toUpperCase(ch) : ch);
            up = Character.isWhitespace(ch) || ch == '-';
        }
        return b.toString();
    }

    /**
     * Visa's endpoint direction is ambiguous (units of A per B or B per A). Pick whichever of
     * v or 1/v is closer (in log distance) to the market rate so both readings display sanely.
     */
    static double alignToMarket(double v, double market) {
        if (Double.isNaN(v) || v <= 0) return Double.NaN;
        if (Double.isNaN(market) || market <= 0) return v;
        double d1 = Math.abs(Math.log(v / market));
        double d2 = Math.abs(Math.log((1.0 / v) / market));
        return d2 < d1 ? 1.0 / v : v;
    }

    /** "5:43" / "17:39" from minutes since midnight; "--:--" if invalid. */
    static String hm(int minutes) {
        if (minutes < 0) return "--:--";
        return String.format(Locale.US, "%d:%02d", (minutes / 60) % 24, minutes % 60);
    }

    /** KWGT $tc(ell, text, n)$: cut to n characters and end with an ellipsis. */
    static String ellipsize(String s, int n) {
        if (s == null || s.length() <= n) return s == null ? "" : s;
        return s.substring(0, n).trim() + "\u2026";
    }
}
