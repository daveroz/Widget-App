package app.currencyexchange.widget;

import android.content.Context;

import org.json.JSONObject;

import java.net.URLEncoder;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

/**
 * Pulls the data the widget shows. Every source is free and needs NO account or API key:
 *  - Open-Meteo geocoding  : city -> lat / lon / timezone / country
 *  - Open-Meteo forecast   : temperature, weather condition + hourly chance of rain
 *  - fawazahmed0 currency-api (CDN), fallback open.er-api.com : market exchange rates
 *  - fawazahmed0 currency-api dated files : the last month of daily rates, for the trend behind each row
 *  - Visa public FX endpoint (same one the KWGT preset used); if Visa refuses, the widget shows an
 *    estimate (market rate + your fee, marked with "~")
 * Results are cached in {@link Store}; each call is independent so one failure never blocks the rest.
 * Must run on a background thread.
 */
final class Fetcher {
    private Fetcher() {}

    private static final long WEATHER_MIN_GAP_MS = 10L * 60_000L;

    static void refreshAll(Context ctx) {
        refreshAll(ctx, false);
    }

    /** @param force true when the user asked for it (Save & refresh): ignores the weather minimum gap */
    static void refreshAll(Context ctx, boolean force) {
        boolean any = false;
        Locator.refreshFromLastKnown(ctx); // best effort: the system may refuse this while the app is in the background
        String to = Store.to(ctx).trim();
        StringBuilder geoErr = new StringBuilder();

        // Cities: geocode (cached forever per name) + flags
        for (int i = 0; i < 4; i++) {
            String city = Store.city(ctx, i);
            try {
                if (Store.geo(ctx, city) == null) geocode(ctx, city);
                JSONObject g = Store.geo(ctx, city);
                if (g != null) {
                    Flags.ensureFlag(ctx, g.optString("cc", ""));
                    any = true;
                }
            } catch (Exception e) {
                geoErr.append(city).append(": ").append(e.getMessage() == null ? "not found" : e.getMessage()).append("; ");
            }
        }
        if (geoErr.length() == 0) Store.sp(ctx).edit().remove("geoErr").apply();
        else Store.sp(ctx).edit().putString("geoErr", geoErr.toString()).apply();

        // Currency rows
        Flags.ensureFlag(ctx, Util.countryOfCurrency(to));
        Flags.ensureVisa(ctx);
        for (int i = 0; i < Store.count(ctx); i++) {
            String from = Store.from(ctx, i).trim();
            Flags.ensureFlag(ctx, Util.countryOfCurrency(from));
            try {
                Store.putRate(ctx, from, to, marketRate(from, to));
                any = true;
            } catch (Exception ignored) { }
            double v = visaRate(ctx, from, to);
            if (!Double.isNaN(v)) Store.putVisa(ctx, from, to, v);
        }

        // Weather
        try {
            double[] ll = Store.weatherLatLon(ctx);
            if (Double.isNaN(ll[0])) {
                Store.sp(ctx).edit().putString("wxErr", "no location yet - allow location access in the app").apply();
            } else if (!force && Store.weather(ctx) != null
                    && System.currentTimeMillis() - Store.sp(ctx).getLong("wxTs", 0L) < WEATHER_MIN_GAP_MS) {
                any = true; // fresh enough
            } else {
                Store.sp(ctx).edit().putLong("wxTs", System.currentTimeMillis()).apply();
                weather(ctx, ll[0], ll[1]);
                Store.sp(ctx).edit().remove("wxErr").apply();
                any = true;
            }
        } catch (Exception e) {
            Store.sp(ctx).edit().putString("wxErr", e.getMessage() == null ? e.toString() : e.getMessage()).apply();
        }

        if (any) Store.setLastFetch(ctx);
    }

    /** Open-Meteo geocoding: free, no key. Gives coordinates, IANA timezone and country code in one call. */
    private static void geocode(Context ctx, String city) throws Exception {
        if (city.trim().isEmpty()) return;
        String url = "https://geocoding-api.open-meteo.com/v1/search?name=" + URLEncoder.encode(city.trim(), "UTF-8")
                + "&count=1&language=en&format=json";
        JSONObject root = new JSONObject(Net.get(url));
        if (!root.has("results")) throw new Exception("city not found");
        JSONObject r = root.getJSONArray("results").getJSONObject(0);
        JSONObject o = new JSONObject();
        o.put("lat", r.getDouble("latitude"));
        o.put("lon", r.getDouble("longitude"));
        o.put("tz", r.getString("timezone"));
        o.put("cc", r.optString("country_code", ""));
        Store.putGeo(ctx, city, o);
    }

