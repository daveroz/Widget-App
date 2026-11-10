package app.currencyexchange.widget;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Locale;

/** Settings + cached network data, all in one SharedPreferences file. */
final class Store {
    private Store() {}

    static final String[] DEF_CITIES = {"Mexico City", "Bucharest", "Brussels", "Charlotte"};
    static final String[] DEF_FROM = {"USD", "EUR", "GBP", "CHF", "CNY", "JPY"};

    static SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences("cfg", Context.MODE_PRIVATE);
    }

    // ---- settings ----
    static String city(Context c, int i) { return sp(c).getString("city" + i, DEF_CITIES[i]); }
    /** How many currency rows are configured (4 by default, up to 6). The widget shows as many as fit. */
    static int count(Context c) { return Math.max(1, Math.min(DEF_FROM.length, sp(c).getInt("fxCount", 4))); }
    static void setCount(Context c, int n) { sp(c).edit().putInt("fxCount", Math.max(1, Math.min(DEF_FROM.length, n))).apply(); }

    static String from(Context c, int i) { return sp(c).getString("fx" + i, DEF_FROM[i]); }
    static String to(Context c) { return sp(c).getString("to", "COP"); }
    /** Visa fee as plain text, never with a trailing ".0" ("2", not "2.0"). */
    static String feeText(Context c) { return Util.cleanNum(sp(c).getString("feeTxt", "2")); }
    static double fee(Context c) {
        double v = Util.parseNum(feeText(c));
        return Double.isNaN(v) ? 2.0 : v;
    }
    /** SBB clock second hand: "smooth" (about 10 pictures a second), "second" (1 a second) or "minute" (none). */
    static String sbbMode(Context c) { return sp(c).getString("sbbMode", "smooth"); }
    /** Calendar widget: which of the next 5 events is shown. */
    static int calIdx(Context c) { return sp(c).getInt("calIdx", 0); }
    static void setCalIdx(Context c, int i) { sp(c).edit().putInt("calIdx", i).apply(); }

    static void setDeviceLocation(Context c, double lat, double lon) {
        sp(c).edit().putString("devLat", String.valueOf(lat)).putString("devLon", String.valueOf(lon)).apply();
    }

    /** {lat, lon} of where you are (last known device location) for weather + sunrise/sunset. */
    static double[] weatherLatLon(Context c) {
        try {
            String la = sp(c).getString("devLat", null), lo = sp(c).getString("devLon", null);
            if (la != null && lo != null) return new double[]{Double.parseDouble(la), Double.parseDouble(lo)};
        } catch (NumberFormatException ignored) { }
        return new double[]{Double.NaN, Double.NaN};
    }

    // ---- cache ----
    /** Last clock update result, shown in the app's Status card. */
    static void diag(Context c, String key, long when, String who, String err) {
        sp(c).edit().putLong(key + "At", when).putString(key + "Who", who).putString(key + "Msg", err == null ? "" : err).apply();
    }

    static long lastFetch(Context c) { return sp(c).getLong("lastFetch", 0L); }
    static void setLastFetch(Context c) { sp(c).edit().putLong("lastFetch", System.currentTimeMillis()).apply(); }

    private static String key(String s) { return s.trim().toLowerCase(Locale.ROOT); }

    static JSONObject geo(Context c, String city) {
        String s = sp(c).getString("geo_" + key(city), null);
        if (s == null) return null;
        try { return new JSONObject(s); } catch (Exception e) { return null; }
    }
    static void putGeo(Context c, String city, JSONObject o) {
        sp(c).edit().putString("geo_" + key(city), o.toString()).apply();
    }

    static double rate(Context c, String from, String to) {
        return Double.longBitsToDouble(sp(c).getLong("rate_" + key(from) + "_" + key(to), Double.doubleToLongBits(Double.NaN)));
    }
    static void putRate(Context c, String from, String to, double v) {
        sp(c).edit().putLong("rate_" + key(from) + "_" + key(to), Double.doubleToLongBits(v)).apply();
    }

    static double visa(Context c, String from, String to) {
        return Double.longBitsToDouble(sp(c).getLong("visa_" + key(from) + "_" + key(to), Double.doubleToLongBits(Double.NaN)));
    }
    static void putVisa(Context c, String from, String to, double v) {
        sp(c).edit().putLong("visa_" + key(from) + "_" + key(to), Double.doubleToLongBits(v)).apply();
    }

    /**
     * Daily market rates into the target currency, for the trend behind each row:
     * {"yyyy-MM-dd": {"usd": 3921.4, "eur": 4410.2, ...}, ...}; 0 marks a currency the source had no rate for.
     */
    static JSONObject hist(Context c, String to) {
        String s = sp(c).getString("hist_" + key(to), null);
        if (s != null) {
            try { return new JSONObject(s); } catch (Exception ignored) { }
        }
        return new JSONObject();
    }
    /** Saves the history for the target currency and drops histories of earlier targets. */
    static void putHist(Context c, String to, JSONObject o) {
        String k = "hist_" + key(to);
        SharedPreferences.Editor e = sp(c).edit();
        for (String old : sp(c).getAll().keySet()) if (old.startsWith("hist_") && !old.equals(k)) e.remove(old);
        e.putString(k, o.toString()).apply();
    }

    /** Weather where you are: temperature (deg C), the WMO weather code and the hourly chance of rain. */
    static final class Weather {
        final double temp;
        final int code;          // WMO weather interpretation code, -1 if unknown
        final long[] hours;      // start of each forecast hour, epoch seconds
        final int[] rain;        // chance of precipitation per hour (%), -1 if unknown

        Weather(double temp, int code, long[] hours, int[] rain) {
            this.temp = temp; this.code = code; this.hours = hours; this.rain = rain;
        }

        /** Highest chance of rain (%) from the current hour through the next {@code ahead} hours; -1 if unknown. */
        int rainChance(long nowMs, int ahead) {
            long now = nowMs / 1000L;
            int best = -1;
            for (int i = 0; i < hours.length; i++) {
                if (hours[i] + 3600L > now && hours[i] < now + ahead * 3600L) best = Math.max(best, rain[i]);
            }
            return best;
        }
    }

    /** Null until the first fetch (and for the older humidity-only cache, so it is fetched again). */
    static Weather weather(Context c) {
        String s = sp(c).getString("wx", null);
        if (s == null) return null;
        try {
            JSONObject o = new JSONObject(s);
            if (!o.has("temp")) return null;
            JSONArray t = o.optJSONArray("t"), p = o.optJSONArray("p");
            int n = t == null || p == null ? 0 : Math.min(t.length(), p.length());
            long[] hours = new long[n];
            int[] rain = new int[n];
            for (int i = 0; i < n; i++) {
                hours[i] = t.getLong(i);
                rain[i] = p.optInt(i, -1);
            }
            return new Weather(o.getDouble("temp"), o.optInt("code", -1), hours, rain);
        } catch (Exception e) { return null; }
    }

    /** hours = epoch seconds of each forecast hour, rain = chance of precipitation for each (JSON nulls allowed). */
    static void putWeather(Context c, double temp, int code, JSONArray hours, JSONArray rain) {
        try {
            JSONObject o = new JSONObject().put("temp", temp).put("code", code);
            if (hours != null && rain != null) o.put("t", hours).put("p", rain);
            sp(c).edit().putString("wx", o.toString()).apply();
        } catch (Exception ignored) { }
    }
}
