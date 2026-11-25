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
 * rain falls. The outer clocks have the card's round corners cut out of them.
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
    private final Bitmap out;
    private final Canvas canvas;
    private final Path outside;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint clear = new Paint(Paint.ANTI_ALIAS_FLAG);

    private static final LruCache<String, SkyCell> CELLS = new LruCache<>(12);

    /**
     * The cell for {@code city} (latitude {@code lat}, in {@code zone}), the sun {@code elevation} degrees up, in the
     * morning ({@code sunLeft}) or afternoon, with {@code cloud} % cover, WMO weather {@code code} and {@code wind}
     * km/h (-1 each when unknown); {@code w} × {@code h} pixels at {@code px} per dp; {@code side} 0 for a middle cell,
     * 1 for the left one (the card's round corners on its left, radius {@code r} pixels), 2 for the right one.
     */
    static synchronized SkyCell get(String city, double lat, ZoneId zone, double elevation, boolean sunLeft, int cloud,
                                    int code, int wind, int w, int h, float px, int side, float r) {
        float el = Math.round(elevation * 2) / 2f;
        String spec = city + "|" + el + "|" + sunLeft + "|" + (cloud < 0 ? -1 : cloud / 5) + "|" + code + "|"
                + (wind < 0 ? -1 : wind / 5) + "|" + w + "x" + h + "|" + px + "|" + side;
        SkyCell hit = CELLS.get(spec);
        if (hit != null) return hit;
        SkyCell made = new SkyCell(spec, city, lat, zone, el, sunLeft, cloud, code, wind, w, h, px, side, r);
        CELLS.put(spec, made);
        return made;
    }

    private SkyCell(String spec, String city, double lat, ZoneId zone, float el, boolean sunLeft, int cloud, int code,
                    int wind, int w, int h, float px, int side, float r) {
        this.spec = spec;
        this.zone = zone;
        this.seed = city == null ? 0 : city.hashCode();
        this.px = px;
        this.wind = wind;
        long now = System.currentTimeMillis();
        java.time.ZonedDateTime local = Instant.ofEpochMilli(now).atZone(zone);
        sky = Sky.layers(w, h, px, el, cloud, code, seed, sunLeft, now, local.getHour() + local.getMinute() / 60f);
        light = CityScene.light(sky, sunLeft);
        built = CityScene.build(city, lat, w, h, px, light, seed);
        // the clouds cross their strip (twice the cell) in about eight minutes in a breeze, faster in a wind
        drift = 2f * w / 480f * (0.6f + Math.min(40, wind < 0 ? 8 : wind) / 25f);
        out = Bitmap.createBitmap(Math.max(2, w), Math.max(2, h), Bitmap.Config.ARGB_8888);
        canvas = new Canvas(out);
        clear.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
        if (side != 0) {
            boolean left = side == 1;
            outside = new Path();
            outside.addRoundRect(new RectF(left ? 0 : -r, 0, left ? w + r : w, h),
                    left ? new float[]{r, r, 0, 0, 0, 0, r, r} : new float[]{0, 0, r, r, r, r, 0, 0}, Path.Direction.CW);
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
        c.drawBitmap(built.still, 0, 0, paint);
        int minute = (int) Math.floorMod(Math.floorDiv(ms / 1000L + zone.getRules().getOffset(Instant.ofEpochMilli(ms))
                .getTotalSeconds(), 60L), 60L);
        CityScene.sparkle(c, built, light, t, minute, px);
        CityScene.beacons(c, built, light, t, px);
        CityScene.trees(c, built, light, t, wind, px);
        Sky.weather(c, sky, t, dot);
        c.drawBitmap(sky.front, 0, 0, paint);
        if (outside != null) c.drawPath(outside, clear);
        return out;
    }
}