    /** Market rate from -> to. Primary and mirror: fawazahmed0 currency-api. Last resort: open.er-api.com. */
    private static double marketRate(String from, String to) throws Exception {
        String f = from.toLowerCase(Locale.ROOT), t = to.toLowerCase(Locale.ROOT);
        String[] bases = {
                "https://cdn.jsdelivr.net/npm/@fawazahmed0/currency-api@latest/v1/currencies/",
                "https://latest.currency-api.pages.dev/v1/currencies/"
        };
        Exception last = null;
        for (String b : bases) {
            try {
                return new JSONObject(Net.get(b + f + ".json")).getJSONObject(f).getDouble(t);
            } catch (Exception e) { last = e; }
        }
        try {
            return new JSONObject(Net.get("https://open.er-api.com/v6/latest/" + from.toUpperCase(Locale.ROOT)))
                    .getJSONObject("rates").getDouble(to.toUpperCase(Locale.ROOT));
        } catch (Exception e) { last = e; }
        throw last;
    }

    /**
     * History for the trend behind each currency row (the last {@link Trend#DAYS} days, one day in every
     * {@link Trend#STEP}), from the dated files of the same fawazahmed0 currency-api. One request per sampled day
     * covers every row: the file for the target currency lists the rates of all currencies into it. Past days never
     * change, so each is fetched once: about 30 requests for the first fill, then one small request a day. Kept
     * apart from {@link #refreshAll} so rates and weather never wait for it. Returns true when something new was stored (redraw the widget then).
     */
    static boolean refreshHistory(Context ctx) {
        String to = Store.to(ctx).trim().toLowerCase(Locale.ROOT);
        List<String> need = new ArrayList<>();
        for (int i = 0; i < Store.count(ctx); i++) {
            String f = Store.from(ctx, i).trim().toLowerCase(Locale.ROOT);
            if (!f.isEmpty() && !f.equals(to) && !need.contains(f)) need.add(f);
        }
        if (to.isEmpty() || need.isEmpty()) return false;
        JSONObject hist = Store.hist(ctx, to);
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        long deadline = System.currentTimeMillis() + 25_000L;
        boolean changed = false;
        int fails = 0;
        for (int back = 1; back <= Trend.DAYS; back++) {
            if (!Trend.sampleDay(today.minusDays(back))) continue;
            String d = today.minusDays(back).toString();
            JSONObject day = hist.optJSONObject(d);
            if (day != null && hasAll(day, need)) continue;
            if (fails >= 3 || System.currentTimeMillis() > deadline) break; // offline or slow: next refresh goes on
            try {
                JSONObject all = dayRates(d, to);
                if (all == null) {
                    // No file for that date. A very recent one may not be published yet; an older gap stays a gap.
                    if (back <= 2) continue;
                    all = new JSONObject();
                }
                JSONObject keep = day == null ? new JSONObject() : day;
                for (String f : need) {
                    double r = all.optDouble(f, Double.NaN);
                    keep.put(f, r > 0 ? 1.0 / r : 0); // 0 = no rate that day, so it isn't asked for again
                }
                hist.put(d, keep);
                changed = true;
            } catch (Exception e) {
                fails++;
            }
        }
        String oldest = today.minusDays(Trend.DAYS).toString();
        for (Iterator<String> it = hist.keys(); it.hasNext(); ) {
            String d = it.next();
            boolean keep;
            try {
                keep = d.compareTo(oldest) >= 0 && Trend.sampleDay(LocalDate.parse(d));
            } catch (Exception e) {
                keep = false;
            }
            if (!keep) { // slid out of the window, or not a sample day (e.g. from an older version)
                it.remove();
                changed = true;
            }
        }
        if (changed) Store.putHist(ctx, to, hist);
        return changed;
    }

    private static boolean hasAll(JSONObject day, List<String> need) {
        for (String f : need) if (!day.has(f)) return false;
        return true;
    }

