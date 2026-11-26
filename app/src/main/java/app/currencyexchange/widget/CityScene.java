package app.currencyexchange.widget;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * What stands in a world clock's lower right corner, under its city's sky (2.05): the city's landmark, drawn in code as
 * a silhouette so the live sky lights it (shaded towards the sun by day, dark against the dusk, warm windows and
 * floodlit monuments at night). Some thirty cities have their own (the Ángel in Mexico City, the Athenaeum in
 * Bucharest, the Atomium in Brussels, the Eiffel Tower, Big Ben, ...); any other city gets a skyline of its own,
 * made from its name, with the trees of its latitude (palms near the equator, round trees in between, firs up north).
 * <p>
 * The buildings are drawn once per light ({@link #build}); the trees ({@link #trees}), the beacons and a far plane
 * with its contrail ({@link #plane}) move with every frame ({@link SkyCell}).
 */
final class CityScene {
    private CityScene() {}

    /** The colours the scene takes from the hour's sky. */
    static final class Light {
        /** Far away (hazy), the landmark, the near streets' buildings, and the darkest in front (trees). */
        int far, near, street, front;
        /** The landmark's side facing the sun. */
        int lit;
        /** 0 by day, 1 at night; how far the windows have lit up (from late afternoon). */
        float night, lights;
        boolean sunLeft, snow;
        float e;
        int cloudTop, cloudUnder, horizon;
    }

    static final int WINDOW = 0xFFFFD68A, FLOOD = 0xFFFFC46B, BEACON = 0xFFFF3B30;

    /** The scene's light from the sky's layers; the sun on the left (morning) or the right (afternoon). */
    static Light light(Sky.Layers L, boolean sunLeft) {
        Light l = new Light();
        float e = L.e;
        l.e = e;
        l.night = Sky.clamp((2f - e) / 8f);
        l.lights = Sky.clamp((7f - e) / 10f);
        // the references' city (2.15): tinted by the sky, not black: blue by day, purple at dusk, navy at night; the
        // landmark a middle tone, the far city hazy and pale, the near streets darkest
        int base = 0xFF141C3A;
        l.near = Scene.mix(Scene.mix(L.sky[1], L.sky[0], 0.5f), base, 0.38f + 0.3f * l.night);
        l.street = Scene.mix(L.sky[0], base, 0.5f + 0.25f * l.night);
        l.far = Scene.mix(L.sky[2], l.near, 0.42f + 0.25f * l.night);
        l.front = Scene.mix(l.street, 0xFF05070F, 0.35f);
        float day = 1f - l.night;
        boolean low = e < 8f;
        l.lit = Scene.mix(l.near, low ? 0xFFFFB27A : 0xFFFFF1DE, (low ? 0.3f : 0.2f) * day);
        l.sunLeft = sunLeft;
        l.snow = L.snow;
        l.cloudTop = L.cloud[0];
        l.cloudUnder = L.cloud[1];
        l.horizon = L.sky[2];
        return l;
    }

    /** A tree in front, in pixels: its kind, where it stands, how tall, how it sways. */
    static final class Tree {
        static final int PALM = 0, ROUND = 1, FIR = 2;
        int kind;
        float x, ground, height, lean, phase;
        /** A round tree's crown: the points of its ragged outline (x, y, as shares of its height) round its middle. */
        float[] puffs;
    }

    /** A city's corner, built for one light: the still picture, and what moves in front of it. */
    static final class Built {
        Bitmap still;
        /** Red beacons on the tallest tops, x and y in pixels, blinking at night. */
        float[] beacons = new float[0];
        List<Tree> trees = new ArrayList<>();
        /** Where the Eiffel Tower sparkles for five minutes every hour after dark (null elsewhere). */
        RectF sparkle;
    }

    /** The landmark (or skyline) and trees of {@code city} (at latitude {@code lat}), {@code w} × {@code h} pixels. */
    static Built build(String city, double lat, int w, int h, float px, Light l, long seed) {
        return build(city, lat, null, w, h, px, l, seed);
    }

    /** As above, for a city in {@code zone} (its streets built as its continent's, {@link #style}). */
    static Built build(String city, double lat, java.time.ZoneId zone, int w, int h, float px, Light l, long seed) {
        Built b = new Built();
        b.still = Bitmap.createBitmap(Math.max(2, w), Math.max(2, h), Bitmap.Config.ARGB_8888);
        float u = h / 100f, wide = 100f * w / h;
        String key = key(city);
        Float centre = CENTRE.get(key);
        Pen pen = new Pen(new Canvas(b.still), u, wide, centre == null ? 73f : centre, l, seed);
        List<float[]> beacons = new ArrayList<>();
        // the references' layout (2.15): the far city across the whole width, the landmark in the middle, the near
        // streets in front of its foot
        int style = style(city, zone);
        if (!MOUNTAINS.contains(key)) {
            hills(pen.c, u, wide, l, seed); // the far hills and their lights (2.27)
            skyline(pen.c, u, wide, l, seed, false, style);
        }
        if (!landmark(key, pen, b, beacons, l, wide)) generic(pen, b, beacons, l, lat, wide, key.hashCode(), style);
        if (!"cairo".equals(key) && !"giza".equals(key)) skyline(pen.c, u, wide, l, seed, true, style);
        finish(pen.c, b.still.getWidth(), b.still.getHeight(), l);
        b.beacons = new float[beacons.size() * 2];
        for (int i = 0; i < beacons.size(); i++) {
            b.beacons[2 * i] = pen.X(beacons.get(i)[0]);
            b.beacons[2 * i + 1] = pen.Y(beacons.get(i)[1]);
        }
        for (Tree t : b.trees) { // trees were placed in units from the left edge: into pixels
            t.x *= u;
            t.ground *= u;
            t.height *= u * 0.85f; // small, in front of the streets (2.15)
        }
        return b;
    }

    /** Where each landmark's middle is in its design units: it's drawn at the middle of the clock (2.15). */
    private static final java.util.Map<String, Float> CENTRE = new java.util.HashMap<>();
    static {
        Object[][] c = {{"mexico city", 83f}, {"ciudad de mexico", 83f}, {"cdmx", 83f}, {"mexico df", 83f}, {"mexico", 83f},
                {"bucharest", 73.5f}, {"bucuresti", 73.5f}, {"brussels", 76f}, {"bruxelles", 76f}, {"brussel", 76f},
                {"charlotte", 76f}, {"bogota", 74f}, {"bogota dc", 74f}, {"santa fe de bogota", 74f}, {"paris", 80f},
                {"london", 78f}, {"new york", 80f}, {"new york city", 80f}, {"nyc", 80f}, {"manhattan", 80f},
                {"brooklyn", 80f}, {"tokyo", 80f}, {"sydney", 73f}, {"dubai", 82f}, {"berlin", 80f}, {"toronto", 80f},
                {"seattle", 80f}, {"rio de janeiro", 80f}, {"rio", 80f}, {"cairo", 70f}, {"giza", 70f},
                {"san francisco", 80f}, {"rome", 74f}, {"roma", 74f}, {"barcelona", 76f}, {"chicago", 79f},
                {"los angeles", 80f}, {"la", 80f}, {"hollywood", 80f}, {"miami", 72f}, {"miami beach", 72f},
                {"buenos aires", 80f}, {"washington", 72f}, {"washington dc", 72f}, {"singapore", 76f},
                {"kuala lumpur", 78f}, {"istanbul", 74f}, {"amsterdam", 72f}, {"moscow", 76f}, {"moskva", 76f}};
        for (Object[] e : c) CENTRE.put((String) e[0], (Float) e[1]);
    }

    /** How a city's own streets are built (2.27): towers, European houses, or low houses. */
    static final int TOWERS = 0, EURO = 1, HOUSES = 2;

    /** Cities outside Europe with a skyline of towers; any other city outside Europe has houses. */
    private static final java.util.Set<String> MAJOR = new java.util.HashSet<>(java.util.Arrays.asList(
            "new york", "new york city", "nyc", "manhattan", "brooklyn", "chicago", "los angeles", "la", "hollywood",
            "miami", "miami beach", "houston", "dallas", "atlanta", "charlotte", "seattle", "san francisco", "boston",
            "philadelphia", "washington", "washington dc", "las vegas", "denver", "phoenix", "san diego", "austin",
            "minneapolis", "detroit", "toronto", "vancouver", "montreal", "calgary", "mexico city", "ciudad de mexico",
            "cdmx", "mexico df", "mexico", "monterrey", "guadalajara", "panama city", "panama", "bogota", "bogota dc",
            "santa fe de bogota", "medellin", "cali", "lima", "santiago", "buenos aires", "sao paulo", "rio de janeiro",
            "rio", "caracas", "quito", "guatemala city", "san juan", "santo domingo", "sydney", "melbourne", "brisbane",
            "perth", "auckland", "gold coast", "tokyo", "osaka", "yokohama", "nagoya", "seoul", "busan", "beijing",
            "shanghai", "shenzhen", "guangzhou", "chongqing", "chengdu", "wuhan", "tianjin", "hong kong", "macau",
            "taipei", "singapore", "kuala lumpur", "bangkok", "jakarta", "manila", "makati", "ho chi minh city",
            "hanoi", "mumbai", "delhi", "new delhi", "bangalore", "bengaluru", "kolkata", "chennai", "hyderabad",
            "karachi", "lahore", "dhaka", "dubai", "abu dhabi", "doha", "riyadh", "jeddah", "kuwait city", "manama",
            "tel aviv", "tehran", "johannesburg", "cape town", "nairobi", "lagos", "cairo", "casablanca", "almaty"));

    /**
     * How {@code city}'s streets are built (2.27): in Europe (its time zone says) as European houses, never towers;
     * elsewhere towers in the big cities and low houses in the rest.
     */
    static int style(String city, java.time.ZoneId zone) {
        String z = zone == null ? "" : zone.getId();
        if (z.startsWith("Europe/") || z.equals("Atlantic/Reykjavik") || z.equals("Atlantic/Canary")
                || z.equals("Atlantic/Madeira") || z.equals("Atlantic/Azores") || z.equals("Atlantic/Faroe")) return EURO;
        return MAJOR.contains(key(city)) ? TOWERS : HOUSES;
    }

    private static final int GLOW = 0xFFFFE2B0;

    /**
     * Far hills along the horizon, hazy (2.27, after the video): a gentle line of hills across the cell, and after dark
     * thousands of tiny lights scattered over them, as a city's lights on the hills across a bay.
     */
    private static void hills(Canvas c, float u, float wide, Light l, long seed) {
        Random r = new Random(seed ^ 0x411L);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        Path hill = new Path();
        float a = r.nextFloat() * 6f, b = r.nextFloat() * 6f;
        hill.moveTo(0, 100 * u);
        int steps = 24;
        float[] ys = new float[steps + 1];
        for (int i = 0; i <= steps; i++) {
            float x = wide * i / steps;
            ys[i] = 83f - 3.2f * (float) Math.sin(x / 9f + a) - 2.2f * (float) Math.sin(x / 4.3f + b);
            hill.lineTo(x * u, ys[i] * u);
        }
        hill.lineTo(wide * u, 100 * u);
        hill.close();
        p.setColor(Scene.mix(l.far, l.horizon, 0.38f));
        c.drawPath(hill, p);
        if (l.lights < 0.05f) return;
        int n = Math.round(wide * 5.5f);
        for (int i = 0; i < n; i++) { // its lights: tiny, cream and warm, thicker towards the shore
            float x = r.nextFloat() * wide, f = x / wide * steps;
            int k = Math.min(steps - 1, (int) f);
            float top = ys[k] + (ys[k + 1] - ys[k]) * (f - k);
            float y = top + 0.6f + (float) Math.pow(r.nextFloat(), 0.7f) * (99.5f - top);
            float size = 0.14f + r.nextFloat() * 0.2f, bright = 0.35f + 0.65f * r.nextFloat();
            p.setColor(Scene.alpha(r.nextFloat() < 0.75f ? GLOW : 0xFFFFC27A, l.lights * 0.75f * bright));
            c.drawCircle(x * u, y * u, size * u, p);
        }
    }

    /**
     * A tower's face in the video's manner (2.27): a thin pale line along every floor (its balconies), a darker line down
     * its middle, and here and there a lit window, warm, of a size of its own, rather than a grid; by day a few panes
     * catch the sky. In pixels: from ({@code x0}, {@code y0}) to ({@code x1}, {@code y1}), {@code unit} pixels a unit.
     */
    static void facade(Canvas c, float x0, float y0, float x1, float y1, float unit, Random r, Light l, float strength) {
        Paint wp = new Paint(Paint.ANTI_ALIAS_FLAG);
        float floor = 1.7f * unit, w = x1 - x0;
        if (w < 1.5f * unit || y1 - y0 < 2 * unit) return;
        wp.setColor(Scene.alpha(Scene.mix(l.lit, 0xFFFFFFFF, 0.3f), (0.12f + 0.12f * (1 - l.night)) * strength));
        for (float y = y0 + 1.3f * unit; y < y1 - 0.5f * unit; y += floor) {
            c.drawRect(x0 + 0.25f * unit, y, x1 - 0.25f * unit, y + Math.max(0.6f, 0.2f * unit), wp);
        }
        wp.setColor(Scene.alpha(0xFF000000, 0.13f * strength)); // the darker line down its middle
        float mid = (x0 + x1) / 2f;
        c.drawRect(mid - 0.14f * unit, y0 + 0.8f * unit, mid + 0.14f * unit, y1, wp);
        int rows = Math.max(1, (int) ((y1 - y0 - 1.6f * unit) / floor));
        int n = Math.max(1, Math.round((w / unit) * rows / 4.5f));
        for (int i = 0; i < n; i++) {
            int row = r.nextInt(rows);
            float ww = (0.6f + r.nextFloat() * 1.0f) * unit, wh = floor * (0.5f + 0.25f * r.nextFloat());
            float x = x0 + 0.5f * unit + r.nextFloat() * Math.max(0.1f, w - ww - 1.0f * unit);
            float y = y0 + 1.3f * unit + row * floor + 0.3f * unit;
            float on = r.nextFloat(), bright = r.nextFloat(), kind = r.nextFloat();
            if (l.lights > 0.03f && on < 0.7f) {
                int col = kind < 0.12f ? 0xFFDCE8FF : kind < 0.3f ? 0xFFFFB866 : WINDOW;
                wp.setColor(Scene.alpha(col, l.lights * (0.5f + 0.5f * bright) * strength));
                c.drawRect(x, y, x + ww, y + wh, wp);
            } else if (l.lights < 0.9f && kind < 0.5f) {
                wp.setColor(Scene.alpha(Scene.mix(l.horizon, 0xFFFFFFFF, 0.3f), 0.1f * (1 - l.lights) * strength));
                c.drawRect(x, y, x + ww, y + wh, wp);
            }
        }
    }

    /**
     * A layer of the city across the whole cell (2.15; built for its {@code style} since 2.27): far ({@code near}
     * false) a hazy crowd behind the landmark, near the darkest streets in front of its foot, low in the middle so the
     * landmark stands clear and taller to either side. Towers have the video's faces ({@link #facade}); European
     * streets are rows of town houses with gables, mansards and stepped gables and a church spire; elsewhere, low houses
     * with pitched roofs. Near, street lamps glow along the pavement at night.
     */
    private static void skyline(Canvas c, float u, float wide, Light l, long seed, boolean near, int style) {
        Random r = new Random(seed ^ (near ? 0x5EA1L : 0xFA4L));
        if (style == EURO) euro(c, u, wide, l, r, near);
        else if (style == HOUSES) houses(c, u, wide, l, r, near);
        else towers(c, u, wide, l, r, near);
        if (near) lamps(c, u, wide, l, r);
    }

    /** The colour of one building in a layer, a little different from its neighbours. */
    private static int tone(Light l, boolean near, Random r) {
        return Scene.mix(near ? l.street : l.far, near ? 0xFF000000 : l.horizon, r.nextFloat() * 0.12f);
    }

    /** Snow lying on a roof's top edge from (x0, y) to (x1, y), in units. */
    private static void snowOn(Canvas c, float u, Light l, boolean near, float x0, float y, float x1, Paint p) {
        if (!l.snow) return;
        p.setColor(Scene.mix(0xFFF4F7FC, l.horizon, near ? 0.12f + 0.4f * l.night : 0.35f + 0.3f * l.night));
        c.drawRect(x0 * u, y * u, x1 * u, (y + (near ? 1.2f : 0.8f)) * u, p);
    }

    /** The side facing the sun catching the light, as a thin strip (near buildings only). */
    private static void litEdge(Canvas c, float u, Light l, float x0, float top, float x1, float bottom, Paint p) {
        p.setColor(Scene.alpha(Scene.mix(l.lit, 0xFFFFFFFF, 0.15f), 0.3f * (1 - l.night) + 0.08f));
        float sw = 0.55f;
        if (l.sunLeft) c.drawRect(x0 * u, top * u, (x0 + sw) * u, bottom * u, p);
        else c.drawRect((x1 - sw) * u, top * u, x1 * u, bottom * u, p);
    }

    private static void towers(Canvas c, float u, float wide, Light l, Random r, boolean near) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        float x = -1;
        float towerL = wide * (0.12f + r.nextFloat() * 0.08f), towerR = wide * (0.74f + r.nextFloat() * 0.1f);
        boolean doneL = false, doneR = false;
        while (x < wide + 1) {
            float w = near ? 4 + r.nextFloat() * 6 : 3 + r.nextFloat() * 5, hgt;
            float mid = Math.abs(x + w / 2 - wide / 2) / (wide / 2);
            if (near) {
                if (!doneL && x + w > towerL) { w = 7 + r.nextFloat() * 3; hgt = 34 + r.nextFloat() * 8; doneL = true; }
                else if (!doneR && x + w > towerR) { w = 7 + r.nextFloat() * 3; hgt = 36 + r.nextFloat() * 10; doneR = true; }
                else hgt = (mid < 0.3f ? 5 + r.nextFloat() * 6 : 9 + r.nextFloat() * 15);
            } else {
                hgt = 14 + r.nextFloat() * 20;
            }
            float l0 = x, r0 = x + w, top = 100 - hgt;
            int col = tone(l, near, r);
            p.setColor(col);
            c.drawRect(l0 * u, top * u, r0 * u, 100 * u, p);
            // its other face, away from the sun, a shade darker (the video's towers show two)
            p.setColor(Scene.alpha(0xFF000000, near ? 0.16f : 0.08f));
            float side = w * 0.3f;
            if (l.sunLeft) c.drawRect((r0 - side) * u, top * u, r0 * u, 100 * u, p);
            else c.drawRect(l0 * u, top * u, (l0 + side) * u, 100 * u, p);
            if (hgt > 16 && r.nextFloat() < 0.55f) { // a smaller block on its roof
                float inset = w * (0.18f + 0.12f * r.nextFloat()), rh = 1.4f + r.nextFloat() * 2f;
                p.setColor(col);
                c.drawRect((l0 + inset) * u, (top - rh) * u, (r0 - inset) * u, top * u, p);
                snowOn(c, u, l, near, l0 + inset, top - rh, r0 - inset, p);
            }
            snowOn(c, u, l, near, l0, top, r0, p);
            if (near) {
                litEdge(c, u, l, l0, top, r0, 100, p);
                if (hgt > 30 && l.night > 0.3f) {
                    p.setColor(Scene.alpha(BEACON, 0.7f * l.night));
                    c.drawCircle((l0 + w / 2) * u, (top - 0.8f) * u, 0.55f * u, p);
                }
            }
            facade(c, l0 * u, top * u, r0 * u, 100 * u, u, r, l, near ? 1f : 0.6f);
            x = r0 + (near ? r.nextFloat() * 0.4f : r.nextFloat() * 0.8f);
        }
    }

    /** European town houses: three to five storeys of tall windows under gables, mansards or stepped gables; a spire. */
    private static void euro(Canvas c, float u, float wide, Light l, Random r, boolean near) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG), wp = new Paint(Paint.ANTI_ALIAS_FLAG);
        float x = -1, spireAt = wide * (near ? 0.1f + r.nextFloat() * 0.12f : 0.3f + r.nextFloat() * 0.4f);
        boolean spire = false;
        float storey = near ? 3.1f : 2.2f;
        while (x < wide + 1) {
            float w = near ? 4.5f + r.nextFloat() * 3f : 3f + r.nextFloat() * 3f;
            float mid = Math.abs(x + w / 2 - wide / 2) / (wide / 2);
            int floors = near ? (mid < 0.3f ? 2 + r.nextInt(2) : 3 + r.nextInt(3)) : 2 + r.nextInt(3);
            float l0 = x, r0 = x + w, top = 100 - floors * storey - 0.8f;
            int col = tone(l, near, r), roof = Scene.mix(col, 0xFF000000, near ? 0.28f : 0.14f);
            if (!spire && x + w > spireAt) { // a church: a tall tower and its spire
                spire = true;
                float tw = near ? 4f : 2.6f, th = near ? 26f : 16f, sp = near ? 11f : 7f;
                float t0 = 100 - th;
                p.setColor(col);
                c.drawRect(l0 * u, t0 * u, (l0 + tw) * u, 100 * u, p);
                p.setColor(roof);
                Path s = new Path();
                s.moveTo((l0 - 0.3f) * u, t0 * u);
                s.lineTo((l0 + tw / 2) * u, (t0 - sp) * u);
                s.lineTo((l0 + tw + 0.3f) * u, t0 * u);
                s.close();
                c.drawPath(s, p);
                if (near) {
                    litEdge(c, u, l, l0, t0, l0 + tw, 100, p);
                    wp.setColor(Scene.alpha(l.lights > 0.1f ? WINDOW : Scene.mix(l.horizon, 0xFFFFFFFF, 0.3f),
                            l.lights > 0.1f ? 0.7f * l.lights : 0.15f));
                    c.drawCircle((l0 + tw / 2) * u, (t0 + 3f) * u, 0.9f * u, wp); // its clock
                }
                x = l0 + tw + 0.2f;
                continue;
            }
            p.setColor(col);
            c.drawRect(l0 * u, top * u, r0 * u, 100 * u, p);
            // its roof
            p.setColor(roof);
            int kind = r.nextInt(4);
            Path rf = new Path();
            if (kind == 0) { // a gable
                rf.moveTo((l0 - 0.2f) * u, top * u);
                rf.lineTo((l0 + w / 2) * u, (top - w * 0.42f) * u);
                rf.lineTo((r0 + 0.2f) * u, top * u);
                rf.close();
            } else if (kind == 1) { // a mansard, its dormers lit at night
                float rh = near ? 2.8f : 1.8f;
                rf.moveTo((l0 - 0.2f) * u, top * u);
                rf.lineTo((l0 + 0.7f) * u, (top - rh) * u);
                rf.lineTo((r0 - 0.7f) * u, (top - rh) * u);
                rf.lineTo((r0 + 0.2f) * u, top * u);
                rf.close();
            } else if (kind == 2) { // a stepped gable
                int steps = 3;
                float sh = w * 0.14f, sw = w / (2 * steps + 1);
                rf.moveTo(l0 * u, top * u);
                float yy = top;
                for (int s = 0; s < steps; s++) {
                    rf.lineTo((l0 + s * sw) * u, (yy - sh) * u);
                    rf.lineTo((l0 + (s + 1) * sw) * u, (yy - sh) * u);
                    yy -= sh;
                }
                rf.lineTo((r0 - steps * sw) * u, (yy - sh) * u);
                for (int s = steps; s > 0; s--) {
                    rf.lineTo((r0 - (s - 1) * sw) * u, (yy) * u);
                    yy += sh;
                    rf.lineTo((r0 - (s - 1) * sw) * u, yy * u);
                }
                rf.lineTo(r0 * u, top * u);
                rf.close();
                p.setColor(col); // stepped gables are the wall carried up
            } else { // flat, with a cornice
                rf.addRect((l0 - 0.3f) * u, (top - 0.7f) * u, (r0 + 0.3f) * u, top * u, Path.Direction.CW);
            }
            c.drawPath(rf, p);
            if (near && r.nextFloat() < 0.5f) { // a chimney
                float cx = l0 + w * (0.2f + 0.6f * r.nextFloat());
                p.setColor(roof);
                c.drawRect(cx * u, (top - w * 0.3f - 1.2f) * u, (cx + 0.7f) * u, (top - 0.4f) * u, p);
            }
            snowOn(c, u, l, near, l0, top - 0.3f, r0, p);
            if (near) litEdge(c, u, l, l0, top, r0, 100, p);
            // its windows: a row of tall ones on every floor, the door below
            float ww = near ? 0.85f : 0.55f, wh = near ? 1.55f : 1.0f;
            int per = Math.max(1, (int) ((w - 1.2f) / (near ? 1.55f : 1.1f)));
            float gap = (w - per * ww) / (per + 1);
            for (int f = 0; f < floors; f++) {
                float y = top + 0.9f + f * storey;
                if (f == floors - 1 && near) { // the door
                    wp.setColor(Scene.alpha(0xFF000000, 0.25f));
                    float dx = l0 + w / 2 - 0.55f;
                    c.drawRect(dx * u, (100 - 2.3f) * u, (dx + 1.1f) * u, 100 * u, wp);
                    continue;
                }
                for (int i = 0; i < per; i++) {
                    float wx = l0 + gap + i * (ww + gap);
                    float on = r.nextFloat(), bright = r.nextFloat();
                    if (l.lights > 0.04f && on < 0.5f) {
                        wp.setColor(Scene.alpha(bright < 0.2f ? 0xFFFFB866 : WINDOW, l.lights * (near ? 0.85f : 0.5f)
                                * (0.55f + 0.45f * bright)));
                    } else {
                        wp.setColor(Scene.alpha(Scene.mix(l.horizon, 0xFFFFFFFF, 0.25f), (near ? 0.14f : 0.08f) * (1 - l.lights * 0.7f)));
                    }
                    c.drawRect(wx * u, y * u, (wx + ww) * u, (y + wh) * u, wp);
                }
            }
            x = r0 + (near ? r.nextFloat() * 0.25f : r.nextFloat() * 0.5f);
        }
    }

    /** Low houses (a smaller city outside Europe): one or two storeys under pitched roofs, gardens between them. */
    private static void houses(Canvas c, float u, float wide, Light l, Random r, boolean near) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG), wp = new Paint(Paint.ANTI_ALIAS_FLAG);
        float x = -2;
        while (x < wide + 1) {
            float w = near ? 6 + r.nextFloat() * 4 : 3.5f + r.nextFloat() * 3;
            float mid = Math.abs(x + w / 2 - wide / 2) / (wide / 2);
            boolean two = near ? (mid > 0.35f && r.nextFloat() < 0.55f) : r.nextFloat() < 0.4f;
            float hgt = near ? (two ? 8.5f : 5.2f) + r.nextFloat() * 1.5f : (two ? 5f : 3.2f) + r.nextFloat();
            float l0 = x, r0 = x + w, top = 100 - hgt;
            int col = tone(l, near, r), roof = Scene.mix(col, 0xFF000000, near ? 0.3f : 0.15f);
            p.setColor(col);
            c.drawRect(l0 * u, top * u, r0 * u, 100 * u, p);
            p.setColor(roof);
            Path rf = new Path();
            float eave = near ? 0.6f : 0.35f, rise = w * (0.22f + 0.1f * r.nextFloat());
            rf.moveTo((l0 - eave) * u, (top + 0.2f) * u);
            rf.lineTo((l0 + w * 0.5f) * u, (top - rise) * u);
            rf.lineTo((r0 + eave) * u, (top + 0.2f) * u);
            rf.close();
            c.drawPath(rf, p);
            snowOn(c, u, l, near, l0 + w * 0.2f, top - rise * 0.6f, r0 - w * 0.2f, p);
            if (near) litEdge(c, u, l, l0, top, r0, 100, p);
            int floors = two ? 2 : 1;
            float fh = hgt / floors, ww = near ? 1.2f : 0.7f, wh = near ? 1.2f : 0.7f;
            for (int f = 0; f < floors; f++) {
                float y = top + fh * f + fh * 0.32f;
                for (int i = 0; i < 2; i++) {
                    float wx = l0 + w * (i == 0 ? 0.22f : 0.62f);
                    if (f == floors - 1 && i == 1 && near) { // the door, and its porch light at night
                        wp.setColor(Scene.alpha(0xFF000000, 0.25f));
                        c.drawRect(wx * u, (100 - 2.4f) * u, (wx + 1.1f) * u, 100 * u, wp);
                        if (l.lights > 0.1f) {
                            wp.setShader(new RadialGradient((wx + 0.55f) * u, (100 - 2.9f) * u, 1.6f * u,
                                    Scene.alpha(GLOW, 0.5f * l.lights), GLOW & 0x00FFFFFF, Shader.TileMode.CLAMP));
                            c.drawCircle((wx + 0.55f) * u, (100 - 2.9f) * u, 1.6f * u, wp);
                            wp.setShader(null);
                        }
                        continue;
                    }
                    float on = r.nextFloat();
                    wp.setColor(l.lights > 0.04f && on < 0.55f ? Scene.alpha(WINDOW, l.lights * (near ? 0.85f : 0.5f))
                            : Scene.alpha(Scene.mix(l.horizon, 0xFFFFFFFF, 0.25f), (near ? 0.14f : 0.08f) * (1 - l.lights * 0.7f)));
                    c.drawRect(wx * u, y * u, (wx + ww) * u, (y + wh) * u, wp);
                }
            }
            x = r0 + (near ? 1.2f + r.nextFloat() * 3f : 0.6f + r.nextFloat() * 1.5f);
        }
    }

    /** Street lamps along the pavement (2.27, after the video): thin posts, and at night a warm glow round each head. */
    private static void lamps(Canvas c, float u, float wide, Light l, Random r) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        float x = 3 + r.nextFloat() * 6;
        while (x < wide) {
            float top = 92.4f;
            p.setColor(Scene.mix(l.street, 0xFF000000, 0.35f));
            c.drawRect((x - 0.13f) * u, top * u, (x + 0.13f) * u, 100 * u, p);
            c.drawRect((x - 0.6f) * u, (top - 0.25f) * u, (x + 0.6f) * u, top * u, p);
            if (l.lights > 0.08f) {
                p.setShader(new RadialGradient(x * u, (top + 0.2f) * u, 2.6f * u, Scene.alpha(GLOW, 0.55f * l.lights),
                        GLOW & 0x00FFFFFF, Shader.TileMode.CLAMP));
                c.drawCircle(x * u, (top + 0.2f) * u, 2.6f * u, p);
                p.setShader(null);
                p.setColor(Scene.alpha(0xFFFFF4DE, 0.95f * l.lights));
                c.drawCircle(x * u, (top + 0.15f) * u, 0.32f * u, p);
            }
            x += 11 + r.nextFloat() * 5;
        }
    }

    /** Cities whose landmark stands before mountains or desert: they draw the far city (or none) themselves. */
    private static final java.util.Set<String> MOUNTAINS = new java.util.HashSet<>(java.util.Arrays.asList(
            "bogota", "bogota dc", "santa fe de bogota", "seattle", "rio de janeiro", "rio", "cairo", "giza",
            "los angeles", "la", "hollywood", "sydney"));

    /**
     * The finishing light over the corner (2.06): the buildings darker towards the ground, a fine grain on them so they
     * aren't flat, the horizon's haze over their feet by day and the streets' warm glow at night.
     */
    private static void finish(Canvas c, int w, int h, Light l) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        // no grain since 2.27: flat and clean, as the video's buildings
        float day = 1 - l.night;
        if (day > 0.02f) {
            p.setShader(new LinearGradient(0, h, 0, h * 0.8f, Scene.alpha(l.horizon, 0.3f * day), l.horizon & 0x00FFFFFF,
                    Shader.TileMode.CLAMP));
            c.drawRect(0, h * 0.8f, w, h, p);
        }
        if (l.night > 0.02f) {
            p.setShader(new LinearGradient(0, h, 0, h * 0.72f, Scene.alpha(0xFFFFA860, 0.22f * l.night), 0x00FFA860,
                    Shader.TileMode.CLAMP));
            c.drawRect(0, h * 0.72f, w, h, p);
        }
        p.setShader(null);
    }

    /** The city's name as a key: lower case, no accents, no country after a comma. */
    static String key(String city) {
        if (city == null) return "";
        String s = city.split(",")[0].trim().toLowerCase(Locale.ROOT);
        s = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return s.replaceAll("[^a-z ]", "").replaceAll("\\s+", " ").trim();
    }

    // ------------------------------------------------------------------ drawing in units

    /**
     * Draws in units of a hundredth of the cell's height, x measured so that 100 is the cell's right edge, everything
     * shrunk by {@link #K} towards the bottom right corner (so the clock's text stays above it).
     */
    private static final class Pen {
        /** How big a landmark is drawn: zoomed in (2.09, eased a little in 2.12), so its details and motion show. */
        static final float K = 1.15f;
        final Canvas c;
        final float u, wide, cx;
        final Light l;
        final Random r;
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);

        /** {@code cx}: the landmark's middle in its design units, drawn at the middle of the cell (2.15). */
        Pen(Canvas c, float u, float wide, float cx, Light l, long seed) {
            this.c = c;
            this.u = u;
            this.wide = wide;
            this.cx = cx;
            this.l = l;
            this.r = new Random(seed);
        }

        float X(float x) { return (wide / 2f + (x - cx) * K) * u; }
        /** The cell's left and right edges, in design units. */
        float left() { return cx - wide / (2f * K); }
        float right() { return cx + wide / (2f * K); }
        float Y(float y) { return (100f - (100f - y) * K) * u; }
        /** A length in units, in pixels. */
        float S(float v) { return v * K * u; }

        /** The landmark's colour, shaded across {@code a}..{@code b}: lighter on the side facing the sun. */
        Pen body(float a, float b) {
            int s = l.lit, d = l.near;
            p.setShader(new LinearGradient(X(a), 0, X(b), 0, l.sunLeft ? s : d, l.sunLeft ? d : s, Shader.TileMode.CLAMP));
            return this;
        }

        /** The landmark's colour, washed towards {@code tint} by {@code k} (a floodlight, a gilded statue). */
        Pen tinted(float a, float b, int tint, float k) {
            int s = Scene.mix(l.lit, tint, k), d = Scene.mix(l.near, tint, k * 0.8f);
            p.setShader(new LinearGradient(X(a), 0, X(b), 0, l.sunLeft ? s : d, l.sunLeft ? d : s, Shader.TileMode.CLAMP));
            return this;
        }

        Pen solid(int colour) {
            p.setShader(null);
            p.setColor(colour);
            return this;
        }

        void rect(float l0, float t, float r0, float b) { c.drawRect(X(l0), Y(t), X(r0), Y(b), p); }

        void oval(float l0, float t, float r0, float b) { c.drawOval(X(l0), Y(t), X(r0), Y(b), p); }

        void circle(float x, float y, float rad) { c.drawCircle(X(x), Y(y), S(rad), p); }

        void poly(float... xy) {
            Path path = new Path();
            path.moveTo(X(xy[0]), Y(xy[1]));
            for (int i = 2; i < xy.length; i += 2) path.lineTo(X(xy[i]), Y(xy[i + 1]));
            path.close();
            c.drawPath(path, p);
        }

        void line(float x0, float y0, float x1, float y1, float width) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(Math.max(0.8f, S(width)));
            p.setStrokeCap(Paint.Cap.ROUND);
            c.drawLine(X(x0), Y(y0), X(x1), Y(y1), p);
            p.setStyle(Paint.Style.FILL);
        }

        /** The top half of an ellipse (a dome) over l..r, from {@code base} up to {@code top}. */
        void dome(float l0, float r0, float base, float top) {
            c.save();
            c.clipRect(X(l0) - 1, Y(top) - 1, X(r0) + 1, Y(base));
            c.drawOval(X(l0), Y(top), X(r0), Y(base + (base - top)), p);
            c.restore();
        }

        /**
         * A block's face: since 2.27 in the video's manner ({@link CityScene#facade}): a pale line along every floor, a
         * darker line down its middle, and here and there a lit window (warm at night, catching the sky by day).
         */
        void windows(float l0, float t, float r0, float b) {
            facade(c, X(l0), Y(t), X(r0), Y(b), S(1), r, l, 1f);
        }

        /** A building: its shaded block, the edge and roof catching the light, its windows. */
        void tower(float l0, float t, float r0, float b) {
            body(l0, r0).rect(l0, t, r0, b);
            edges(l0, t, r0, b);
            windows(l0, t, r0, b);
        }

        /** The side facing the sun and the roof's edge catching the light (a rim of sky light at night). */
        void edges(float l0, float t, float r0, float b) {
            Paint e = new Paint(Paint.ANTI_ALIAS_FLAG);
            int hi = Scene.mix(l.lit, 0xFFFFFFFF, 0.2f * (1 - l.night));
            float a = 0.5f * (1 - l.night) + 0.1f;
            e.setColor(Scene.alpha(hi, a));
            float sw = 0.7f;
            if (l.sunLeft) c.drawRect(X(l0), Y(t), X(l0 + sw), Y(b), e);
            else c.drawRect(X(r0 - sw), Y(t), X(r0), Y(b), e);
            e.setColor(Scene.alpha(hi, a * 0.8f));
            c.drawRect(X(l0), Y(t), X(r0), Y(t + 0.5f), e);
        }


        /** A floodlit glow round a monument at night, centred at (x, y), {@code rad} units wide. */
        void glow(float x, float y, float rad, int colour, float a) {
            if (a <= 0.01f) return;
            Paint gp = new Paint(Paint.ANTI_ALIAS_FLAG);
            gp.setShader(new RadialGradient(X(x), Y(y), S(rad), Scene.alpha(colour, a), colour & 0x00FFFFFF,
                    Shader.TileMode.CLAMP));
            c.drawCircle(X(x), Y(y), S(rad), gp);
        }
    }

    private static Tree tree(int kind, float x, float ground, float height, float lean, Random r) {
        Tree t = new Tree();
        t.kind = kind;
        t.x = x;
        t.ground = ground;
        t.height = height;
        t.lean = lean;
        t.phase = r.nextFloat() * 6.2832f;
        // a crown cut as from paper (2.27, after the video): a ragged polygon, its points in and out round an oval
        int n = 15;
        t.puffs = new float[n * 2];
        for (int i = 0; i < n; i++) {
            double a = (i + r.nextFloat() * 0.5f) / n * Math.PI * 2;
            float d = 0.3f * (i % 2 == 0 ? 0.92f + r.nextFloat() * 0.18f : 0.72f + r.nextFloat() * 0.12f);
            t.puffs[2 * i] = (float) Math.cos(a) * d * 1.1f;
            t.puffs[2 * i + 1] = (float) Math.sin(a) * d * 0.88f;
        }
        return t;
    }

    // ------------------------------------------------------------------ landmarks

    /** Draws {@code key}'s own landmark; false when the city has none (it gets a skyline). */
    private static boolean landmark(String key, Pen p, Built b, List<float[]> beacons, Light l, float wide) {
        float night = l.night;
        Random r = p.r;
        switch (key) {
            case "mexico city": case "ciudad de mexico": case "cdmx": case "mexico df": case "mexico": {
                // Torre Reforma's slanted top and Torre Mayor behind, the Ángel de la Independencia, palms on the left
                p.tower(47, 60, 55, 100);
                p.body(58, 68).poly(58, 100, 58, 51, 63, 46.5f, 68, 49.5f, 68, 100);
                p.windows(58, 52, 68, 100);
                beacons.add(new float[]{63, 46.5f});
                p.body(76, 90).rect(76, 96, 90, 100);
                p.body(78, 88).rect(78, 92, 88, 96);
                p.body(79.5f, 86.5f).rect(79.5f, 86, 86.5f, 92);
                p.body(81.6f, 84.4f).poly(81.4f, 86, 81.9f, 54, 84.1f, 54, 84.6f, 86);
                p.body(80.8f, 85.2f).rect(80.8f, 52, 85.2f, 54.5f);
                p.glow(83, 47, 7, FLOOD, 0.35f * night);
                p.tinted(80, 86, 0xFFE8B84A, 0.45f + 0.45f * night); // the gilded angel
                p.poly(82.4f, 52, 83.6f, 52, 83.3f, 46.2f, 82.7f, 46.2f);
                p.poly(83, 48.4f, 79.6f, 44.2f, 81.6f, 48.8f);
                p.poly(83, 48.4f, 86.4f, 44.6f, 84.4f, 48.9f);
                p.circle(83, 45.4f, 0.75f);
                p.line(83.3f, 47.2f, 84.7f, 43.2f, 0.5f);
                b.trees.add(tree(Tree.PALM, 9, 100, 44, 0.12f, r));
                b.trees.add(tree(Tree.PALM, 19, 100, 35, -0.08f, r));
                return true;
            }
            case "bucharest": case "bucuresti": {
                // the Romanian Athenaeum: its dome on a drum over a columned portico, floodlit gold at night
                float k = 0.78f * night;
                p.tinted(50, 97, FLOOD, k * 0.6f).rect(50, 84, 97, 100);
                p.tinted(62, 85, FLOOD, k * 0.7f).rect(62, 70, 85, 78);
                p.tinted(60.5f, 86.5f, FLOOD, k * 0.75f).dome(60.5f, 86.5f, 71, 51.5f);
                p.solid(Scene.alpha(l.near, 0.55f));
                for (int i = 0; i < 5; i++) {
                    float x = 64 + i * 4.75f;
                    p.line(73.5f, 52, x, 70.6f, 0.35f);
                }
                p.glow(68, 58, 9, Scene.mix(l.lit, 0xFFFFFFFF, 0.4f), 0.3f * (1 - night)); // the sky caught on the dome
                p.tinted(72, 75, FLOOD, k).rect(72, 46.5f, 75, 51.8f);
                p.circle(73.5f, 46.5f, 1.6f);
                p.line(73.5f, 45, 73.5f, 42.2f, 0.45f);
                p.solid(Scene.mix(l.near, 0xFF000000, 0.35f)).rect(56.5f, 86, 90.5f, 97); // the portico's shade
                p.tinted(56, 91, FLOOD, k).poly(55.5f, 84.5f, 73.5f, 77.2f, 91.5f, 84.5f);
                p.rect(56, 84.4f, 91, 86.2f);
                for (int i = 0; i < 6; i++) {
                    float x = 57.2f + i * 6.2f;
                    p.rect(x, 86.2f, x + 2.4f, 97);
                }
                p.rect(54, 97, 93, 100);
                p.glow(73.5f, 66, 22, FLOOD, 0.28f * night);
                b.trees.add(tree(Tree.ROUND, 10, 100, 26, 0, r));
                b.trees.add(tree(Tree.ROUND, 22, 100, 20, 0, r));
                b.trees.add(tree(Tree.ROUND, 44, 100, 17, 0, r));
                return true;
            }
            case "brussels": case "bruxelles": case "brussel": {
                // the Atomium: nine steel spheres, a cube stood on its corner, joined by tubes
                float cx = 76, top = 50, H = 40, dx = 0.408f * H, sr = 4.1f;
                float y1 = top + H / 3f, y2 = top + 2 * H / 3f, yc = top + H / 2f, bot = top + H;
                float[][] s = {{cx, top}, {cx, y1}, {cx - dx, y1}, {cx + dx, y1}, {cx, yc},
                        {cx - dx, y2}, {cx + dx, y2}, {cx, y2}, {cx, bot}};
                int[][] tubes = {{0, 1}, {0, 2}, {0, 3}, {8, 7}, {8, 5}, {8, 6}, {1, 5}, {1, 6}, {2, 5}, {2, 7},
                        {3, 6}, {3, 7}, {4, 0}, {4, 1}, {4, 2}, {4, 3}, {4, 5}, {4, 6}, {4, 7}, {4, 8}};
                p.body(cx - dx - sr, cx + dx + sr);
                p.line(cx, bot + 2, cx - 9, 100, 1.1f);
                p.line(cx, bot + 2, cx + 9, 100, 1.1f);
                p.line(cx, bot + 2, cx, 100, 1.1f);
                for (int[] t : tubes) p.line(s[t[0]][0], s[t[0]][1], s[t[1]][0], s[t[1]][1], 1.5f);
                int steel = Scene.mix(l.lit, 0xFFFFFFFF, 0.35f * (1 - night));
                for (float[] c : s) { // each sphere lit from the sun's side, a bright spot of sky on it
                    float hx = c[0] + (l.sunLeft ? -1.4f : 1.4f), hy = c[1] - 1.4f;
                    p.p.setShader(new RadialGradient(p.X(hx), p.Y(hy), p.S(sr * 1.6f),
                            new int[]{steel, l.near, Scene.mix(l.near, 0xFF000000, 0.3f)}, new float[]{0f, 0.6f, 1f},
                            Shader.TileMode.CLAMP));
                    p.circle(c[0], c[1], sr);
                }
                if (l.lights > 0.05f) { // the lights strung round each sphere at night
                    p.solid(Scene.alpha(0xFFFFF4DA, 0.85f * l.lights));
                    for (float[] c : s) {
                        for (int k = 0; k < 8; k++) {
                            double a = k * Math.PI / 4;
                            p.circle(c[0] + (float) Math.cos(a) * sr * 0.62f, c[1] + (float) Math.sin(a) * sr * 0.62f, 0.32f);
                        }
                    }
                }
                beacons.add(new float[]{cx, top - sr});
                b.trees.add(tree(Tree.ROUND, 9, 100, 24, 0, r));
                b.trees.add(tree(Tree.ROUND, 20, 100, 18, 0, r));
                return true;
            }
            case "charlotte": {
                // Uptown: the Bank of America Corporate Center's crown of rods, Duke Energy Center, the Hearst Tower
                p.tower(47, 79, 54, 100);
                p.body(54, 59.5f).poly(54, 100, 54, 68, 56.5f, 66, 59.5f, 68, 59.5f, 100);
                p.windows(54, 68, 59.5f, 100);
                p.body(60, 68.5f).poly(60, 100, 60, 59.5f, 61.5f, 58, 67, 58, 68.5f, 59.5f, 68.5f, 100);
                p.windows(60, 59, 68.5f, 100);
                p.tower(72, 52, 80.5f, 100);
                p.tinted(72.6f, 79.9f, 0xFFFFE7B8, 0.6f * night).poly(72.6f, 52, 73.6f, 47, 78.9f, 47, 79.9f, 52);
                for (int i = 0; i < 7; i++) {
                    float x = 73.8f + i * 0.82f, t = 43.5f - (3 - Math.abs(i - 3)) * 1.1f;
                    p.line(x, 47, x, t, 0.32f);
                }
                beacons.add(new float[]{76.25f, 40.2f});
                p.tower(83, 61, 90, 100);
                p.body(83, 90).poly(83, 61.2f, 86.5f, 55.5f, 90, 61.2f);
                p.body(91, 98).rect(91, 76, 98.5f, 100);
                p.windows(91, 76, 98.5f, 100);
                for (int i = 0; i < 6; i++) b.trees.add(tree(Tree.ROUND, wide - 56 + i * 10.5f, 100, 11 + r.nextFloat() * 5, 0, r));
                return true;
            }
            case "bogota": case "bogota dc": case "santa fe de bogota": {
                // Monserrate behind, its sanctuary on the top, and the Colpatria and Bacatá towers
                p.solid(l.far).poly(p.left() - 2, 100, p.left() + 10, 90, 40, 78, 56, 62, 66, 53.5f, 72, 56, 86, 64, p.right() + 2, 74, p.right() + 2, 100);
                int church = Scene.mix(Scene.mix(l.far, 0xFFFFFFFF, 0.45f * (1 - night)), FLOOD, 0.8f * night);
                p.solid(church).rect(64.2f, 50, 68.4f, 54);
                p.poly(64, 50.2f, 66.3f, 48.2f, 68.6f, 50.2f);
                p.rect(65.6f, 46.5f, 67, 49);
                p.glow(66.3f, 51, 6, FLOOD, 0.35f * night);
                p.tower(66, 76, 73, 100);
                p.body(74, 80).poly(74, 100, 74, 57, 80, 54.5f, 80, 100);
                p.windows(74, 57, 80, 100);
                p.tower(82, 51, 88, 100);
                if (night > 0.1f) { // Colpatria's coloured lights
                    p.solid(Scene.alpha(0xFF7FD3FF, 0.5f * night)).rect(82, 51, 88, 53);
                    p.solid(Scene.alpha(0xFFFF7FD0, 0.35f * night)).rect(82, 53, 88, 54.2f);
                }
                beacons.add(new float[]{85, 50.5f});
                p.body(89, 98).rect(89, 72, 98.5f, 100);
                p.windows(89, 72, 98.5f, 100);
                b.trees.add(tree(Tree.ROUND, 10, 100, 22, 0, r));
                b.trees.add(tree(Tree.ROUND, 21, 100, 16, 0, r));
                return true;
            }
            case "paris": {
                // the Eiffel Tower, golden at night (and sparkling for five minutes every hour after dark)
                float k = 0.85f * night;
                p.glow(80, 70, 26, FLOOD, 0.22f * night);
                p.tinted(64, 96, FLOOD, k);
                Path t = new Path();
                t.moveTo(p.X(65), p.Y(100));
                t.quadTo(p.X(73), p.Y(84), p.X(75.4f), p.Y(72.5f));
                t.quadTo(p.X(77.2f), p.Y(63), p.X(78.1f), p.Y(58.5f));
                t.lineTo(p.X(79.3f), p.Y(47));
                t.lineTo(p.X(79.6f), p.Y(42.5f));
                t.lineTo(p.X(80.4f), p.Y(42.5f));
                t.lineTo(p.X(80.7f), p.Y(47));
                t.lineTo(p.X(81.9f), p.Y(58.5f));
                t.quadTo(p.X(82.8f), p.Y(63), p.X(84.6f), p.Y(72.5f));
                t.quadTo(p.X(87), p.Y(84), p.X(95), p.Y(100));
                t.lineTo(p.X(89.5f), p.Y(100));
                t.quadTo(p.X(80), p.Y(83), p.X(70.5f), p.Y(100));
                t.close();
                p.c.drawPath(t, p.p);
                p.rect(72.6f, 71.4f, 87.4f, 74);
                p.rect(76.6f, 57.6f, 83.4f, 59.6f);
                p.rect(78.7f, 46, 81.3f, 48);
                p.line(80, 42.5f, 80, 37, 0.5f);
                p.solid(Scene.alpha(Scene.mix(l.near, 0xFF000000, 0.3f), 0.55f)); // its lattice
                p.line(76.2f, 74, 83.8f, 58.5f, 0.3f);
                p.line(83.8f, 74, 76.2f, 58.5f, 0.3f);
                p.line(77.8f, 58.5f, 81.3f, 47, 0.25f);
                p.line(82.2f, 58.5f, 78.7f, 47, 0.25f);
                p.line(68, 96, 75, 74, 0.25f);
                p.line(70, 87, 75.8f, 74, 0.25f);
                p.line(92, 96, 85, 74, 0.25f);
                p.line(90, 87, 84.2f, 74, 0.25f);
                p.line(71.5f, 88, 88.5f, 88, 0.3f);
                beacons.add(new float[]{80, 37});
                b.sparkle = new RectF(p.X(68), p.Y(40), p.X(92), p.Y(98));
                b.trees.add(tree(Tree.ROUND, 9, 100, 18, 0, r));
                b.trees.add(tree(Tree.ROUND, 19, 100, 14, 0, r));
                return true;
            }
            case "london": {
                // Parliament's roofline and the Elizabeth Tower (Big Ben), its clock lit at night
                p.tower(44, 82, 76, 100);
                for (float x = 46; x < 75; x += 4.8f) p.poly(x - 0.8f, 82.2f, x, 77.5f, x + 0.8f, 82.2f);
                p.body(76, 84).rect(76, 56, 84, 100);
                p.body(75.2f, 84.8f).rect(75.2f, 48.8f, 84.8f, 56);
                p.body(76.2f, 83.8f).rect(76.2f, 44, 83.8f, 48.8f);
                p.body(76.2f, 83.8f).poly(76.2f, 44.2f, 80, 31, 83.8f, 44.2f);
                p.solid(Scene.mix(Scene.mix(l.lit, 0xFFFFFFFF, 0.25f), 0xFFFFF1C4, 0.85f * l.lights));
                p.circle(80, 52.4f, 2.9f);
                p.solid(Scene.alpha(l.near, 0.8f));
                p.line(80, 52.4f, 80, 50.4f, 0.35f);
                p.line(80, 52.4f, 81.3f, 52.4f, 0.35f);
                p.glow(80, 52.4f, 7, 0xFFFFF1C4, 0.35f * night);
                b.trees.add(tree(Tree.ROUND, 10, 100, 18, 0, r));
                b.trees.add(tree(Tree.ROUND, 22, 100, 14, 0, r));
                return true;
            }
            case "new york": case "new york city": case "nyc": case "manhattan": case "brooklyn": {
                // the Empire State Building over Midtown, its top lit at night
                p.tower(50, 64, 60, 100);
                p.body(61, 68).poly(61, 100, 61, 58, 64.5f, 54, 68, 58, 68, 100);
                p.windows(61, 58, 68, 100);
                p.body(70, 90).rect(70, 80, 90, 100);
                p.body(72.5f, 87.5f).rect(72.5f, 64, 87.5f, 80);
                p.body(74.5f, 85.5f).rect(74.5f, 49, 85.5f, 64);
                p.windows(70, 64, 90, 100);
                p.windows(74.5f, 50, 85.5f, 64);
                p.tinted(76.5f, 83.5f, FLOOD, 0.8f * night).rect(76.5f, 45, 83.5f, 49);
                p.tinted(77.8f, 82.2f, FLOOD, 0.8f * night).rect(77.8f, 41, 82.2f, 45);
                p.tinted(79, 81, FLOOD, 0.8f * night).poly(79.2f, 41, 80, 31, 80.8f, 41);
                p.line(80, 31, 80, 25, 0.4f);
                beacons.add(new float[]{80, 25});
                p.glow(80, 42, 8, FLOOD, 0.3f * night);
                p.body(92, 98).rect(92, 68, 98.5f, 100);
                p.windows(92, 68, 98.5f, 100);
                b.trees.add(tree(Tree.ROUND, 10, 100, 15, 0, r));
                return true;
            }
            case "tokyo": {
                // the Tokyo Skytree: its tapering tower with two observation decks, blue at night
                int blue = 0xFF8FB8FF;
                p.tower(52, 80, 62, 100);
                p.body(88, 98).rect(88, 76, 98.5f, 100);
                p.windows(88, 76, 98.5f, 100);
                p.tinted(73, 87, blue, 0.6f * night).poly(73, 100, 78.6f, 57, 81.4f, 57, 87, 100);
                p.tinted(78.6f, 81.4f, blue, 0.6f * night).poly(78.6f, 57, 79.2f, 40, 80.8f, 40, 81.4f, 57);
                p.tinted(76.5f, 83.5f, blue, 0.7f * night).oval(76.5f, 55, 83.5f, 59.5f);
                p.tinted(77.6f, 82.4f, blue, 0.7f * night).oval(77.6f, 39.5f, 82.4f, 43);
                p.rect(79.6f, 26, 80.4f, 40);
                beacons.add(new float[]{80, 26});
                p.glow(80, 58, 12, blue, 0.25f * night);
                b.trees.add(tree(Tree.ROUND, 10, 100, 16, 0, r));
                return true;
            }
            case "sydney": {
                // the Opera House's sails on their podium, white, floodlit at night
                int white = Scene.mix(Scene.mix(l.lit, 0xFFFFFFFF, 0.5f * (1 - night)), 0xFFFFF2DE, 0.75f * night);
                p.body(46, 99).rect(46, 92, 99, 100);
                float[][] sails = {{49, 63, 52, 78}, {56, 72, 60, 70.5f}, {65, 80, 69, 74.5f}, {74, 90, 78, 67.5f}, {84, 97.5f, 87, 75.5f}};
                Paint seam = new Paint(Paint.ANTI_ALIAS_FLAG);
                seam.setStyle(Paint.Style.STROKE);
                seam.setStrokeWidth(Math.max(1f, p.S(0.55f)));
                seam.setColor(Scene.mix(white, l.near, 0.55f));
                for (float[] sl : sails) {
                    Path sp = new Path();
                    sp.moveTo(p.X(sl[0]), p.Y(92));
                    sp.quadTo(p.X(sl[0] - 0.5f), p.Y(sl[3] + 7), p.X(sl[2]), p.Y(sl[3]));
                    sp.quadTo(p.X(sl[1] - 1.5f), p.Y(sl[3] + 3), p.X(sl[1]), p.Y(92));
                    sp.close();
                    p.solid(Scene.mix(white, l.near, 0.12f));
                    p.c.drawPath(sp, p.p);
                    Path edge = new Path();
                    edge.moveTo(p.X(sl[2]), p.Y(sl[3]));
                    edge.quadTo(p.X(sl[1] - 1.5f), p.Y(sl[3] + 3), p.X(sl[1]), p.Y(92));
                    p.c.drawPath(edge, seam);
                }
                p.glow(74, 84, 20, 0xFFFFF2DE, 0.2f * night);
                b.trees.add(tree(Tree.PALM, 12, 100, 30, 0.1f, r));
                return true;
            }
            case "dubai": {
                // the Burj Khalifa, stepping up to its spire
                float[][] tiers = {{76, 88, 88}, {77.5f, 86.5f, 76}, {78.6f, 85.4f, 64}, {79.4f, 84.6f, 54},
                        {80.2f, 83.8f, 45}, {80.8f, 83.2f, 38}};
                float below = 100;
                for (float[] t : tiers) {
                    p.body(t[0], t[1]).rect(t[0], t[2], t[1], below);
                    p.windows(t[0], t[2], t[1], below);
                    below = t[2];
                }
                p.body(81.4f, 82.6f).poly(81.4f, 38, 82, 21, 82.6f, 38);
                beacons.add(new float[]{82, 21});
                p.body(55, 62).poly(55, 100, 55, 64, 58.5f, 60, 62, 64, 62, 100);
                p.windows(55, 64, 62, 100);
                p.tower(64, 74, 71, 100);
                p.body(90, 97).rect(90, 70, 97.5f, 100);
                p.windows(90, 70, 97.5f, 100);
                b.trees.add(tree(Tree.PALM, 11, 100, 30, 0.08f, r));
                b.trees.add(tree(Tree.PALM, 21, 100, 24, -0.1f, r));
                return true;
            }
            case "berlin": {
                // the Fernsehturm: its sphere on a needle
                p.tower(50, 82, 62, 100);
                p.body(88, 98).rect(88, 80, 98.5f, 100);
                p.windows(88, 80, 98.5f, 100);
                p.body(77, 83).poly(77, 100, 78.9f, 92, 79.4f, 56, 80.6f, 56, 81.1f, 92, 83, 100);
                p.body(74.6f, 85.4f).circle(80, 51.5f, 5.4f);
                if (l.lights > 0.05f) p.solid(Scene.alpha(WINDOW, 0.7f * l.lights)).rect(75.2f, 51.1f, 84.8f, 52.1f);
                p.body(79.4f, 80.6f).rect(79.5f, 39, 80.5f, 46.4f);
                p.poly(79.7f, 39, 80, 22, 80.3f, 39);
                beacons.add(new float[]{80, 22});
                b.trees.add(tree(Tree.ROUND, 10, 100, 18, 0, r));
                b.trees.add(tree(Tree.ROUND, 21, 100, 13, 0, r));
                return true;
            }
            case "toronto": {
                // the CN Tower over the skyline
                p.tower(52, 66, 60, 100);
                p.tower(61, 74, 70, 100);
                p.body(77.5f, 82.5f).poly(77.5f, 100, 79.3f, 53, 80.7f, 53, 82.5f, 100);
                p.body(75.5f, 84.5f).oval(75.5f, 49, 84.5f, 55.5f);
                if (l.lights > 0.05f) p.solid(Scene.alpha(WINDOW, 0.7f * l.lights)).rect(76, 51.8f, 84, 52.8f);
                p.body(79.6f, 80.4f).rect(79.6f, 37, 80.4f, 49);
                p.oval(78.8f, 37, 81.2f, 39.6f);
                p.poly(79.7f, 37, 80, 20, 80.3f, 37);
                beacons.add(new float[]{80, 20});
                p.body(86, 97).rect(86, 64, 97.5f, 100);
                p.windows(86, 64, 97.5f, 100);
                b.trees.add(tree(Tree.FIR, 10, 100, 18, 0, r));
                b.trees.add(tree(Tree.ROUND, 20, 100, 14, 0, r));
                return true;
            }
            case "seattle": {
                // Mount Rainier far behind and the Space Needle
                p.solid(l.far).poly(14, 100, 38, 74, 44, 70, 50, 75, 74, 100);
                p.solid(Scene.mix(l.far, 0xFFFFFFFF, 0.5f * (1 - night) + 0.15f)).poly(38.8f, 73.4f, 44, 70, 48.6f, 74, 46, 75, 43.5f, 73.8f, 41, 75.6f);
                p.body(74, 86).poly(74.5f, 100, 79.3f, 71, 76.6f, 53.5f, 78.2f, 53.5f, 80, 68, 81.8f, 53.5f, 83.4f, 53.5f, 80.7f, 71, 85.5f, 100, 84, 100, 80, 75, 76, 100);
                p.body(72, 88).oval(72, 48, 88, 54);
                if (l.lights > 0.05f) p.solid(Scene.alpha(WINDOW, 0.75f * l.lights)).rect(73, 50.6f, 87, 51.6f);
                p.body(75.5f, 84.5f).poly(75.5f, 48.2f, 80, 44.5f, 84.5f, 48.2f);
                p.rect(79.7f, 36, 80.3f, 44.8f);
                beacons.add(new float[]{80, 36});
                b.trees.add(tree(Tree.FIR, 9, 100, 24, 0, r));
                b.trees.add(tree(Tree.FIR, 19, 100, 18, 0, r));
                return true;
            }
            case "rio de janeiro": case "rio": {
                // Sugarloaf far off, Corcovado with Christ the Redeemer, white and floodlit at night
                p.solid(l.far).poly(26, 100, 30, 82, 36, 74, 42, 73, 47, 80, 52, 100);
                p.body(54, 100).poly(54, 100, 66, 80, 74, 67, 79, 62.5f, 82, 62.5f, 88, 70, p.right() + 2, 86, p.right() + 2, 100);
                int stone = Scene.mix(Scene.mix(l.lit, 0xFFFFFFFF, 0.4f * (1 - night)), 0xFFFFF4E0, 0.85f * night);
                p.glow(80.5f, 56, 9, 0xFFFFF4E0, 0.35f * night);
                p.solid(stone).rect(79.9f, 53, 81.1f, 62.6f);
                p.poly(79.6f, 62.6f, 81.4f, 62.6f, 81.1f, 58, 79.9f, 58);
                p.rect(77, 54.2f, 84, 55.3f);
                p.circle(80.5f, 52.2f, 0.85f);
                b.trees.add(tree(Tree.PALM, 10, 100, 30, 0.1f, r));
                b.trees.add(tree(Tree.PALM, 20, 100, 24, -0.06f, r));
                return true;
            }
            case "cairo": case "giza": {
                // the pyramids of Giza, lit on the side facing the sun
                drawPyramid(p, 50, 88, 100, 69, l);
                drawPyramid(p, 74, 99, 100, 79, l);
                drawPyramid(p, 40, 56, 100, 90, l);
                b.trees.add(tree(Tree.PALM, 12, 100, 28, 0.1f, r));
                b.trees.add(tree(Tree.PALM, 22, 100, 22, -0.08f, r));
                return true;
            }
            case "san francisco": {
                // a tower of the Golden Gate Bridge, its cables sweeping down, and the Transamerica Pyramid
                int orange = Scene.mix(0xFFC0362C, FLOOD, 0.5f * night);
                p.tinted(50, 59, orange, 0.45f + 0.3f * night);
                p.rect(51.5f, 62, 53, 100);
                p.rect(57, 62, 58.5f, 100);
                p.rect(51.5f, 63.5f, 58.5f, 65);
                p.rect(51.5f, 74, 58.5f, 75.3f);
                p.rect(51.5f, 85, 58.5f, 86.2f);
                p.p.setStyle(Paint.Style.STROKE);
                p.p.setStrokeWidth(Math.max(0.8f, p.S(0.45f)));
                Path cable = new Path();
                cable.moveTo(p.X(20), p.Y(90));
                cable.quadTo(p.X(40), p.Y(88), p.X(55), p.Y(62.5f));
                cable.quadTo(p.X(64), p.Y(84), p.X(74), p.Y(90));
                p.c.drawPath(cable, p.p);
                p.p.setStyle(Paint.Style.FILL);
                p.body(16, 74).rect(16, 90, 74, 91.6f);
                p.body(77, 87).poly(77, 100, 82, 42, 87, 100);
                p.body(75.6f, 88.4f).rect(75.6f, 64, 77.6f, 82);
                p.rect(86.4f, 64, 88.4f, 82);
                p.windows(78.5f, 60, 85.5f, 100);
                beacons.add(new float[]{82, 42});
                p.body(90, 98).rect(90, 76, 98.5f, 100);
                p.windows(90, 76, 98.5f, 100);
                return true;
            }
            case "rome": case "roma": {
                // the Colosseum, its arches in tiers, the top broken off on one side; warm floodlights at night
                float k = 0.75f * night;
                p.tinted(48, 99, FLOOD, k).poly(48, 100, 48, 84, 56, 82, 64, 80, 72, 76, 99, 73.5f, 99, 100);
                p.solid(Scene.mix(l.near, 0xFF000000, 0.45f));
                for (int row = 0; row < 3; row++) {
                    float t = 79 + row * 7, bt = t + 4.6f;
                    for (float x = 50; x < 97; x += 4) {
                        if (row == 0 && x < 70) continue;
                        p.rect(x, t + 1.2f, x + 2.2f, bt);
                        p.circle(x + 1.1f, t + 1.3f, 1.1f);
                    }
                }
                p.glow(74, 86, 22, FLOOD, 0.22f * night);
                b.trees.add(tree(Tree.FIR, 12, 100, 24, 0, r));
                b.trees.add(tree(Tree.ROUND, 24, 100, 15, 0, r));
                return true;
            }
            case "barcelona": {
                // the Sagrada Família's spires, the tallest crowned with a star
                p.body(58, 94).rect(58, 82, 94, 100);
                float[][] spires = {{61, 60}, {66, 54}, {71, 50}, {76, 38}, {81, 50}, {86, 54}, {91, 60}};
                for (float[] s : spires) {
                    float w = s[0] == 76 ? 3.4f : 2.4f;
                    p.body(s[0] - w, s[0] + w).poly(s[0] - w, 82.5f, s[0] - w * 0.55f, s[1] + 6, s[0], s[1], s[0] + w * 0.55f, s[1] + 6, s[0] + w, 82.5f);
                    p.circle(s[0], s[1], w * 0.4f);
                }
                p.solid(Scene.mix(l.lit, 0xFFFFFFFF, 0.3f + 0.5f * night)).circle(76, 36.6f, 1.2f);
                p.body(95, 97).rect(96, 44, 96.8f, 100); // a crane: it's still being built
                p.rect(86, 44, 99, 45);
                b.trees.add(tree(Tree.PALM, 11, 100, 28, 0.1f, r));
                b.trees.add(tree(Tree.ROUND, 22, 100, 14, 0, r));
                return true;
            }
            case "chicago": {
                // the Willis Tower's bundled tubes and its two antennas
                float[][] tubes = {{70, 74, 60}, {74, 78, 44}, {78, 82, 40}, {82, 86, 52}, {86, 90, 66}};
                for (float[] t : tubes) {
                    p.body(70, 90).rect(t[0], t[2], t[1], 100);
                    p.windows(t[0], t[2], t[1], 100);
                }
                p.line(76.5f, 44, 76.5f, 28, 0.55f);
                p.line(80.5f, 40, 80.5f, 26, 0.55f);
                beacons.add(new float[]{76.5f, 28});
                beacons.add(new float[]{80.5f, 26});
                p.tower(52, 62, 60, 100);
                p.body(61, 68).poly(61, 100, 61, 70, 64.5f, 66, 68, 70, 68, 100);
                p.windows(61, 70, 68, 100);
                p.body(92, 98).rect(92, 72, 98.5f, 100);
                p.windows(92, 72, 98.5f, 100);
                b.trees.add(tree(Tree.ROUND, 10, 100, 15, 0, r));
                return true;
            }
            case "los angeles": case "la": case "hollywood": {
                // hills far behind, the US Bank Tower's crown, and tall thin palms
                p.solid(l.far).poly(p.left() - 1, 100, p.left() + 6, 82, 40, 76, 60, 82, 80, 77, 100, 84, p.right() + 1, 86, p.right() + 1, 100);
                p.tower(76, 54, 84, 100);
                p.body(76, 84).poly(76, 54.2f, 77, 51.5f, 83, 51.5f, 84, 54.2f);
                beacons.add(new float[]{80, 51.5f});
                p.tower(64, 66, 72, 100);
                p.tower(87, 70, 95, 100);
                b.trees.add(tree(Tree.PALM, 9, 100, 50, 0.04f, r));
                b.trees.add(tree(Tree.PALM, 17, 100, 44, -0.03f, r));
                b.trees.add(tree(Tree.PALM, wide - 3, 100, 38, -0.05f, r));
                return true;
            }
            case "miami": case "miami beach": {
                // palms and the towers along the bay
                for (int i = 0; i < 4; i++) {
                    float x = 58 + i * 9.5f, t = 56 + (i % 2) * 10 + r.nextFloat() * 4;
                    p.tower(x, t, x + 7, 100);
                }
                b.trees.add(tree(Tree.PALM, 10, 100, 40, 0.1f, r));
                b.trees.add(tree(Tree.PALM, 19, 100, 32, -0.06f, r));
                b.trees.add(tree(Tree.PALM, wide - 22, 100, 26, 0.08f, r));
                return true;
            }
            case "buenos aires": {
                // the Obelisco
                p.tower(54, 72, 64, 100);
                p.body(88, 98).rect(88, 74, 98.5f, 100);
                p.windows(88, 74, 98.5f, 100);
                p.tinted(77.5f, 82.5f, FLOOD, 0.5f * night).poly(77.4f, 100, 78.6f, 48, 80, 44.5f, 81.4f, 48, 82.6f, 100);
                p.glow(80, 70, 12, FLOOD, 0.2f * night);
                b.trees.add(tree(Tree.ROUND, 10, 100, 20, 0, r));
                b.trees.add(tree(Tree.ROUND, 22, 100, 15, 0, r));
                return true;
            }
            case "washington": case "washington dc": {
                // the Capitol's dome and the Washington Monument
                float k = 0.7f * night;
                p.tinted(46, 72, FLOOD, k).rect(46, 86, 72, 100);
                p.tinted(52, 66, FLOOD, k).rect(52, 76, 66, 86);
                p.tinted(53, 65, FLOOD, k).dome(53, 65, 77, 64);
                p.rect(58.3f, 60, 59.7f, 64.2f);
                p.tinted(78.5f, 85.5f, FLOOD, k * 0.8f).poly(79.6f, 100, 80.6f, 41, 82, 37.5f, 83.4f, 41, 84.4f, 100);
                beacons.add(new float[]{82, 37.5f});
                p.glow(59, 76, 14, FLOOD, 0.22f * night);
                b.trees.add(tree(Tree.ROUND, 10, 100, 18, 0, r));
                b.trees.add(tree(Tree.ROUND, 22, 100, 13, 0, r));
                return true;
            }
            case "singapore": {
                // Marina Bay Sands: three towers and the ship across their tops
                for (int i = 0; i < 3; i++) {
                    float x = 63 + i * 10;
                    p.body(x, x + 7).poly(x - 0.6f, 100, x, 60, x + 7, 60, x + 7.6f, 100);
                    p.windows(x, 61, x + 7, 100);
                }
                p.body(57, 96).poly(56.5f, 57.2f, 95, 56.5f, 96, 59.6f, 60, 60.2f);
                p.tower(48, 74, 56, 100);
                b.trees.add(tree(Tree.PALM, 10, 100, 30, 0.08f, r));
                b.trees.add(tree(Tree.ROUND, 22, 100, 15, 0, r));
                return true;
            }
            case "kuala lumpur": {
                // the Petronas Twin Towers and their skybridge
                for (float cx : new float[]{70, 86}) {
                    p.tower(cx - 4.5f, 60, cx + 4.5f, 100);
                    p.body(cx - 3.6f, cx + 3.6f).rect(cx - 3.6f, 52, cx + 3.6f, 60);
                    p.body(cx - 2.6f, cx + 2.6f).rect(cx - 2.6f, 46, cx + 2.6f, 52);
                    p.body(cx - 1.6f, cx + 1.6f).poly(cx - 1.6f, 46, cx, 40, cx + 1.6f, 46);
                    p.line(cx, 40, cx, 30, 0.45f);
                    beacons.add(new float[]{cx, 30});
                }
                p.body(74, 82).rect(74.5f, 68.5f, 81.5f, 70.5f);
                b.trees.add(tree(Tree.PALM, 10, 100, 30, 0.08f, r));
                b.trees.add(tree(Tree.PALM, 20, 100, 22, -0.08f, r));
                return true;
            }
            case "istanbul": {
                // a great dome among minarets, over the Bosphorus' shore
                p.body(54, 94).rect(54, 84, 94, 100);
                p.body(58, 90).rect(58, 77, 90, 84);
                p.body(61, 87).dome(61, 87, 77.5f, 63);
                p.rect(73.4f, 59.6f, 74.6f, 63.5f);
                p.body(58, 66).dome(57, 66, 79, 73);
                p.body(82, 91).dome(82, 91, 79, 73);
                float[][] minarets = {{54, 50}, {94.5f, 50}, {59.5f, 58}, {88.5f, 58}};
                for (float[] m : minarets) {
                    p.body(m[0] - 0.9f, m[0] + 0.9f).rect(m[0] - 0.9f, m[1], m[0] + 0.9f, 100);
                    p.rect(m[0] - 1.4f, m[1] + 8, m[0] + 1.4f, m[1] + 9); // its balcony
                    p.poly(m[0] - 0.9f, m[1] + 0.2f, m[0], m[1] - 6, m[0] + 0.9f, m[1] + 0.2f);
                }
                p.glow(74, 76, 18, FLOOD, 0.2f * night);
                b.trees.add(tree(Tree.FIR, 12, 100, 22, 0, r));
                return true;
            }
            case "amsterdam": {
                // a row of canal houses with their gables
                float x = 47;
                int kind = 0;
                while (x < 99) {
                    float w = 7 + r.nextFloat() * 2.5f, t = 64 + r.nextFloat() * 10;
                    p.body(x, x + w).rect(x, t, x + w, 100);
                    switch (kind % 4) {
                        case 0: // a step gable
                            p.rect(x + w * 0.14f, t - 2.6f, x + w * 0.86f, t + 0.2f);
                            p.rect(x + w * 0.28f, t - 5.2f, x + w * 0.72f, t - 2.4f);
                            p.rect(x + w * 0.42f, t - 7.4f, x + w * 0.58f, t - 5);
                            break;
                        case 1: // a bell gable
                            p.dome(x + w * 0.1f, x + w * 0.9f, t + 0.2f, t - 6);
                            p.rect(x + w * 0.44f, t - 7.4f, x + w * 0.56f, t - 5.6f);
                            break;
                        case 2: // a neck gable
                            p.rect(x + w * 0.27f, t - 6, x + w * 0.73f, t + 0.2f);
                            p.poly(x + w * 0.27f, t - 5.8f, x + w / 2, t - 8.2f, x + w * 0.73f, t - 5.8f);
                            break;
                        default: // a plain pointed gable
                            p.poly(x, t + 0.2f, x + w / 2, t - 6, x + w, t + 0.2f);
                    }
                    p.windows(x, t, x + w, 100);
                    x += w + 0.5f;
                    kind++;
                }
                b.trees.add(tree(Tree.ROUND, 10, 100, 20, 0, r));
                b.trees.add(tree(Tree.ROUND, 22, 100, 15, 0, r));
                return true;
            }
            case "moscow": case "moskva": {
                // Saint Basil's onion domes round its tented spire, their colours showing by day
                int[] colours = {0xFFD9493E, 0xFF3F9A5B, 0xFF3E6FD9, 0xFFE8B84A};
                p.body(58, 94).rect(58, 82, 94, 100);
                p.body(74, 78).poly(73.5f, 82, 76, 44, 78.5f, 82);
                float[][] domes = {{64, 62, 3.4f}, {70, 57, 3.8f}, {82, 57, 3.8f}, {88, 62, 3.4f}};
                for (int i = 0; i < domes.length; i++) {
                    float[] d = domes[i];
                    p.body(d[0] - d[2], d[0] + d[2]).rect(d[0] - d[2] * 0.7f, d[1] + d[2], d[0] + d[2] * 0.7f, 82);
                    p.tinted(d[0] - d[2], d[0] + d[2], colours[i], 0.55f * (1 - night) + 0.3f * night);
                    Path on = new Path();
                    on.moveTo(p.X(d[0] - d[2]), p.Y(d[1] + d[2]));
                    on.cubicTo(p.X(d[0] - d[2] * 1.3f), p.Y(d[1]), p.X(d[0] - 0.3f), p.Y(d[1] - d[2] * 0.6f), p.X(d[0]), p.Y(d[1] - d[2] * 1.5f));
                    on.cubicTo(p.X(d[0] + 0.3f), p.Y(d[1] - d[2] * 0.6f), p.X(d[0] + d[2] * 1.3f), p.Y(d[1]), p.X(d[0] + d[2]), p.Y(d[1] + d[2]));
                    on.close();
                    p.c.drawPath(on, p.p);
                }
                b.trees.add(tree(Tree.FIR, 12, 100, 22, 0, r));
                b.trees.add(tree(Tree.FIR, 22, 100, 16, 0, r));
                return true;
            }
            default:
                return false;
        }
    }

    private static void drawPyramid(Pen p, float l0, float r0, float base, float top, Light l) {
        float mid = (l0 + r0) / 2;
        int s = l.lit, d = Scene.mix(l.near, 0xFF000000, 0.15f);
        p.solid(l.sunLeft ? s : d).poly(l0, base, mid, top, mid + (r0 - l0) * 0.08f, base);
        p.solid(l.sunLeft ? d : s).poly(mid + (r0 - l0) * 0.08f, base, mid, top, r0, base);
    }

    /**
     * Any other city: a corner of its own, made from its name (since 2.27 as its streets are built, {@link #style}):
     * towers in a big city, European town houses round a church, or a few low houses among the trees; and the trees
     * of its latitude.
     */
    private static void generic(Pen p, Built b, List<float[]> beacons, Light l, double lat, float wide, int seed, int style) {
        Random r = new Random(seed);
        if (style == EURO) genericEuro(p, l, r);
        else if (style == HOUSES) genericHouses(p, l, r);
        else genericTowers(p, beacons, r);
        double a = Double.isNaN(lat) ? 40 : Math.abs(lat);
        int kind = a < 28 ? Tree.PALM : a > 52 ? Tree.FIR : Tree.ROUND;
        float hScale = kind == Tree.PALM ? 34 : kind == Tree.FIR ? 22 : 18;
        b.trees.add(tree(kind, 10, 100, hScale, kind == Tree.PALM ? 0.1f : 0, r));
        b.trees.add(tree(kind, 21, 100, hScale * 0.78f, kind == Tree.PALM ? -0.07f : 0, r));
        if (style == HOUSES) b.trees.add(tree(kind, 33, 100, hScale * 0.9f, kind == Tree.PALM ? 0.06f : 0, r));
    }

    private static void genericTowers(Pen p, List<float[]> beacons, Random r) {
        float x = 98.5f;
        int n = 0;
        while (x > 48 && n < 9) {
            float w = 5 + r.nextFloat() * 5, t = 100 - (14 + r.nextFloat() * 22 + (x > 70 ? 10 : 0));
            float l0 = x - w;
            p.body(l0, x);
            switch (r.nextInt(6)) {
                case 0: // a spire
                    p.rect(l0, t, x, 100);
                    p.poly(l0 + w * 0.3f, t + 0.2f, l0 + w / 2, t - 8, l0 + w * 0.7f, t + 0.2f);
                    beacons.add(new float[]{l0 + w / 2, t - 8});
                    break;
                case 1: // set back
                    p.rect(l0, t + 6, x, 100);
                    p.rect(l0 + w * 0.2f, t, x - w * 0.2f, t + 6.2f);
                    break;
                case 2: // a slanted top
                    p.poly(l0, 100, l0, t + 4, x, t, x, 100);
                    break;
                case 3: // an antenna
                    p.rect(l0, t, x, 100);
                    p.line(l0 + w / 2, t, l0 + w / 2, t - 6, 0.4f);
                    beacons.add(new float[]{l0 + w / 2, t - 6});
                    break;
                default:
                    p.rect(l0, t, x, 100);
            }
            p.edges(l0, t, x, 100);
            p.windows(l0, t, x, 100);
            x = l0 - 0.6f - r.nextFloat() * 1.6f;
            n++;
        }
    }

    /** A European corner: town houses with tall windows round a church with its spire. */
    private static void genericEuro(Pen p, Light l, Random r) {
        float x = 98.5f;
        int n = 0;
        boolean church = false;
        while (x > 50 && n < 7) {
            if (!church && x < 86) { // the church: its tower, spire and a round window
                church = true;
                float w = 7, t = 100 - 30;
                p.body(x - w, x).rect(x - w, t, x, 100);
                p.solid(Scene.mix(l.near, 0xFF000000, 0.25f)).poly(x - w - 0.4f, t, x - w / 2, t - 15, x + 0.4f, t);
                p.edges(x - w, t, x, 100);
                p.solid(Scene.alpha(l.lights > 0.1f ? WINDOW : Scene.mix(l.horizon, 0xFFFFFFFF, 0.3f),
                        l.lights > 0.1f ? 0.75f * l.lights : 0.18f)).circle(x - w / 2, t + 5, 1.5f);
                p.solid(Scene.alpha(0xFF000000, 0.3f)).rect(x - w / 2 - 1.2f, 100 - 6, x - w / 2 + 1.2f, 100);
                x -= w + 0.4f;
                continue;
            }
            float w = 6 + r.nextFloat() * 3;
            int floors = 3 + r.nextInt(3);
            float storey = 3.6f, t = 100 - floors * storey - 1, l0 = x - w;
            p.body(l0, x).rect(l0, t, x, 100);
            int roof = Scene.mix(l.near, 0xFF000000, 0.25f);
            if (r.nextBoolean()) p.solid(roof).poly(l0 - 0.3f, t, l0 + w / 2, t - w * 0.45f, x + 0.3f, t);
            else p.solid(roof).poly(l0 - 0.3f, t, l0 + 0.8f, t - 3.2f, x - 0.8f, t - 3.2f, x + 0.3f, t);
            p.edges(l0, t, x, 100);
            int per = Math.max(1, (int) ((w - 1.4f) / 1.8f));
            float ww = 1f, wh = 1.9f, gap = (w - per * ww) / (per + 1);
            for (int f = 0; f < floors - 1; f++) {
                for (int i = 0; i < per; i++) {
                    float wx = l0 + gap + i * (ww + gap), wy = t + 1.1f + f * storey;
                    boolean on = l.lights > 0.04f && r.nextFloat() < 0.5f;
                    p.solid(on ? Scene.alpha(WINDOW, 0.85f * l.lights)
                            : Scene.alpha(Scene.mix(l.horizon, 0xFFFFFFFF, 0.25f), 0.16f * (1 - 0.7f * l.lights)))
                            .rect(wx, wy, wx + ww, wy + wh);
                }
            }
            p.solid(Scene.alpha(0xFF000000, 0.25f)).rect(l0 + w / 2 - 0.7f, 100 - 3, l0 + w / 2 + 0.7f, 100);
            x = l0 - 0.3f;
            n++;
        }
    }

    /** A smaller city's corner: a few low houses under pitched roofs, their windows lit at night. */
    private static void genericHouses(Pen p, Light l, Random r) {
        float x = 98.5f;
        int n = 0;
        while (x > 52 && n < 4) {
            float w = 9 + r.nextFloat() * 4, t = 100 - (7 + r.nextFloat() * 5), l0 = x - w;
            p.body(l0, x).rect(l0, t, x, 100);
            p.solid(Scene.mix(l.near, 0xFF000000, 0.28f)).poly(l0 - 0.8f, t + 0.3f, l0 + w / 2, t - w * 0.3f, x + 0.8f, t + 0.3f);
            p.edges(l0, t, x, 100);
            for (int i = 0; i < 2; i++) {
                float wx = l0 + w * (i == 0 ? 0.18f : 0.6f), wy = t + (100 - t) * 0.3f;
                boolean on = l.lights > 0.04f && r.nextFloat() < 0.6f;
                p.solid(on ? Scene.alpha(WINDOW, 0.85f * l.lights)
                        : Scene.alpha(Scene.mix(l.horizon, 0xFFFFFFFF, 0.25f), 0.16f * (1 - 0.7f * l.lights)))
                        .rect(wx, wy, wx + 1.8f, wy + 1.6f);
            }
            p.solid(Scene.alpha(0xFF000000, 0.25f)).rect(l0 + w * 0.42f, 100 - 3.4f, l0 + w * 0.42f + 1.5f, 100);
            x = l0 - 3 - r.nextFloat() * 3;
            n++;
        }
    }

    // ------------------------------------------------------------------ what moves

    /** The trees at {@code t} seconds, swaying in a {@code wind} km/h wind (a breeze when unknown). */
    static void trees(Canvas c, Built b, Light l, float t, float wind, float px) {
        if (b.trees.isEmpty()) return;
        float w = wind < 0 ? 8f : wind;
        float amp = (float) Math.toRadians(Math.min(8f, 1.3f + w * 0.16f));
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(l.front);
        int litLeaf = Scene.mix(l.front, l.lit, 0.42f * (1 - 0.7f * l.night));
        for (Tree tr : b.trees) {
            float period = 3.6f + (tr.phase % 1.4f);
            float sway = amp * ((float) Math.sin(t * 6.2832f / period + tr.phase)
                    + 0.35f * (float) Math.sin(t * 6.2832f / (period * 0.43f) + tr.phase * 1.7f));
            switch (tr.kind) {
                case Tree.PALM: palm(c, p, tr, sway, px); break;
                case Tree.FIR: fir(c, p, tr, sway); break;
                default: round(c, p, tr, sway, litLeaf, l.sunLeft);
            }
        }
    }

    /** Palm fronds' angles from the crown (degrees; up is -90): none straight up, the outer ones longest. */
    private static final float[] FRONDS = {-176, -158, -138, -118, -98, -80, -60, -40, -20, -3};

    /**
     * A palm (2.27, after the video): a tall, thin, gently curved trunk and a crown of drooping fronds, each a long
     * blade with a serrated lower edge, as cut from paper.
     */
    private static void palm(Canvas c, Paint p, Tree t, float sway, float px) {
        float h = t.height, topX = t.x + t.lean * h + sway * h * 0.18f, topY = t.ground - h;
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeWidth(Math.max(1f, h * 0.024f));
        Path trunk = new Path();
        trunk.moveTo(t.x, t.ground);
        trunk.quadTo(t.x + t.lean * h * 0.25f + h * 0.04f, t.ground - h * 0.55f, topX, topY);
        c.drawPath(trunk, p);
        p.setStyle(Paint.Style.FILL);
        float len = h * 0.42f;
        for (float deg : FRONDS) {
            double a = Math.toRadians(deg) + sway * 1.4f;
            float cos = (float) Math.cos(a), sin = (float) Math.sin(a), droop = Math.abs(cos);
            float L = len * (0.62f + 0.38f * droop);
            float ex = topX + cos * L * (1f - 0.12f * droop), ey = topY + sin * L * (1f - 0.62f * droop) + droop * L * 0.55f;
            float cx = topX + cos * L * 0.5f, cy = topY + sin * L * 0.5f - droop * L * 0.22f;
            Path leaf = new Path();
            int k = 9;
            float[] bx = new float[k + 1], by = new float[k + 1], nx = new float[k + 1], ny = new float[k + 1];
            for (int i = 0; i <= k; i++) {
                float q = i / (float) k, m = 1 - q;
                bx[i] = m * m * topX + 2 * m * q * cx + q * q * ex;
                by[i] = m * m * topY + 2 * m * q * cy + q * q * ey;
                float tx = 2 * m * (cx - topX) + 2 * q * (ex - cx), ty = 2 * m * (cy - topY) + 2 * q * (ey - cy);
                float tl = Math.max(0.001f, (float) Math.hypot(tx, ty));
                nx[i] = -ty / tl;
                ny[i] = tx / tl;
                if (ny[i] < 0) { nx[i] = -nx[i]; ny[i] = -ny[i]; } // "down" side of the blade
            }
            float th = h * 0.03f;
            leaf.moveTo(bx[0], by[0]);
            for (int i = 1; i <= k; i++) { // its upper edge, smooth
                float q = i / (float) k;
                leaf.lineTo(bx[i] - nx[i] * th * 0.35f * (1 - q), by[i] - ny[i] * th * 0.35f * (1 - q));
            }
            for (int i = k - 1; i >= 1; i--) { // its lower edge, in points: the leaflets hanging from it
                float q = i / (float) k, deep = th * (2.4f - 1.6f * q);
                leaf.lineTo(bx[i] + nx[i] * deep, by[i] + ny[i] * deep + deep * 0.5f);
                leaf.lineTo(bx[i] + nx[i] * th * 0.3f, by[i] + ny[i] * th * 0.3f);
            }
            leaf.close();
            c.drawPath(leaf, p);
        }
        c.drawCircle(topX, topY, h * 0.03f, p);
    }

    /**
     * A round tree (2.27, after the video): a thin trunk and a crown cut as from paper, flat in two tones: its shade,
     * and the side facing the light a lighter one, the same ragged shape moved towards the light and cut to the crown.
     */
    private static void round(Canvas c, Paint p, Tree t, float sway, int lit, boolean sunLeft) {
        float h = t.height;
        int shade = p.getColor();
        c.drawRect(t.x - h * 0.018f, t.ground - h * 0.46f, t.x + h * 0.018f, t.ground, p);
        c.save();
        c.rotate((float) Math.toDegrees(sway * 0.6f), t.x, t.ground - h * 0.4f);
        float cy = t.ground - h * 0.68f, side = sunLeft ? -1 : 1;
        Path crown = new Path();
        for (int i = 0; i < t.puffs.length; i += 2) {
            float x = t.x + t.puffs[i] * h, y = cy + t.puffs[i + 1] * h;
            if (i == 0) crown.moveTo(x, y); else crown.lineTo(x, y);
        }
        crown.close();
        c.drawPath(crown, p);
        c.save();
        c.clipPath(crown);
        Path litSide = new Path();
        for (int i = 0; i < t.puffs.length; i += 2) {
            float x = t.x + (t.puffs[i] * 0.82f + side * 0.09f) * h, y = cy + (t.puffs[i + 1] * 0.82f - 0.07f) * h;
            if (i == 0) litSide.moveTo(x, y); else litSide.lineTo(x, y);
        }
        litSide.close();
        p.setColor(lit);
        c.drawPath(litSide, p);
        c.restore();
        p.setColor(shade);
        c.restore();
    }

    private static void fir(Canvas c, Paint p, Tree t, float sway) {
        float h = t.height, shift = sway * h * 0.12f;
        c.drawRect(t.x - h * 0.03f, t.ground - h * 0.2f, t.x + h * 0.03f, t.ground, p);
        for (int i = 0; i < 3; i++) {
            float base = t.ground - h * (0.14f + i * 0.24f), top = base - h * 0.42f, half = h * (0.24f - i * 0.05f);
            float s = shift * (0.4f + i * 0.3f);
            Path tier = new Path();
            tier.moveTo(t.x - half + s * 0.3f, base);
            tier.lineTo(t.x + s, top);
            tier.lineTo(t.x + half + s * 0.3f, base);
            tier.close();
            c.drawPath(tier, p);
        }
    }

    /** The red beacons on the tallest tops, blinking at night. */
    static void beacons(Canvas c, Built b, Light l, float t, float px) {
        if (l.night < 0.25f || b.beacons.length == 0) return;
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        for (int i = 0; i < b.beacons.length; i += 2) {
            float on = (float) Math.pow(0.5f + 0.5f * Math.sin(t * 4.4f + i), 3);
            float a = l.night * on;
            if (a < 0.03f) continue;
            float x = b.beacons[i], y = b.beacons[i + 1];
            p.setShader(new RadialGradient(x, y, 3.2f * px, Scene.alpha(BEACON, 0.45f * a), BEACON & 0x00FFFFFF,
                    Shader.TileMode.CLAMP));
            c.drawCircle(x, y, 3.2f * px, p);
            p.setShader(null);
            p.setColor(Scene.alpha(0xFFFF6A5E, a));
            c.drawCircle(x, y, 0.75f * px, p);
        }
    }

    /** The Eiffel Tower's sparkle: for the first five minutes of every hour after dark, lights flashing all over it. */
    static void sparkle(Canvas c, Built b, Light l, float t, int minuteOfHour, float px) {
        if (b.sparkle == null || l.night < 0.6f || minuteOfHour >= 5) return;
        Random r = new Random((long) (t * 12));
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(0xFFFFFFFF);
        RectF s = b.sparkle;
        for (int i = 0; i < 26; i++) {
            float y = s.top + r.nextFloat() * s.height(), f = (y - s.top) / s.height();
            float half = s.width() * (0.06f + 0.44f * f * f); // within the tower's spreading shape
            float x = s.centerX() + (r.nextFloat() * 2 - 1) * half;
            c.drawCircle(x, y, (0.4f + r.nextFloat() * 0.5f) * px, p);
        }
    }

    /**
     * Birds (2.27, after the video): now and then a loose flock of three to five crosses the sky, gull-shaped dark
     * silhouettes beating their wings out of step; by day and at dusk, not at night nor in rain or snow.
     */
    static void birds(Canvas c, Sky.Layers L, Light l, float t, long seed, float px) {
        if (l.night > 0.75f || L.rain || L.snow || L.cover > 0.9f) return;
        Random r = new Random(seed ^ 0xB1BDL);
        float period = 34 + r.nextInt(26), cross = 16 + r.nextInt(7), phase = r.nextFloat() * period;
        float f = ((t + phase) % period) / cross;
        if (f >= 1f) return;
        boolean right = r.nextBoolean();
        float dir = right ? 1 : -1, s = px * 1.2f;
        int n = 3 + r.nextInt(3);
        float w = L.w, h = L.h, y0 = h * (0.15f + r.nextFloat() * 0.18f);
        float x0 = right ? (-0.15f + 1.3f * f) * w : (1.15f - 1.3f * f) * w;
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
        p.setStrokeWidth(Math.max(1f, 0.6f * s));
        p.setColor(Scene.alpha(Scene.mix(l.front, l.near, 0.2f), 0.85f * (1 - 0.6f * l.night)));
        for (int i = 0; i < n; i++) {
            float bx = x0 - dir * i * (3.2f + (i * 0.7f) % 1.3f) * s;
            float by = y0 + (i % 2 == 0 ? -1 : 1) * ((i + 1) / 2) * 1.6f * s + (float) Math.sin(t * 1.3f + i) * 0.5f * s;
            float flap = (float) Math.sin(t * 7.5f + i * 1.7f), span = (2.0f + (i % 3) * 0.25f) * s;
            Path b = new Path();
            b.moveTo(bx - span, by - flap * span * 0.45f);
            b.quadTo(bx - span * 0.45f, by - span * 0.35f - flap * span * 0.12f, bx, by);
            b.quadTo(bx + span * 0.45f, by - span * 0.35f - flap * span * 0.12f, bx + span, by - flap * span * 0.45f);
            c.drawPath(b, p);
        }
    }

    /**
     * A helicopter (2.27, after the video) passing now and then: seen from the side, nose a little down, its cabin with
     * dark windows, a tail boom ending in a ring, skids below, the light catching its top edge, and its rotor a blur
     * over it; at night a red light blinking on it and a white strobe.
     */
    static void helicopter(Canvas c, Sky.Layers L, Light l, float t, long seed, float px) {
        if (L.cover > 0.92f || L.fog || (L.rain && L.cover > 0.7f)) return;
        Random r = new Random(seed ^ 0x4E11L);
        float period = 95 + r.nextInt(70), cross = 13 + r.nextInt(6), phase = r.nextFloat() * period;
        float f = ((t + phase) % period) / cross;
        if (f >= 1f) return;
        boolean right = r.nextBoolean();
        float dir = right ? 1 : -1, s = px * 1.2f, len = 13 * s;
        float w = L.w, h = L.h;
        float x = right ? (-0.2f + 1.4f * f) * w : (1.2f - 1.4f * f) * w;
        float y = h * (0.27f + r.nextFloat() * 0.12f) + (float) Math.sin(t * 1.7f) * 0.6f * s;
        int body = Scene.mix(l.near, l.lit, 0.3f * (1 - l.night)), dark = Scene.mix(body, 0xFF000000, 0.45f);
        int rim = Scene.mix(l.lit, 0xFFFFFFFF, 0.35f);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        c.save();
        c.translate(x, y);
        c.scale(dir, 1);
        c.rotate(5f); // nose a little down, flying forward
        // the rotor: a thin blur over it, a blade glinting as it turns
        p.setColor(Scene.alpha(body, 0.32f));
        c.drawOval(-0.5f * len, -3.35f * s, 0.62f * len, -2.75f * s, p);
        float blade = (float) Math.sin(t * 37f);
        p.setColor(Scene.alpha(dark, 0.6f));
        c.drawRect(-0.45f * len * Math.abs(blade), -3.15f * s, 0.55f * len * Math.abs(blade), -2.95f * s, p);
        p.setColor(dark);
        c.drawRect(-0.03f * len, -3f * s, 0.05f * len, -2.2f * s, p); // the mast
        // the tail boom and its ring
        Path boom = new Path();
        boom.moveTo(-0.16f * len, -1.6f * s);
        boom.lineTo(-0.6f * len, -1.25f * s);
        boom.lineTo(-0.6f * len, -0.55f * s);
        boom.lineTo(-0.16f * len, 0.4f * s);
        boom.close();
        p.setColor(body);
        c.drawPath(boom, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Math.max(1f, 0.55f * s));
        c.drawCircle(-0.64f * len, -1.05f * s, 1.15f * s, p);
        p.setStyle(Paint.Style.FILL);
        Path fin = new Path();
        fin.moveTo(-0.6f * len, -1.4f * s);
        fin.lineTo(-0.7f * len, -3.2f * s);
        fin.lineTo(-0.62f * len, -3.2f * s);
        fin.lineTo(-0.52f * len, -1.4f * s);
        fin.close();
        c.drawPath(fin, p);
        // the cabin, rounder at the nose
        RectF cab = new RectF(-0.2f * len, -2.4f * s, 0.44f * len, 1.6f * s);
        c.drawRoundRect(cab, 2.2f * s, 2.0f * s, p);
        // its windows, dark, and the windscreen
        p.setColor(Scene.alpha(0xFF000000, 0.5f));
        for (int i = 0; i < 3; i++) {
            float wx = -0.08f * len + i * 0.12f * len;
            c.drawRoundRect(wx, -1.5f * s, wx + 0.085f * len, -0.3f * s, 0.3f * s, 0.3f * s, p);
        }
        Path screen = new Path();
        screen.moveTo(0.3f * len, -1.6f * s);
        screen.lineTo(0.41f * len, -0.9f * s);
        screen.lineTo(0.42f * len, -0.2f * s);
        screen.lineTo(0.3f * len, -0.2f * s);
        screen.close();
        c.drawPath(screen, p);
        // the light catching its top edge
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeWidth(Math.max(1f, 0.45f * s));
        p.setColor(Scene.alpha(rim, 0.55f * (1 - 0.6f * l.night)));
        c.drawLine(-0.12f * len, -2.35f * s, 0.34f * len, -2.2f * s, p);
        // the skids
        p.setColor(dark);
        p.setStrokeWidth(Math.max(1f, 0.4f * s));
        c.drawLine(-0.14f * len, 2.6f * s, 0.34f * len, 2.6f * s, p);
        c.drawLine(-0.02f * len, 1.5f * s, -0.04f * len, 2.6f * s, p);
        c.drawLine(0.24f * len, 1.5f * s, 0.26f * len, 2.6f * s, p);
        p.setStyle(Paint.Style.FILL);
        if (l.night > 0.2f) { // its lights
            float blink = (float) Math.pow(0.5f + 0.5f * Math.sin(t * 5f), 4);
            p.setColor(Scene.alpha(BEACON, l.night * blink));
            c.drawCircle(-0.05f * len, 1.8f * s, 0.6f * s, p);
            float strobe = ((t * 1000) % 1100) < 80 ? 1f : 0f;
            p.setColor(Scene.alpha(0xFFFFFFFF, l.night * strobe));
            c.drawCircle(-0.68f * len, -3.2f * s, 0.6f * s, p);
        }
        c.restore();
    }

    /**
     * A plane crossing high and far away every minute or so (each city at its own times), leaving a contrail that
     * catches the light of the hour: white by day, pink and gold at dusk, faint at night, when its lights blink.
     */
    static void plane(Canvas c, Sky.Layers L, Light l, float t, long seed, float px) {
        px *= 1.2f; // zoomed in (2.09, 2.12)
        if (L.cover > 0.85f) return;
        Random r = new Random(seed ^ 0xA1121EL);
        float period = 46 + r.nextInt(34), cross = 17 + r.nextInt(6), phase = r.nextFloat() * period;
        float f = (float) (((t + phase) % period) / cross);
        if (f >= 1f) return;
        boolean right = r.nextBoolean();
        float w = L.w, h = L.h;
        float y0 = h * (0.1f + r.nextFloat() * 0.14f), y1 = y0 - h * (0.03f + r.nextFloat() * 0.05f);
        float xs = right ? -0.06f * w : 1.06f * w, xe = right ? 1.06f * w : -0.06f * w;
        float x = xs + (xe - xs) * f, y = y0 + (y1 - y0) * f;
        // the contrail: as long as the plane has flown, up to half the sky, thinning away behind it
        float tail = Math.max(0f, f - 0.5f);
        float tx = xs + (xe - xs) * tail, ty = y0 + (y1 - y0) * tail;
        float dusk = Sky.clamp(1f - Math.abs(l.e - 2f) / 9f);
        int trail = Scene.mix(Scene.mix(0xFFFFFFFF, l.cloudUnder, 0.65f * dusk), l.cloudTop, 0.4f * l.night);
        float vis = (0.55f * (1 - l.night) + 0.08f * l.night) * (1f - L.cover * 0.5f);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        if (vis > 0.02f && (x - tx) * (x - tx) + (y - ty) * (y - ty) > 1f) {
            p.setStrokeWidth(2.2f * px);
            p.setShader(new LinearGradient(x, y, tx, ty, Scene.alpha(trail, 0.22f * vis), trail & 0x00FFFFFF, Shader.TileMode.CLAMP));
            c.drawLine(x, y, tx, ty, p);
            p.setStrokeWidth(Math.max(1f, 0.7f * px));
            p.setShader(new LinearGradient(x, y, tx, ty, Scene.alpha(trail, 0.8f * vis), trail & 0x00FFFFFF, Shader.TileMode.CLAMP));
            c.drawLine(x, y, tx, ty, p);
            p.setShader(null);
        }
        p.setStyle(Paint.Style.FILL);
        float dir = right ? 1 : -1;
        if (l.night < 0.6f) { // a tiny glint of a plane by day
            p.setColor(Scene.alpha(Scene.mix(0xFFE8ECF2, l.near, 0.35f), 0.9f * (1 - l.night)));
            c.drawCircle(x, y, 0.75f * px, p);
            p.setStrokeWidth(Math.max(1f, 0.45f * px));
            p.setStyle(Paint.Style.STROKE);
            c.drawLine(x - 0.4f * dir * px, y - 1.1f * px, x - 0.4f * dir * px, y + 1.1f * px, p);
            p.setStyle(Paint.Style.FILL);
        }
        if (l.night > 0.2f) { // its lights: a white strobe, red and green at the wingtips
            float strobe = ((t * 1000) % 1300) < 90 ? 1f : 0f;
            p.setColor(Scene.alpha(0xFFFFFFFF, l.night * strobe));
            c.drawCircle(x, y, 0.9f * px, p);
            p.setColor(Scene.alpha(0xFFFF5040, 0.8f * l.night));
            c.drawCircle(x, y - 0.9f * px, 0.45f * px, p);
            p.setColor(Scene.alpha(0xFF50FF80, 0.8f * l.night));
            c.drawCircle(x, y + 0.9f * px, 0.45f * px, p);
        }
    }
}
