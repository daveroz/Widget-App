package app.currencyexchange.widget;

import android.content.Context;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Built-in list of ~480 cities (name, country, time zone, coordinates) so picking a city needs no network. */
final class CityData {
    private CityData() {}

    static final class City {
        final String name, cc, tz;
        final double lat, lon;

        City(String name, String cc, String tz, double lat, double lon) {
            this.name = name; this.cc = cc; this.tz = tz; this.lat = lat; this.lon = lon;
        }

        String country() { return new Locale("", cc).getDisplayCountry(Locale.ENGLISH); }

        JSONObject toGeo() {
            try {
                return new JSONObject().put("lat", lat).put("lon", lon).put("tz", tz).put("cc", cc);
            } catch (Exception e) { return new JSONObject(); }
        }
    }

    private static List<City> cache;

    static synchronized List<City> all(Context ctx) {
        if (cache != null) return cache;
        List<City> out = new ArrayList<>();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(
                ctx.getApplicationContext().getAssets().open("cities.txt"), StandardCharsets.UTF_8))) {
            String l;
            while ((l = r.readLine()) != null) {
                String[] p = l.split("\\|");
                if (p.length < 5) continue;
                out.add(new City(p[0], p[1], p[2], Double.parseDouble(p[3]), Double.parseDouble(p[4])));
            }
        } catch (Exception ignored) { }
        cache = out;
        return out;
    }
}