    /** All rates of one day as {currency: units of it per 1 cur}; null if the source has no file for that date. */
    private static JSONObject dayRates(String date, String cur) throws Exception {
        String[] urls = {
                "https://cdn.jsdelivr.net/npm/@fawazahmed0/currency-api@" + date + "/v1/currencies/" + cur + ".json",
                "https://" + date + ".currency-api.pages.dev/v1/currencies/" + cur + ".json"
        };
        Exception last = null;
        boolean missing = false;
        for (String u : urls) {
            try {
                return new JSONObject(Net.get(u)).getJSONObject(cur);
            } catch (Exception e) {
                last = e;
                if (String.valueOf(e.getMessage()).startsWith("HTTP 404")) missing = true;
            }
        }
        if (missing) return null;
        throw last;
    }

    /**
     * Same endpoint as the KWGT preset (fromCurr=TARGET, toCurr=SOURCE), same parameters (fee=2 and a
     * MM/dd/yyyy date with raw slashes) and a plain request with no browser headers, plus a walk back
     * through older dates (Visa publishes with a delay and not on weekends). The last error is stored so
     * the settings screen can show it. Returns NaN when Visa does not answer; the widget then shows an estimate.
     */
    private static double visaRate(Context ctx, String from, String to) {
        String[] hosts = {"https://www.visa.com.au"}; // the same host as the original KWGT formula
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MM/dd/yyyy", Locale.US);
        String fee = Store.feeText(ctx);
        String err = "no response";
        for (int back = 0; back <= 4; back++) {
            boolean retryOlder = false; // server answered but has no rate for this date
            String d = LocalDate.now().minusDays(back).format(fmt);
            for (String host : hosts) {
                try {
                    String url = host + "/cmsapi/fx/rates?amount=1&fee=" + fee
                            + "&utcConvertedDate=" + d
                            + "&exchangedate=" + d
                            + "&fromCurr=" + to.toUpperCase(Locale.ROOT)
                            + "&toCurr=" + from.toUpperCase(Locale.ROOT);
                    String body = Net.getPlain(url); // plain request, no browser disguise (that is what KWGT sends)
                    JSONObject o = new JSONObject(body).optJSONObject("originalValues");
                    if (o == null) {
                        retryOlder = true;
                        err = from + ": no rate for " + d + " (" + body.substring(0, Math.min(100, body.length())) + ")";
                        continue;
                    }
                    double v = Util.parseNum(o.optString("toAmountWithAdditionalFee", ""));
                    if (!Double.isNaN(v)) {
                        Store.sp(ctx).edit().remove("visaErr").apply();
                        return v;
                    }
                    retryOlder = true;
                    err = from + ": empty value for " + d;
                } catch (Exception e) {
                    String m = String.valueOf(e.getMessage());
                    err = from + " " + host.replace("https://", "") + " " + d + ": " + m;
                    // 400/404 = no rate for that date: try an older one. 403/429/timeouts: older dates won't help.
                    if (m.startsWith("HTTP 404") || m.startsWith("HTTP 400")) retryOlder = true;
                }
            }
            if (!retryOlder) break;
        }
        Store.sp(ctx).edit().putString("visaErr", err + "  (showing estimate: market rate + fee)").apply();
        return Double.NaN;
    }

    /**
     * Open-Meteo forecast: free, no key. Current temperature and weather code, plus the chance of rain
     * for each hour of today and tomorrow (unix times, so the widget can pick the coming hours when it draws).
     */
    private static void weather(Context ctx, double lat, double lon) throws Exception {
        String url = "https://api.open-meteo.com/v1/forecast?latitude=" + String.format(Locale.US, "%.4f", lat)
                + "&longitude=" + String.format(Locale.US, "%.4f", lon)
                + "&current=temperature_2m,weather_code"
                + "&hourly=precipitation_probability&forecast_days=2&timeformat=unixtime&timezone=auto";
        JSONObject root = new JSONObject(Net.get(url));
        JSONObject cur = root.getJSONObject("current");
        JSONObject hourly = root.optJSONObject("hourly");
        Store.putWeather(ctx, cur.getDouble("temperature_2m"), cur.optInt("weather_code", -1),
                hourly == null ? null : hourly.optJSONArray("time"),
                hourly == null ? null : hourly.optJSONArray("precipitation_probability"));
    }
}
