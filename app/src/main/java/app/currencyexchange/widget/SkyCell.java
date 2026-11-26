package app.currencyexchange.widget;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.util.LruCache;

import java.time.Instant;
import java.time.ZoneId;

/**
 * One world clock's picture, frame by frame (2.05): its city's sky ({@link Sky}) and corner ({@link CityScene}),
 * built once per light and weather, and put together for each moment: the stars twinkle, a far plane crosses now and
 * then with its contrail, the clouds drift on the wind, the beacons blink, the trees sway (more when it's windy), the
 * rain falls. The clocks at the card's corners have its round corners cut out of them.
 * <p>
 * {@link LiveService} asks for a frame about eight times a second while the screen is on (when skies are animated);
 * otherwise it's one frame a minute, with the clock.
 */
final class SkyCell {
    /** What a cell is built for; a new one is built when any of it changes (the light about every half degree). */
    final String spec;
    private final Sky.Layers sky;
    private final CityScene.Light light;
    private final CityScene.Built built;
    private final ZoneId zone;
    private final long seed;
    private final float px, wind, drift;
    /** The city's country (ISO code, null if unknown) and WMO weather code: for fireworks and lightning (2.27). */
    private String cc;
    private final int code;
    private final Bitmap out;
    private final Canvas canvas;
    private final Path outside;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint clear = new Paint(Paint.ANTI_ALIAS_FLAG);

    private static final LruCache<String, SkyCell> CELLS = new LruCache<>(20); // up to eight clocks (2.26)
    /** The card's corners a cell carries (2.26: any of them, for a grid of clocks). */
    static final int TL = 1, TR = 2, BR = 4, BL = 8;
    /** For test pictures: the moment the sun and moon are placed for, instead of now (0: now). */
    static long clock = 0;

    private static long now() { return clock > 0 ? clock : System.currentTimeMillis(); }

    /**
     * The cell for {@code city} (at {@code lat}, {@code lon}, in {@code zone}), the sun {@code elevation} degrees up, in the
     * morning ({@code sunLeft}) or afternoon, with {@code cloud} % cover, WMO weather {@code code} and {@code wind}
     * km/h (-1 each when unknown); {@code w} × {@code h} pixels at {@code px} per dp; {@code corners} the card's round
     * corners it carries ({@link #TL}, {@link #TR}, {@link #BR}, {@link #BL}; 0 for none), radius {@code r} pixels.
     */
    static synchronized SkyCell get(String city, double lat, double lon, ZoneId zone, double elevation, boolean sunLeft,
                                    int cloud, int code, int wind, int w, int h, float px, int corners, float r) {
        return get(city, null, lat, lon, zone, elevation, sunLeft, cloud, code, wind, w, h, px, corners, r);
    }

    /** As above, for a city in country {@code cc} (its fireworks days, 2.27). */
    static synchronized SkyCell get(String city, String cc, double lat, double lon, ZoneId zone, double elevation,
                                    boolean sunLeft, int cloud, int code, int wind, int w, int h, float px, int corners,
                                    float r) {
        float el = Math.round(elevation * 2) / 2f;
        // the moon where it really is, built again each time it has moved a degree or so (2.08)
        double[] moon = Double.isNaN(lat) || Double.isNaN(lon) ? null : SunCalc.moon(lat, lon, now());
        String moonKey = moon == null || moon[0] < Math.toRadians(-2) ? "-"
                : Math.round(Math.toDegrees(moon[0])) + "," + Math.round(Math.toDegrees(moon[1])) + "," + Math.round(moon[2] * 50);
        String spec = city + "|" + el + "|" + sunLeft + "|" + (cloud < 0 ? -1 : cloud / 5) + "|" + code + "|"
                + (wind < 0 ? -1 : wind / 5) + "|" + w + "x" + h + "|" + px + "|" + corners + "|" + moonKey;
        SkyCell hit = CELLS.get(spec);
        if (hit != null) {
            hit.cc = cc;
            return hit;
        }
        SkyCell made = new SkyCell(spec, city, cc, lat, lon, zone, el, sunLeft, cloud, code, wind, w, h, px, corners, r, moon);
        CELLS.put(spec, made);
        return made;
    }

    private SkyCell(String spec, String city, String cc, double lat, double lon, ZoneId zone, float el, boolean sunLeft,
                    int cloud, int code, int wind, int w, int h, float px, int corners, float r, double[] moon) {
        this.spec = spec;
        this.cc = cc;
        this.zone = zone;
        this.seed = city == null ? 0 : city.hashCode();
        this.px = px;
        this.wind = wind;
        this.code = code;
        double[] sun = Double.isNaN(lat) || Double.isNaN(lon) ? null : SunCalc.sun(lat, lon, now());
        sky = Sky.layers(w, h, px, el, cloud, code, seed, sunLeft, moon, sun);
        light = CityScene.light(sky, sunLeft);
        // the city as it really is, from open map data (2.31; its streets, landmarks, water and hills), or else the drawn
        // scene (its streets as its continent's, 2.27)
        CityScene.Built real = WorldScene.build(city, cc, w, h, px, light, seed);
        built = real != null ? real : CityScene.build(city, lat, zone, w, h, px, light, seed);
        // the final grade (2.34, after the user's Lightroom edit): richer colour everywhere, the day's shadows lifted
        float lift = Grade.day(el);
        sky.back = Grade.apply(sky.back, lift);
        sky.clouds = Grade.apply(sky.clouds, lift);
        sky.over = Grade.apply(sky.over, lift);
        built.still = Grade.apply(built.still, lift);
        // the clouds cross their strip (twice the cell) in about eight minutes in a breeze, faster in a wind
        drift = 2f * w / 480f * (0.6f + Math.min(40, wind < 0 ? 8 : wind) / 25f);
        out = Bitmap.createBitmap(Math.max(2, w), Math.max(2, h), Bitmap.Config.ARGB_8888);
        canvas = new Canvas(out);
        clear.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
        if (corners != 0) {
            float tl = (corners & TL) != 0 ? r : 0, tr = (corners & TR) != 0 ? r : 0;
            float br = (corners & BR) != 0 ? r : 0, bl = (corners & BL) != 0 ? r : 0;
            outside = new Path();
            outside.addRoundRect(new RectF(0, 0, w, h), new float[]{tl, tl, tr, tr, br, br, bl, bl}, Path.Direction.CW);
            outside.setFillType(Path.FillType.INVERSE_WINDING);
        } else {
            outside = null;
        }
    }

    /**
     * The picture at {@code ms} (epoch milliseconds). The same bitmap is drawn over each time: send it (or copy it)
     * before asking for the next frame.
     */
    synchronized Bitmap frame(long ms) {
        float t = (ms % 86_400_000L) / 1000f;
        Canvas c = canvas;
        c.drawBitmap(sky.back, 0, 0, paint);
        Sky.stars(c, sky, t, dot);
        CityScene.plane(c, sky, light, t, seed, px);
        Sky.clouds(c, sky, t, drift, paint);
        // behind the city (2.27): fireworks after dark on its days, lightning in a thunderstorm
        java.time.LocalDate day = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate();
        float dark = Math.max(0f, Math.min(1f, (-2f - sky.e) / 6f));
        if (dark > 0f && Festive.fireworks(day, cc)) Festive.fireworks(c, out.getWidth(), out.getHeight(), px, t, seed, dark, dot);
        boolean storm = Festive.storm(code);
        if (storm) Festive.lightning(c, out.getWidth(), out.getHeight(), px, t, seed, dot);
        c.drawBitmap(built.still, 0, 0, paint);
        WorldScene.traffic(c, built.traffic, t, dot, px); // the cars' lights on the roads at night (2.32)
        int minute = (int) Math.floorMod(Math.floorDiv(ms / 1000L + zone.getRules().getOffset(Instant.ofEpochMilli(ms))
                .getTotalSeconds(), 60L), 60L);
        CityScene.sparkle(c, built, light, t, minute, px);
        CityScene.beacons(c, built, light, t, px);
        CityScene.trees(c, built, light, t, wind, px);
        // a flock now and then, a helicopter more rarely (2.27), since 2.36 in front of the city, flying over it
        CityScene.birds(c, sky, light, t, seed, px);
        CityScene.helicopter(c, sky, light, t, seed, px);
        Sky.weather(c, sky, t, dot);
        if (sky.flare != null) c.drawBitmap(sky.flare, 0, 0, paint); // the lens's flare, faint (2.31)
        c.drawBitmap(sky.front, 0, 0, paint);
        if (storm) { // the flash lights the city too
            float f = Festive.flash(t, seed);
            if (f > 0f) {
                dot.setColor(Scene.alpha(0xFFDCE6FF, 0.12f * f));
                c.drawRect(0, 0, out.getWidth(), out.getHeight(), dot);
            }
        }
        if (outside != null) c.drawPath(outside, clear);
        return out;
    }
}
