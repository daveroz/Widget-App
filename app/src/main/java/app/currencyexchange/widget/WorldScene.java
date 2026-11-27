package app.currencyexchange.widget;

import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.Shader;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.zip.InflaterInputStream;

/**
 * A world clock's city as it really is (2.31): baked from open map data (Overture Maps: buildings with their heights,
 * water, parks, roads; the CC0 landmarks collection at their real places; the real hills on the horizon; see
 * tools/scenes) into a small file per place (assets/scenes/*.bin), drawn here in the palette's colours for the hour.
 * <p>
 * The camera is fixed per city (high over it, level, the horizon 64% of the way down, as the skies), so a scene is
 * already flat: the hills' lines, the ground's shapes (water, parks, main roads) and then, far to near, blocks and
 * landmark faces. A block is stored once, as its footprint on the ground and its height: the camera being level, every
 * vertical edge stays vertical, so each corner's top is straight above its foot, and the walls (which way each faces,
 * how wide it is) are worked out here. Major cities have their full streets and own view; every other place a
 * low-poly version of its own. The light, weather, sun and moon, stars, clouds, fireworks, Christmas lights, birds and
 * the rest stay {@link SkyCell}'s, round and over this.
 * <p>
 * Faces turned to the sun's side are lit, the others in shade, roofs lighter, all hazed with distance; at night the
 * windows light in grids (a floor every 3.3 m, a bay every 3.2 m; each building its own share), with an orange-pink
 * glow that nearer buildings hide, and the street lamps glow pink along the main roads.
 */
final class WorldScene {
    private WorldScene() {}

    private static AssetManager assets;

    /** Lets scenes be read (the app's assets). */
    static void init(Context ctx) {
        if (assets == null && ctx != null) assets = ctx.getApplicationContext().getAssets();
    }

    private static java.util.Set<String> files;

    /** Whether the place {@code city} in country {@code cc} has its own scene (2.41: the clock city picker lists only these). */
    static synchronized boolean has(Context ctx, String city, String cc) {
        init(ctx);
        if (files == null) {
            files = new java.util.HashSet<>();
            try {
                String[] list = assets == null ? null : assets.list("scenes");
                if (list != null) for (String f : list) if (f.endsWith(".bin")) files.add(f.substring(0, f.length() - 4));
            } catch (java.io.IOException ignored) { }
        }
        String k = slug(city, cc);
        return k != null && files.contains(k);
    }

    private static final float HZ = 0.64f, FN = (float) (0.5 / Math.tan(Math.toRadians(20)));
    private static final int WALL = 0, ROOF = 1, WATER = 2, PARK = 3, ROAD = 4, LM = 5, LM_GLASS = 6, LM_LIGHT = 7,
            LM_ROOF = 9, LM_LATTICE = 10, LM_GIRDER = 11, DECK = 12;

    /** A scene as read: the reference frame (3:1, 280 tall), the camera, the hills, the ground and the items. */
    static final class Data {
        int w, h, look, tier;
        float hc;
        /** The lens: its focal length in cell heights (format 4; the standard 40 degree view before). */
        float fn = FN;
        float[][] hills;
        int[] groundKind;
        float[][] ground;
        /** Far to near: kind (-1 a block), facing (degrees, -1 none), distance (m), height (m), hue, chroma, main. */
        int[] kind;
        float[] facing, dist, height, hue, chroma;
        boolean[] main;
        float[][] pts;
        /** The main roads' centre lines (format 4, 2.32; null before): width (m) and points, for the cars at night. */
        float[] roadW;
        float[][] roads;
        /** Each road point's height (m; a bridge's deck) and mode (0, 1 on a landmark bridge, 2 hidden: a tunnel), format 5. */
        float[][] roadH;
        byte[][] roadM;
        /** The rivers' and canals' centre lines (format 5, 2.43; null before): kind (0 a river, 1 a canal) and points, for the boats. */
        int[] boatKind;
        float[][] boats;
    }

    private static final Map<String, Data> CACHE = new LinkedHashMap<String, Data>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Data> e) { return size() > 10; }
    };
    private static final Data NONE = new Data();

    /** The file name of a place: its name and country (as the tools' slug). */
    static String slug(String city, String cc) {
        if (city == null || cc == null) return null;
        String s = Normalizer.normalize(city.split(",")[0].trim().toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").replaceAll("[^a-z ]", "").replaceAll("\\s+", " ").trim().replace(' ', '-');
        return s + "-" + cc.toLowerCase(Locale.ROOT);
    }

    private static Map<String, String> byName;

    /** A scene's file by the place's name alone (a clock saved without its country): only when the name is unique. */
    private static String byName(String city) {
        if (byName == null) {
            byName = new java.util.HashMap<>();
            try {
                String[] list = assets.list("scenes");
                if (list != null) {
                    for (String f : list) {
                        if (!f.endsWith(".bin")) continue;
                        String k = f.substring(0, Math.max(0, f.length() - 7)); // less "-cc.bin"
                        byName.put(k, byName.containsKey(k) ? "" : f.substring(0, f.length() - 4));
                    }
                }
            } catch (java.io.IOException ignored) { }
        }
        String k = slug(city, "xx");
        String hit = k == null ? null : byName.get(k.substring(0, k.length() - 3));
        return hit == null || hit.isEmpty() ? null : hit;
    }

    /** The listed places that have a scene: slug, country, latitude, longitude (2.40). */
    private static List<Object[]> mapped;

    /**
     * The scene of the nearest mapped place within 30 km (2.40): a clock found online under another spelling of a listed
     * place ("Bengaluru" for the list's "Bangalore") or a suburb of one; in its own country when that's known.
     */
    private static String nearest(String cc, double lat, double lon) {
        if (Double.isNaN(lat) || Double.isNaN(lon)) return null;
        if (mapped == null) {
            mapped = new ArrayList<>();
            java.util.Set<String> have = new java.util.HashSet<>();
            try {
                String[] list = assets.list("scenes");
                if (list != null) for (String f : list) if (f.endsWith(".bin")) have.add(f.substring(0, f.length() - 4));
            } catch (java.io.IOException ignored) { }
            try (java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(
                    assets.open("cities.txt"), java.nio.charset.StandardCharsets.UTF_8))) {
                for (String l; (l = r.readLine()) != null; ) {
                    String[] p = l.split("\\|");
                    if (p.length < 5) continue;
                    String k = slug(p[0], p[1]);
                    if (k != null && have.contains(k)) {
                        mapped.add(new Object[]{k, p[1], Double.parseDouble(p[3]), Double.parseDouble(p[4])});
                    }
                }
            } catch (Exception ignored) { }
        }
        String best = null;
        double bestKm = 30;
        for (Object[] m : mapped) {
            if (cc != null && !cc.equalsIgnoreCase((String) m[1])) continue;
            double la = (Double) m[2], lo = (Double) m[3];
            double dy = (la - lat) * 111.2, dx = (lo - lon) * 111.2 * Math.cos(Math.toRadians(lat));
            double km = Math.sqrt(dx * dx + dy * dy);
            if (km < bestKm) { bestKm = km; best = (String) m[0]; }
        }
        return best;
    }

    /** As below, without the place's position (no nearest-place fallback). */
    static Data load(String city, String cc) { return load(city, cc, Double.NaN, Double.NaN); }

    /** The scene of {@code city} in country {@code cc}, or null when there's none (the drawn scene stays). */
    static synchronized Data load(String city, String cc, double lat, double lon) {
        if (assets == null || city == null) return null;
        String key = cc != null ? slug(city, cc) : byName(city);
        if (key == null) key = nearest(null, lat, lon);
        if (key == null) return null;
        Data d = CACHE.get(key);
        if (d == null) {
            try (InputStream in = new InflaterInputStream(assets.open("scenes/" + key + ".bin"))) {
                d = parse(readAll(in));
            } catch (Exception e) {
                d = NONE;
                String alt = cc != null ? byName(city) : null; // its country as saved may differ from the list's
                if (alt == null) alt = nearest(cc, lat, lon); // another spelling of a listed place, or its suburb
                if (alt != null && !alt.equals(key)) {
                    try (InputStream in = new InflaterInputStream(assets.open("scenes/" + alt + ".bin"))) {
                        d = parse(readAll(in));
                    } catch (Exception ignored) { }
                }
            }
            CACHE.put(key, d);
        }
        return d == NONE ? null : d;
    }

    private static byte[] readAll(InputStream in) throws java.io.IOException {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        for (int n; (n = in.read(buf)) > 0; ) o.write(buf, 0, n);
        return o.toByteArray();
    }

    /** Reads formats 3 to 5 (see tools/scenes/README.md). */
    static Data parse(byte[] b) {
        if (b.length < 14 || b[0] != 'W' || b[1] != 'S' || b[2] != 'C' || b[3] != '3') throw new IllegalArgumentException("not a scene");
        Reader r = new Reader(b, 4);
        Data d = new Data();
        int version = r.u8();
        d.tier = r.u8();
        d.w = r.u16();
        d.h = r.u16();
        d.look = r.u16();
        d.hc = r.u16() / 4f;
        if (version >= 4) d.fn = r.u16() / 10000f; // a wider lens for a skyscraper (2.32)
        int nh = (int) r.uvar();
        d.hills = new float[nh][];
        for (int i = 0; i < nh; i++) d.hills[i] = r.pts();
        int ng = (int) r.uvar();
        d.groundKind = new int[ng];
        d.ground = new float[ng][];
        for (int i = 0; i < ng; i++) {
            d.groundKind[i] = r.u8();
            d.ground[i] = r.pts();
        }
        int n = (int) r.uvar();
        d.kind = new int[n];
        d.facing = new float[n];
        d.dist = new float[n];
        d.height = new float[n];
        d.hue = new float[n];
        d.chroma = new float[n];
        d.main = new boolean[n];
        d.pts = new float[n][];
        for (int i = 0; i < n; i++) {
            int t = r.u8();
            if (t == 0) { // a block
                d.kind[i] = -1;
                d.dist[i] = (float) Math.expm1(r.u8() / 28.0);
                d.height[i] = r.uvar() / 2f;
                d.facing[i] = -1;
            } else {
                d.kind[i] = t - 1;
                int f = r.u8();
                d.facing[i] = f == 255 ? -1 : f / 254f * 360f;
                d.dist[i] = (float) Math.expm1(r.u8() / 28.0);
                d.hue[i] = r.u8() / 255f * 360f;
                d.chroma[i] = r.u8() / 800f;
                d.main[i] = (r.u8() & 1) != 0;
            }
            d.pts[i] = r.pts();
        }
        if (version >= 4 && r.p < b.length) { // the cars' roads (2.32)
            int nt = (int) r.uvar();
            d.roadW = new float[nt];
            d.roads = new float[nt][];
            if (version >= 5) {
                d.roadH = new float[nt][];
                d.roadM = new byte[nt][];
            }
            for (int i = 0; i < nt; i++) {
                d.roadW[i] = r.u8();
                d.roads[i] = r.pts();
                if (version >= 5) {
                    int np = d.roads[i].length / 2;
                    d.roadH[i] = new float[np];
                    d.roadM[i] = new byte[np];
                    for (int j = 0; j < np; j++) d.roadH[i][j] = r.u8() / 2f;
                    for (int j = 0; j < np; j++) d.roadM[i][j] = (byte) r.u8();
                }
            }
        }
        if (version >= 5 && r.p < b.length) { // the boats' lanes (2.43)
            int nb = (int) r.uvar();
            d.boatKind = new int[nb];
            d.boats = new float[nb][];
            for (int i = 0; i < nb; i++) {
                d.boatKind[i] = r.u8();
                d.boats[i] = r.pts();
            }
        }
        return d;
    }

    /** Zigzag variable-length numbers, and points as deltas (the first from the last list's first, half pixels). */
    private static final class Reader {
        final byte[] b;
        int p, lx, ly;
        Reader(byte[] b, int p) { this.b = b; this.p = p; }
        int u8() { return b[p++] & 0xFF; }
        int u16() { int v = (b[p] & 0xFF) | ((b[p + 1] & 0xFF) << 8); p += 2; return v; }
        long raw() {
            long v = 0;
            int s = 0;
            while (true) {
                int x = b[p++] & 0xFF;
                v |= (long) (x & 0x7F) << s;
                if ((x & 0x80) == 0) return v;
                s += 7;
            }
        }
        long var() { long v = raw(); return (v >>> 1) ^ -(v & 1); }
        long uvar() { return var(); }
        float[] pts() {
            int n = (int) uvar();
            float[] out = new float[n * 2];
            int x = lx + (int) var(), y = ly + (int) var();
            lx = x;
            ly = y;
            out[0] = x / 2f;
            out[1] = y / 2f;
            for (int i = 1; i < n; i++) {
                x += (int) var();
                y += (int) var();
                out[2 * i] = x / 2f;
                out[2 * i + 1] = y / 2f;
            }
            return out;
        }
    }

    // ------------------------------------------------------------------ drawing

    // the lights (2.33, after the graded reference): warm amber and gold windows (the brightest two first), warm white
    // lamps, and the bloom round the brightest lights amber rather than 2.31's salmon and pink
    private static final int[] WARM = {0xFFFFF4D2, 0xFFFFDC8A, 0xFFFFB85C, 0xFFFFA552, 0xFFFFC57E}; // the brightest near white-hot
    private static final int LAMP = 0xFFFFE2BC, POOL = 0xFFFFC496, GLOW_WINDOW = 0xFFFFA650, GLOW_LAMP = 0xFFFF9248,
            HALATION = 0xFFFF5A36,
            FLOOD = 0xFFFFA27E;
    /**
     * At night every building is lit from the street (2.32), the hero too, alike: warm at its foot, fading out 60% of the
     * way up but never above FADE_MAX (the street's light doesn't reach a tower's top; a landmark other than the hero by
     * FADE_M, its height unknown here), CITY_UP strong near, less far off. The hero has a touch of moonlight besides.
     */
    private static final float FADE_M = 45f, FADE_MAX = 60f, CITY_UP = 0.3f;
    private static final int MOON = 0xFF8C9CFF;
    /** The hills by full day (2.42, after the pastel references): the nearest, near and far lines. */
    private static final int HILL_DAY_NEAREST = 0xFF7B7FAD, HILL_DAY_NEAR = 0xFF9796C2, HILL_DAY_FAR = 0xFFD1B3CD;
    /** The floodlight on a hero high on its hill (2.40): a cool white, as the Cristo's. */
    private static final int HILL_FLOOD = 0xFFE6EAFF;
    /** Cars' lights on the main roads at night (2.32): headlights coming, tail lights going. */
    private static final int HEAD = 0xFFFFF1D2, TAIL = 0xFFFF3C3C;
    /** Where they drive on the left (the cars' lanes, 2.32). */
    private static final java.util.Set<String> LEFT_HAND = new java.util.HashSet<>(java.util.Arrays.asList(("AG AI AU BB BD BM BN BS BT "
            + "BW CY DM FJ FK GB GD GG GY HK ID IE IM IN JE JM JP KE KI KN KY LC LK LS MO MS MT MU MV MW MY MZ NA NP NR NZ PG PK "
            + "PN SB SC SG SH SR SZ TC TH TL TO TT TV TZ UG VC VG VI WS ZA ZM ZW").split(" ")));

    /**
     * The still picture of {@code city}'s scene ({@code w} x {@code h} pixels) for the light {@code l}, or null when the
     * place has no scene. Its beacons: the hero landmark's top, blinking at night.
     */
    static CityScene.Built build(String city, String cc, double lat, double lon, int w, int h, float px,
                                 CityScene.Light l, long seed) {
        Data d = load(city, cc, lat, lon);
        if (d == null) return null;
        CityScene.Built b = new CityScene.Built();
        b.still = Bitmap.createBitmap(Math.max(2, w), Math.max(2, h), Bitmap.Config.ARGB_8888);
        draw(new Canvas(b.still), d, w, h, l, seed, b, cc != null && LEFT_HAND.contains(cc.toUpperCase(Locale.ROOT)),
                "Paris".equalsIgnoreCase(city) && (cc == null || "FR".equalsIgnoreCase(cc)));
        punch(b.still, PUNCH_CLARITY, PUNCH_VIBRANCE * (1f - 0.7f * smoothF(6f, 16f, l.e))); // by day less (2.39: it warmed the greys to tan)
        return b;
    }

    private static void draw(Canvas c, Data d, int w, int h, CityScene.Light l, long seed, CityScene.Built built, boolean left,
                             boolean sparkles) {
        float k = h / (float) d.h, x0 = (d.w - w / k) / 2f, hz = HZ * h;
        float f = d.fn * d.h; // the focal length in reference pixels (the scene's own lens)
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        Path path = new Path();
        float night = l.night, lights = l.lights;
        int farHaze = Scene.mix(l.far, l.horizon, 0.45f);
        // the hills on the horizon: the far line, then the near one (and the nearest, 2.40)
        float hillDay = smoothF(6f, 16f, l.e) * (l.snow ? 0.5f : 1f);
        for (int i = d.hills.length - 1; i >= 0; i--) {
            float[] line = d.hills[i];
            if (line.length < 4) continue;
            path.reset();
            path.moveTo((line[0] - x0) * k, hz + 4 * k);
            float ridge = hz;
            for (int j = 0; j < line.length; j += 2) {
                float y = Math.min(hz + 4 * k, line[j + 1] * k);
                path.lineTo((line[j] - x0) * k, y);
                ridge = Math.min(ridge, y);
            }
            path.lineTo((line[line.length - 2] - x0) * k, hz + 4 * k);
            path.close();
            // a third, nearest line (2.40): the hill a landmark stands on, a little nearer and deeper
            boolean nearest = d.hills.length > 2 && i == 0, far = i == d.hills.length - 1;
            int col = nearest ? Scene.mix(Scene.mix(l.far, l.front, 0.4f), l.horizon, 0.12f)
                    : Scene.mix(l.far, l.horizon, far ? 0.55f : 0.35f);
            // by day (2.42) the pastel references' far hills: periwinkle near, a pink lavender further off (the
            // illustration's far colour with the butter horizon made a muddy taupe)
            if (hillDay > 0f) col = Scene.mix(col, nearest ? HILL_DAY_NEAREST : far ? HILL_DAY_FAR : HILL_DAY_NEAR, hillDay);
            // and by day never one flat tone: deeper at the ridge, fading into the haze down to the horizon (only by
            // day: with a low sun the warm horizon turned their feet olive)
            if (hillDay > 0.01f && hz + 4 * k - ridge > 2f) {
                p.setShader(new LinearGradient(0, ridge, 0, hz + 4 * k, col, Scene.mix(col, l.horizon, 0.4f * hillDay),
                        Shader.TileMode.CLAMP));
            } else {
                p.setColor(col);
            }
            c.drawPath(path, p);
            p.setShader(null);
        }
        // the ground: hazy far off, darker near
        int landFar = Scene.mix(l.far, l.horizon, 0.3f), landNear = Scene.mix(l.street, l.far, 0.15f);
        p.setShader(new LinearGradient(0, hz, 0, hz + (h - hz) / 2.2f, landFar, landNear, Shader.TileMode.CLAMP));
        c.drawRect(0, hz - 0.5f * k, w, h, p);
        p.setShader(null);
        // the water: the sky in it, deeper (2.33: the graded reference's water is darker than the sky it mirrors)
        int waterFar = Scene.mix(Scene.mix(l.horizon, l.mid, 0.3f), l.near, 0.32f), waterNear = Scene.mix(Scene.mix(l.horizon, l.mid, 0.65f), l.near, 0.6f);
        // by full day (2.40) the pastel references' water: mint far off, a deeper aqua-periwinkle near
        float wDay = smoothF(6f, 16f, l.e) * (l.snow ? 0.4f : 1f);
        if (wDay > 0f) {
            waterFar = Scene.mix(waterFar, 0xFFB6D6CF, 0.6f * wDay);
            waterNear = Scene.mix(waterNear, 0xFF88A8C9, 0.55f * wDay);
        }
        int park = Scene.mix(l.front, l.street, 0.35f), road = Scene.mix(l.street, l.edge, 0.18f);
        int[] order = {PARK, WATER, ROAD};
        // where the cars and boats can show (2.32; day and night since 2.43): the roads (red) and the water (green), less
        // everything later drawn over them, at half size
        boolean moving = d.roads != null || d.boats != null;
        Bitmap maskBmp = moving ? Bitmap.createBitmap(Math.max(2, w / 2), Math.max(2, h / 2), Bitmap.Config.ARGB_8888) : null;
        Canvas mask = maskBmp == null ? null : new Canvas(maskBmp);
        if (mask != null) mask.scale(0.5f, 0.5f);
        Paint mp = new Paint(Paint.ANTI_ALIAS_FLAG);
        mp.setColor(0xFFFF0000);
        Paint wp = new Paint(Paint.ANTI_ALIAS_FLAG);
        wp.setColor(0xFF00FF00);
        // (2.43) how far off whatever stands at each pixel is (half size; the ground: nothing), so a boat is hidden only by
        // what stands nearer than it, never by the bank behind it
        Bitmap depthBmp = d.boats != null ? Bitmap.createBitmap(Math.max(2, w / 2), Math.max(2, h / 2), Bitmap.Config.ARGB_8888) : null;
        Canvas depth = depthBmp == null ? null : new Canvas(depthBmp);
        if (depth != null) depth.scale(0.5f, 0.5f);
        Paint dpaint = new Paint();
        for (int kind : order) {
            for (int i = 0; i < d.ground.length; i++) {
                if (d.groundKind[i] != kind) continue;
                float[] s = d.ground[i];
                float my = 0;
                for (int j = 1; j < s.length; j += 2) my += s[j];
                my /= s.length / 2f;
                float t = clamp((Math.max(my, HZ * d.h) - HZ * d.h) / (0.36f * d.h) * 2.2f);
                int col = kind == WATER ? Scene.mix(waterFar, waterNear, t)
                        : kind == PARK ? Scene.mix(Scene.mix(park, farHaze, 0.5f), park, t)
                        : Scene.mix(Scene.mix(road, farHaze, 0.6f), road, t);
                p.setColor(col);
                c.drawPath(poly(path, s, x0, k), p);
                if (kind == ROAD && mask != null) mask.drawPath(path, mp);
                else if (kind == WATER && mask != null) mask.drawPath(path, wp);
            }
        }
        // what stands on it, far to near: the scene's items, with trees in the parks and lamps along the roads put in
        List<float[]> extras = extras(d, seed, f, lights);
        Bitmap glowBmp = lights > 0.05f ? Bitmap.createBitmap(Math.max(2, w / 2), Math.max(2, h / 2), Bitmap.Config.ARGB_8888) : null;
        Canvas glow = glowBmp == null ? null : new Canvas(glowBmp);
        if (glow != null) glow.scale(0.5f, 0.5f);
        Paint clear = new Paint(Paint.ANTI_ALIAS_FLAG);
        clear.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
        Paint gp = new Paint(Paint.ANTI_ALIAS_FLAG);
        float sun = d.look + (l.sunLeft ? -115f : 115f);
        int litC = l.lit, shadeC = l.shade, roofC = l.snow ? Scene.mix(0xFFF0F3F8, l.horizon, 0.2f + 0.4f * night)
                : Scene.mix(l.near, l.rim, 0.22f);
        int treeC = Scene.mix(l.front, l.near, 0.2f), treeLit = Scene.mix(l.front, l.lit, 0.35f * (1 - 0.6f * night));
        float share = (night > 0.5f ? 0.42f : 0.3f) * lights;
        float bestTop = Float.MAX_VALUE, bestX = 0;
        // the hero's top and foot on screen (for its light at night)
        float heroTop = Float.MAX_VALUE, heroFoot = -Float.MAX_VALUE, heroL = Float.MAX_VALUE, heroR = -Float.MAX_VALUE;
        for (int i = 0; i < d.kind.length; i++) {
            if (d.kind[i] == -1 || !d.main[i]) continue;
            for (int j = 1; j < d.pts[i].length; j += 2) {
                heroTop = Math.min(heroTop, d.pts[i][j] * k);
                heroFoot = Math.max(heroFoot, d.pts[i][j] * k);
            }
        }
        p.setStrokeJoin(Paint.Join.ROUND);
        int e = 0;
        Random rnd = new Random(seed ^ 0x3D5EL);
        for (int i = 0; i <= d.kind.length; i++) {
            float di = i < d.kind.length ? d.dist[i] : -1;
            while (e < extras.size() && extras.get(e)[0] > di) { // a tree or lamp further than this item: first
                float[] x = extras.get(e++);
                float fog = fog(x[0], night);
                float X = (x[2] - x0) * k, Y = x[3] * k;
                if (X < -10 || X > w + 10) continue;
                if (x[1] == 0) {
                    float rr = x[4] * 0.35f * k;
                    if (rr < 0.6f) continue;
                    crown(c, p, path, X, Y - rr, rr, Scene.mix(treeC, farHaze, fog), Scene.mix(treeLit, farHaze, fog), rnd);
                } else {
                    float rad = Math.max(0.35f, Math.min(1.4f, 150f / Math.max(x[0], 1))); // in pixels
                    p.setColor(Scene.alpha(POOL, 0.28f * lights));
                    c.drawOval(X - rad * 2.6f, Y - rad * 0.9f, X + rad * 2.6f, Y + rad * 0.9f, p);
                    p.setColor(Scene.alpha(LAMP, 0.92f * lights));
                    c.drawOval(X - rad * 0.55f, Y - rad * 1.6f, X + rad * 0.55f, Y - rad * 0.5f, p);
                    if (glow != null) {
                        gp.setColor(Scene.alpha(GLOW_LAMP, 0.6f * lights));
                        glow.drawOval(X - rad * 2.2f, Y - rad * 2.6f, X + rad * 2.2f, Y + rad * 0.6f, gp);
                    }
                }
            }
            if (i == d.kind.length) break;
            float fog = d.main[i] ? 0 : fog(d.dist[i], night);
            if (depth != null) dpaint.setColor(depthCode(d.dist[i]));
            if (d.kind[i] == -1) {
                block(c, glow, mask, depth, dpaint, clear, gp, p, path, d, d.pts[i], d.height[i], f, x0, k, hz, sun, fog, l, litC, shadeC, roofC,
                        farHaze, share, lights);
                continue;
            }
            int kind = d.kind[i];
            float[] s = d.pts[i];
            if (kind == DECK) { // a bridge's deck (2.43): the road raised over the water, and its sides in the light
                int col;
                if (d.facing[i] < 0) col = road;
                else {
                    double lit = Math.cos(Math.toRadians(d.facing[i] - sun));
                    col = Scene.mix(lit > 0.1 ? litC : shadeC, l.edge, 0.25f);
                }
                poly(path, s, x0, k);
                p.setColor(Scene.mix(col, farHaze, fog));
                p.setStrokeWidth(0.6f);
                p.setStyle(Paint.Style.FILL_AND_STROKE);
                c.drawPath(path, p);
                p.setStyle(Paint.Style.FILL);
                if (glow != null) glow.drawPath(path, clear);
                if (mask != null) mask.drawPath(path, d.facing[i] < 0 ? mp : clear); // its cars show on it; boats pass under
                if (depth != null) depth.drawPath(path, dpaint);
                continue;
            }
            int base;
            if (kind == LM_ROOF) base = roofC;
            else if (d.facing[i] < 0) base = l.near;
            else {
                double lit = Math.cos(Math.toRadians(d.facing[i] - sun));
                base = lit > 0.1 ? litC : lit < -0.1 ? shadeC : l.near;
            }
            if (kind == LM || kind == LM_ROOF || kind == LM_LATTICE || kind == LM_GIRDER) {
                int t = tint(base, d.hue[i], d.chroma[i]);
                base = Scene.mix(t, base, 0.5f * smoothF(6f, 16f, l.e)); // by day half its own colour (2.39: less orange)
            }
            if (kind == LM_GLASS) base = Scene.mix(base, shadeC, 0.4f);
            int col = Scene.mix(base, farHaze, fog);
            poly(path, s, x0, k);
            if (d.main[i] && kind != LM_GLASS && lights > 0.05f) col = screen(col, MOON, 0.1f * lights); // the hero: a touch of moonlight, to stand out
            p.setColor(col);
            if (d.main[i] && kind != LM_GLASS && lights > 0.05f && heroFoot < hz) {
                // a hero high on its hill (2.40, the Cristo on Corcovado): floodlit whole, a cool white over the dark hill
                p.setColor(screen(col, HILL_FLOOD, 0.6f * lights));
            } else if (d.main[i] && kind != LM_GLASS && lights > 0.05f && heroFoot > heroTop) {
                // the hero at night: lit from the street as every building is (the same strength and reach), warm at its
                // foot and fading out 60% of the way up, never more than FADE_MAX
                float footR = heroFoot / k, z = d.hc * f / Math.max(footR - HZ * d.h, 0.25f);
                float hm = (heroFoot - heroTop) / k * z / f, fadeY = heroFoot - Math.min(0.6f * hm, FADE_MAX) * f / z * k;
                if (heroFoot - fadeY > 1f)
                    p.setShader(new LinearGradient(0, heroFoot, 0, fadeY, screen(col, FLOOD, CITY_UP * lights), col, Shader.TileMode.CLAMP));
            } else if (kind != LM_GLASS && lights > 0.05f) { // the others lit from the street, as every building is
                float up = CITY_UP * lights * (1f - 0.7f * clamp(fog / 0.4f)), z = Math.max(d.dist[i], 20f);
                float footY = (HZ * d.h + d.hc * f / z) * k, fadeY = (HZ * d.h - (Math.min(FADE_M, FADE_MAX) - d.hc) * f / z) * k;
                if (up > 0.01f && footY - fadeY > 1f)
                    p.setShader(new LinearGradient(0, footY, 0, fadeY, screen(col, FLOOD, up), col, Shader.TileMode.CLAMP));
            }
            if (kind == LM_LATTICE) { // ironwork: girders, with the sky and the city showing through
                lattice(c, p, path, s, x0, k, f, d.dist[i]);
                p.setShader(null);
            } else if (kind == LM_GIRDER) { // a single bar of a structure: solid, never thinner than a fine line
                p.setStrokeWidth(0.45f);
                p.setStyle(Paint.Style.FILL_AND_STROKE);
                c.drawPath(path, p);
                p.setStyle(Paint.Style.FILL);
                p.setShader(null);
            } else {
                // a landmark's faces drawn a hair larger, so the seams between its triangles don't show
                p.setStrokeWidth(0.75f);
                p.setStyle(Paint.Style.FILL_AND_STROKE);
                c.drawPath(path, p);
                p.setStyle(Paint.Style.FILL);
                p.setShader(null);
                if (glow != null) glow.drawPath(path, clear);
                if (mask != null) mask.drawPath(path, clear);
            }
            if (depth != null && kind != LM_LATTICE) depth.drawPath(path, dpaint);
            if (d.main[i]) { // the hero's extent across (for the Eiffel Tower's sparkle)
                for (int j = 0; j < s.length; j += 2) {
                    heroL = Math.min(heroL, (s[j] - x0) * k);
                    heroR = Math.max(heroR, (s[j] - x0) * k);
                }
            }
            if (d.main[i]) { // the hero's top: its beacon
                for (int j = 1; j < s.length; j += 2) {
                    if (s[j] < bestTop) { bestTop = s[j]; bestX = s[j - 1]; }
                }
            }
            if (kind == LM_GLASS && lights > 0.05f) glassWindows(c, glow, gp, p, s, x0, k, share, lights, fog, i);
        }
        // the glow: blurred, laid over by screen (the bloom)
        // the haze on the far distance (2.33): a thin veil of the horizon's light along it, about 8% at its densest
        float hzY = HZ * h;
        int haze = Scene.mix(l.horizon, l.far, 0.4f);
        p.setShader(new LinearGradient(0, hzY - 0.035f * h, 0, hzY + 0.07f * h, new int[]{haze & 0x00FFFFFF,
                Scene.alpha(haze, 0.08f), haze & 0x00FFFFFF}, new float[]{0f, 0.35f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(0, hzY - 0.035f * h, w, hzY + 0.07f * h, p);
        p.setShader(null);
        if (glowBmp != null) {
            Bitmap small = Bitmap.createScaledBitmap(glowBmp, Math.max(2, glowBmp.getWidth() / 2), Math.max(2, glowBmp.getHeight() / 2), true);
            blur(small, 1);
            // the halation (2.33): the same lights spread wider and redder, faintly, as film does round tiny highlights
            // (the city's lights only: never the sun or the moon, which are the sky's)
            Bitmap wide = Bitmap.createScaledBitmap(small, Math.max(2, small.getWidth() / 2), Math.max(2, small.getHeight() / 2), true);
            blur(wide, 2);
            Paint hp = new Paint(Paint.FILTER_BITMAP_FLAG);
            hp.setColorFilter(new android.graphics.PorterDuffColorFilter(HALATION, PorterDuff.Mode.SRC_IN));
            hp.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SCREEN));
            hp.setAlpha(Math.round(255 * 0.45f * lights));
            c.drawBitmap(wide, new Rect(0, 0, wide.getWidth(), wide.getHeight()), new Rect(0, 0, w, h), hp);
            wide.recycle();
            // the bloom round the brightest lights
            Paint sp = new Paint(Paint.FILTER_BITMAP_FLAG);
            sp.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SCREEN));
            sp.setAlpha(Math.round(255 * Math.min(1f, 0.85f + 0.15f * night)));
            c.drawBitmap(small, new Rect(0, 0, small.getWidth(), small.getHeight()), new Rect(0, 0, w, h), sp);
        }
        if (bestTop < HZ * d.h && night > 0.3f) built.beacons = new float[]{(bestX - x0) * k, bestTop * k + 1.5f * k};
        if (sparkles && heroR > heroL && heroFoot > heroTop) // the Eiffel Tower sparkles five minutes every hour after dark
            built.sparkle = new android.graphics.RectF(heroL, heroTop, heroR, heroFoot);
        if (maskBmp != null) {
            int mw = maskBmp.getWidth(), mh = maskBmp.getHeight();
            int[] mpx = new int[mw * mh];
            maskBmp.getPixels(mpx, 0, mw, 0, 0, mw, mh);
            maskBmp.recycle();
            if (d.roads != null) built.traffic = Traffic.make(d, channel(mpx, 16), mw, mh, x0, k, f, l, seed, left);
            if (d.boats != null) {
                built.boats = Boats.make(d, channel(mpx, 8), mw, mh, x0, k, f, l, farHaze, seed, left);
                if (depthBmp != null && depthBmp.getWidth() == mw && depthBmp.getHeight() == mh) {
                    int[] dpx = new int[mw * mh];
                    depthBmp.getPixels(dpx, 0, mw, 0, 0, mw, mh);
                    float[] dm = new float[dpx.length];
                    for (int i = 0; i < dm.length; i++) dm[i] = (dpx[i] >>> 24) < 128 ? Float.MAX_VALUE : (float) Math.expm1((dpx[i] & 0xFFFFFF) / 100000.0);
                    built.boats.depth = dm;
                }
            }
            if (depthBmp != null) depthBmp.recycle();
        }
    }

    private static float clamp(float v) { return Math.max(0f, Math.min(1f, v)); }

    private static float smoothF(float a, float b, float x) {
        float t = clamp((x - a) / (b - a));
        return t * t * (3 - 2 * t);
    }

    /** The buildings' materials by day (2.37): their colour, its lightness ignored (each wall keeps its own). */
    // 2.40: in the pastel references' colours: concrete a lilac grey, stone a peach cream, sandstone a pale butter, glass
    // an aqua, brick a dusty rose, dark glass a periwinkle (2.39's: C6C9CE DEDCD7 D2CABA 8EA8C8 AE958A 6F86A6)
    private static final int MAT_CONCRETE = 0xFFC3C2D1, MAT_STONE = 0xFFEFD9CC, MAT_SAND = 0xFFE2DBC1,
            MAT_GLASS = 0xFF8AAEBD, MAT_BRICK = 0xFFC9A3A7, MAT_DARK_GLASS = 0xFF717EAB;

    /**
     * {@code col} taking the hue and colour of the material {@code mat} at its own lightness, {@code k} of the way,
     * then {@code jit} lighter or darker (a share).
     */
    private static int material(int col, int mat, float k, float jit) {
        float r = (col >> 16) & 255, g = (col >> 8) & 255, b = col & 255;
        float y = 0.299f * r + 0.587f * g + 0.114f * b;
        float mr = (mat >> 16) & 255, mg = (mat >> 8) & 255, mb = mat & 255;
        float my = Math.max(1f, 0.299f * mr + 0.587f * mg + 0.114f * mb), sc = y / my;
        r += (mr * sc - r) * k;
        g += (mg * sc - g) * k;
        b += (mb * sc - b) * k;
        float f = 1f + jit;
        return 0xFF000000 | (clamp255(r * f) << 16) | (clamp255(g * f) << 8) | clamp255(b * f);
    }

    /**
     * A face of ironwork (2.32; the Eiffel Tower's lattice, which its model gives as a see-through texture): a faint fill
     * for its mass, a cross-hatch of bars about every 8 m (one screen grid, so neighbouring faces line up) and rails
     * along its edges, sized by its distance; what's behind shows through.
     */
    private static void lattice(Canvas c, Paint p, Path path, float[] s, float x0, float k, float f, float dist) {
        float minx = Float.MAX_VALUE, maxx = -Float.MAX_VALUE, miny = Float.MAX_VALUE, maxy = -Float.MAX_VALUE;
        for (int j = 0; j < s.length; j += 2) {
            float x = (s[j] - x0) * k, y = s[j + 1] * k;
            minx = Math.min(minx, x); maxx = Math.max(maxx, x);
            miny = Math.min(miny, y); maxy = Math.max(maxy, y);
        }
        float mpx = f * k / Math.max(dist, 30f); // pixels a metre, there
        float cell = Math.max(3f, 8f * mpx), bar = Math.max(0.55f, 0.8f * mpx);
        int a0 = p.getAlpha();
        p.setStyle(Paint.Style.FILL);
        p.setAlpha(Math.round(a0 * 0.3f));
        c.drawPath(path, p);
        p.setAlpha(a0);
        c.save();
        c.clipPath(path);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(bar);
        float hgt = maxy - miny;
        for (float o = (float) Math.floor((minx - hgt) / cell) * cell; o <= maxx; o += cell) {
            c.drawLine(o, maxy, o + hgt, miny, p);
            c.drawLine(o + hgt, maxy, o, miny, p);
        }
        c.restore();
        p.setStrokeWidth(Math.max(0.7f, 1.2f * mpx));
        c.drawPath(path, p);
        p.setStyle(Paint.Style.FILL);
    }

    /** {@code light} laid over {@code col} by screen at {@code a}, as light falls on a thing (it never muddies it). */
    private static int screen(int col, int light, float a) {
        int r = 255 - (255 - ((col >> 16) & 255)) * (255 - Math.round(((light >> 16) & 255) * a)) / 255;
        int g = 255 - (255 - ((col >> 8) & 255)) * (255 - Math.round(((light >> 8) & 255) * a)) / 255;
        int b = 255 - (255 - (col & 255)) * (255 - Math.round((light & 255) * a)) / 255;
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    /** How far the haze has taken a thing {@code dist} metres off (less at night). */
    private static float fog(float dist, float night) {
        return (float) ((1 - Math.exp(-dist / 4200.0)) * (0.62 - 0.25 * night));
    }

    private static Path poly(Path path, float[] s, float x0, float k) {
        path.reset();
        path.moveTo((s[0] - x0) * k, s[1] * k);
        for (int j = 2; j < s.length; j += 2) path.lineTo((s[j] - x0) * k, s[j + 1] * k);
        path.close();
        return path;
    }

    /** A landmark material's colour: a hint of its own hue (a gold, a red steel), staying in the palette. */
    private static int tint(int base, float hue, float chroma) {
        if (chroma < 0.12f) return base;
        float[] b = Palette.lch(base);
        float kk = Math.min(1f, (chroma - 0.12f) / 0.1f) * 0.3f;
        float dh = ((hue - b[2] + 540f) % 360f) - 180f;
        return Palette.fromLch(b[0] * (1 + 0.08f * kk), b[1] + (chroma * 0.7f - b[1]) * kk, b[2] + dh * kk);
    }

    /**
     * A block: its walls rebuilt from its footprint on screen and its height (the camera level: each corner's top
     * straight above its foot), the ones facing the camera drawn far to near, lit or in shade by which way they face,
     * a rim of light along a tall one's top, its windows at night; then its roof if it's lower than the camera.
     */
    private static void block(Canvas c, Canvas glow, Canvas mask, Canvas depth, Paint dpaint, Paint clear, Paint gp, Paint p, Path path, Data d, float[] s, float hm,
                              float f, float x0, float k, float hzScreen, float sun, float fog, CityScene.Light l, int litC,
                              int shadeC, int roofC, int farHaze, float share, float lights) {
        int n = s.length / 2;
        if (n < 3) return;
        float hzr = HZ * d.h, hc = d.hc, W2 = d.w;
        float[] X = new float[n], Z = new float[n], top = new float[n];
        float area = 0;
        for (int i = 0; i < n; i++) {
            float dy = Math.max(s[2 * i + 1] - hzr, 0.25f);
            Z[i] = hc * f / dy;
            X[i] = (s[2 * i] - W2 / 2f) * Z[i] / f;
            top[i] = hzr - (hm - hc) * dy / hc;
        }
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            area += X[i] * Z[j] - X[j] * Z[i];
        }
        // the walls facing the camera, far to near
        int cnt = 0;
        int[] wi = new int[n];
        float[] wd = new float[n], wf = new float[n];
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            float ex = X[j] - X[i], ez = Z[j] - Z[i];
            float nx = area > 0 ? ez : -ez, nz = area > 0 ? -ex : ex;
            float mx = (X[i] + X[j]) / 2, mz = (Z[i] + Z[j]) / 2;
            if (nx * -mx + nz * -mz <= 0) continue;
            wi[cnt] = i;
            wd[cnt] = (float) Math.hypot(mx, mz);
            wf[cnt] = (float) ((d.look + Math.toDegrees(Math.atan2(nx, nz))) % 360 + 360) % 360;
            cnt++;
        }
        for (int a = 1; a < cnt; a++) { // far to near
            for (int b = a; b > 0 && wd[b] > wd[b - 1]; b--) {
                float t = wd[b]; wd[b] = wd[b - 1]; wd[b - 1] = t;
                t = wf[b]; wf[b] = wf[b - 1]; wf[b - 1] = t;
                int ti = wi[b]; wi[b] = wi[b - 1]; wi[b - 1] = ti;
            }
        }
        // by day each building its own material (2.37): concrete, pale stone, sandstone, glass, brick or dark glass, as
        // its footprint picks it (the same every day), and a little lighter or darker, so the city isn't one grey
        float dayF = smoothF(6f, 16f, l.e) * (l.snow ? 0.5f : 1f);
        int mat = 0;
        float jit = 0;
        if (dayF > 0.01f) {
            int hsh = (int) (s[0] * 73856093) ^ (int) (s[1] * 19349663) ^ n * 83492791;
            int pick = Math.floorMod(hsh, 100);
            mat = pick < 34 ? MAT_CONCRETE : pick < 54 ? MAT_STONE : pick < 64 ? MAT_SAND : pick < 87 ? MAT_GLASS : pick < 94 ? MAT_BRICK : MAT_DARK_GLASS;
            jit = (Math.floorMod(hsh >> 8, 1000) / 1000f * 2f - 1f) * 0.07f * dayF;
        }
        for (int a = 0; a < cnt; a++) {
            int i = wi[a], j = (i + 1) % n;
            double lit = Math.cos(Math.toRadians(wf[a] - sun));
            int base = lit > 0.1 ? litC : lit < -0.1 ? shadeC : l.near;
            if (dayF > 0.01f) base = material(base, mat, 0.32f * dayF, jit);
            int col = Scene.mix(base, farHaze, fog);
            float ax = (s[2 * i] - x0) * k, ay = s[2 * i + 1] * k, bx = (s[2 * j] - x0) * k, by = s[2 * j + 1] * k;
            float atx = ax, aty = top[i] * k, btx = bx, bty = top[j] * k;
            path.reset();
            path.moveTo(ax, ay);
            path.lineTo(bx, by);
            path.lineTo(btx, bty);
            path.lineTo(atx, aty);
            path.close();
            p.setColor(col);
            float up = CITY_UP * lights * (1f - 0.7f * clamp(fog / 0.4f));
            if (up > 0.01f) { // lit from the street at night: warm at its foot, fading out 60% of the way up
                float footY = (ay + by) / 2, dyA = Math.max(footY / k - hzr, 0.25f);
                float fadeY = (hzr - (Math.min(FADE_MAX, Math.max(10f, 0.6f * hm)) - hc) * dyA / hc) * k;
                if (footY - fadeY > 1f) p.setShader(new LinearGradient(0, footY, 0, fadeY, screen(col, FLOOD, up), col, Shader.TileMode.CLAMP));
            }
            c.drawPath(path, p);
            p.setShader(null);
            if (glow != null) glow.drawPath(path, clear);
            if (mask != null) mask.drawPath(path, clear);
            if (depth != null) depth.drawPath(path, dpaint);
            if (Math.abs(bx - ax) > 1.5f && Math.abs(ay - aty) > 8f) { // the rim along a tall one's top
                p.setColor(Scene.mix(col, l.rim, 0.55f * (1 - fog)));
                p.setStrokeWidth(1f);
                c.drawLine(atx, aty, btx, bty, p);
            }
            if (lights > 0.05f) {
                float wm = (float) Math.hypot(X[j] - X[i], Z[j] - Z[i]);
                windows(c, glow, gp, p, path, ax, ay, bx, by, btx, bty, atx, aty, wm, hm, share, lights, fog, k,
                        (int) (s[2 * i] * 7 + s[2 * i + 1] * 13 + s[2 * j] * 3));
            }
        }
        if (hm < hc - 1) { // the roof, seen from above
            path.reset();
            path.moveTo((s[0] - x0) * k, top[0] * k);
            for (int i = 1; i < n; i++) path.lineTo((s[2 * i] - x0) * k, top[i] * k);
            path.close();
            p.setColor(Scene.mix(dayF > 0.01f ? material(roofC, mat, 0.3f * dayF, jit) : roofC, farHaze, fog));
            c.drawPath(path, p);
            if (glow != null) glow.drawPath(path, clear);
            if (mask != null) mask.drawPath(path, clear);
            if (depth != null) depth.drawPath(path, dpaint);
        }
    }

    /**
     * A wall's windows at night: a grid (a floor every 3.3 m, a bay every 3.2 m; merged where too fine to see), each lit
     * or not the same every night, the building its own share; the ground floor of a tall one left dark. Each lit one a
     * warm core and, on the glow layer, an orange-pink halo.
     */
    private static void windows(Canvas c, Canvas glow, Paint gp, Paint p, Path path, float ax, float ay, float bx,
                                float by, float btx, float bty, float atx, float aty, float wm, float hm, float share,
                                float lights, float fog, float k, int seed) {
        int floors = Math.max(1, (int) (hm / 3.3f)), bays = Math.max(1, (int) (wm / 3.2f));
        float hpx = (float) Math.hypot(atx - ax, aty - ay), wpx = (float) Math.hypot(bx - ax, by - ay);
        int merged = 1;
        while (floors > 1 && hpx / floors < 1.6f) { floors = (floors + 1) / 2; merged *= 2; } // in pixels
        while (bays > 1 && wpx / bays < 1.8f) { bays = (bays + 1) / 2; merged *= 2; }
        if (hpx < 1f || wpx < 0.8f) return;
        Random rw = new Random(seed);
        float[] busyOf = {0.15f, 0.35f, 0.6f, 0.85f, 1f, 1f};
        float busy = busyOf[rw.nextInt(busyOf.length)];
        float fshare = share * busy * (merged == 1 ? 1f : merged <= 4 ? 0.7f : 0.45f);
        float span = merged == 1 ? 0.56f : 0.34f;
        float aCore = Math.min(1f, lights * 1.25f) * (1 - 0.5f * fog); // 2.33: brighter, as the reference's lights
        int drawn = 0;
        for (int fl = 0; fl < floors && drawn < 48; fl++) {
            if (hm > 8 && fl == 0 && floors > 2) continue;
            for (int b = 0; b < bays && drawn < 48; b++) {
                if (rw.nextFloat() > fshare) continue;
                float cu = b + 0.5f + (merged > 1 ? (rw.nextFloat() - 0.5f) * 0.4f : 0), cv = fl + 0.52f;
                float u0 = (cu - span / 2) / bays, u1 = (cu + span / 2) / bays, v0 = (cv - 0.22f) / floors, v1 = (cv + 0.22f) / floors;
                path.reset();
                float[] q = new float[8];
                float[][] uv = {{u0, v0}, {u1, v0}, {u1, v1}, {u0, v1}};
                for (int m = 0; m < 4; m++) {
                    float u = uv[m][0], v = uv[m][1];
                    float botx = ax + (bx - ax) * u, boty = ay + (by - ay) * u;
                    float topx = atx + (btx - atx) * u, topy = aty + (bty - aty) * u;
                    q[2 * m] = botx + (topx - botx) * v;
                    q[2 * m + 1] = boty + (topy - boty) * v;
                    if (m == 0) path.moveTo(q[0], q[1]); else path.lineTo(q[2 * m], q[2 * m + 1]);
                }
                path.close();
                int ci = rw.nextInt(WARM.length);
                p.setColor(Scene.alpha(WARM[ci], aCore));
                c.drawPath(path, p);
                if (ci == 0) { // the brightest: a white-hot point at its heart (2.33), as the reference's lights pop
                    float cx = (q[0] + q[2] + q[4] + q[6]) / 4, cy = (q[1] + q[3] + q[5] + q[7]) / 4;
                    float cr = Math.max(0.55f, Math.min(1.4f, (float) Math.hypot(q[2] - q[0], q[3] - q[1]) * 0.45f));
                    p.setColor(Scene.alpha(0xFFFFFCF2, aCore));
                    c.drawCircle(cx, cy, cr, p);
                }
                if (glow != null && ci < 2) { // the bloom: only round the brightest windows (2.33)
                    float gx = (q[0] + q[2] + q[4] + q[6]) / 4, gy = (q[1] + q[3] + q[5] + q[7]) / 4;
                    float gr = Math.max(1f, Math.min(5f, (float) Math.hypot(q[2] - q[0], q[3] - q[1]) * 1.2f));
                    gp.setColor(Scene.alpha(GLOW_WINDOW, 0.5f * lights));
                    glow.drawOval(gx - gr, gy - gr * 0.8f, gx + gr, gy + gr * 0.8f, gp);
                }
                drawn++;
            }
        }
    }

    /** A landmark's glass at night: windows in a grid on it, not one glowing slab. */
    private static void glassWindows(Canvas c, Canvas glow, Paint gp, Paint p, float[] s, float x0, float k, float share,
                                     float lights, float fog, int idx) {
        float minx = Float.MAX_VALUE, maxx = -Float.MAX_VALUE, miny = Float.MAX_VALUE, maxy = -Float.MAX_VALUE;
        for (int j = 0; j < s.length; j += 2) {
            float x = (s[j] - x0) * k, y = s[j + 1] * k;
            minx = Math.min(minx, x); maxx = Math.max(maxx, x); miny = Math.min(miny, y); maxy = Math.max(maxy, y);
        }
        if (maxx - minx < 1.5f || maxy - miny < 1.5f) return;
        Random rw = new Random(idx * 31L + (long) (s[0] * 7));
        float sx = 3.0f, sy = 3.6f; // in pixels
        for (float y = (float) Math.floor(miny / sy) * sy; y < maxy; y += sy) {
            for (float x = (float) Math.floor(minx / sx) * sx; x < maxx; x += sx) {
                float cx = x + sx / 2, cy = y + sy / 2;
                if (!inside(s, (cx / k) + x0, cy / k) || rw.nextFloat() > share * 0.8f) continue;
                int gi = rw.nextInt(WARM.length);
                p.setColor(Scene.alpha(WARM[gi], 0.9f * lights * (1 - 0.6f * fog)));
                c.drawRect(cx - 0.8f, cy - 0.8f, cx + 0.8f, cy + 0.8f, p);
                if (glow != null && gi < 2) {
                    gp.setColor(Scene.alpha(GLOW_WINDOW, 0.32f * lights));
                    glow.drawOval(cx - 2f, cy - 1.6f, cx + 2f, cy + 1.6f, gp);
                }
            }
        }
    }

    /** Whether reference point (x, y) is inside polygon s. */
    private static boolean inside(float[] s, float x, float y) {
        boolean in = false;
        int n = s.length / 2;
        for (int i = 0, j = n - 1; i < n; j = i++) {
            float xi = s[2 * i], yi = s[2 * i + 1], xj = s[2 * j], yj = s[2 * j + 1];
            if ((yi > y) != (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi + 1e-6f) + xi) in = !in;
        }
        return in;
    }

    /**
     * Trees scattered in the parks and street lamps along the main roads (at night), in reference pixels: {distance, 0
     * tree / 1 lamp, x, y, size}, far to near. Where each stands on the ground gives how far it is.
     */
    private static List<float[]> extras(Data d, long seed, float f, float lights) {
        List<float[]> out = new ArrayList<>();
        Random r = new Random(seed ^ 0x7EE5L);
        float hzr = HZ * d.h;
        for (int i = 0; i < d.ground.length; i++) {
            float[] s = d.ground[i];
            if (d.groundKind[i] == PARK) {
                float minx = Float.MAX_VALUE, maxx = -Float.MAX_VALUE, miny = Float.MAX_VALUE, maxy = -Float.MAX_VALUE, area = 0;
                int n = s.length / 2;
                for (int j = 0; j < n; j++) {
                    minx = Math.min(minx, s[2 * j]); maxx = Math.max(maxx, s[2 * j]);
                    miny = Math.min(miny, s[2 * j + 1]); maxy = Math.max(maxy, s[2 * j + 1]);
                    int m = (j + 1) % n;
                    area += s[2 * j] * s[2 * m + 1] - s[2 * m] * s[2 * j + 1];
                }
                int count = (int) Math.min(60, Math.abs(area) / 2 / 14);
                for (int t = 0; t < count; t++) {
                    float x = minx + r.nextFloat() * (maxx - minx), y = miny + r.nextFloat() * (maxy - miny);
                    if (y <= hzr + 1 || !inside(s, x, y)) continue;
                    float z = d.hc * f / (y - hzr);
                    out.add(new float[]{z, 0, x, y, Math.max(0.5f, 9f / z * f)});
                }
            } else if (d.groundKind[i] == ROAD && lights > 0.05f) {
                int n = s.length / 2;
                float carry = 0;
                for (int j = 0; j < n; j++) {
                    int m = (j + 1) % n;
                    float ax = s[2 * j], ay = s[2 * j + 1], bx = s[2 * m], by = s[2 * m + 1];
                    float len = (float) Math.hypot(bx - ax, by - ay);
                    if (len < 0.01f) continue;
                    float t = carry;
                    while (t < len) {
                        float x = ax + (bx - ax) * t / len, y = ay + (by - ay) * t / len;
                        if (y <= hzr + 0.5f) break;
                        float z = d.hc * f / (y - hzr);
                        if (z < 3000 && x >= 0 && x <= d.w) out.add(new float[]{z * (float) Math.sqrt(1 + Math.pow((x - d.w / 2f) / f, 2)), 1, x, y, 0});
                        t += Math.max(1.5f, 70f / z * f); // a lamp every 70 m or so
                    }
                    carry = t - len;
                }
            }
        }
        streetTrees(d, f, r, out);
        out.sort((a, b) -> Float.compare(b[0], a[0]));
        return out;
    }

    /**
     * Trees along the main roads' pavements (2.43): both sides, 2.5 m out from the kerb, about every 14 m with gaps
     * here and there, up to 1.2 km off (none along motorways, none where the pavement crosses water); a little smaller
     * than the parks' trees. Each as an extra: distance, 0 (a tree), x, y, size, drawn in depth with the buildings.
     */
    private static void streetTrees(Data d, float f, Random r, List<float[]> out) {
        if (d.roads == null) return;
        List<float[]> lines = new ArrayList<>();
        List<Float> lanes = new ArrayList<>();
        for (int i = 0; i < d.roads.length; i++) {
            if (d.roadW[i] >= 20f) continue; // a motorway: no pavement
            boolean raised = false; // a bridge or a tunnel: no trees (2.43)
            if (d.roadH != null && d.roadH[i] != null) for (float hh : d.roadH[i]) raised |= hh > 0.05f;
            if (d.roadM != null && d.roadM[i] != null) for (byte mm : d.roadM[i]) raised |= mm != 0;
            if (raised) continue;
            lines.add(d.roads[i]);
            lanes.add(d.roadW[i] / 2f + 2.5f);
        }
        float[] lane = new float[lanes.size()];
        for (int i = 0; i < lane.length; i++) lane[i] = lanes.get(i);
        List<float[]> runs = runs(d, lines.toArray(new float[0][]), null, null, lane, null, 0f, 1f, f, false);
        float[] o = new float[12];
        int made = 0;
        for (float[] q : runs) {
            float L = runLength(q);
            for (float at = r.nextFloat() * 14f; at < L && made < 2500; at += 11f + r.nextFloat() * 6f) {
                if (r.nextFloat() < 0.22f) continue; // a gap: a driveway, a crossing, a missing tree
                place(q, at, o);
                float x = o[0], y = o[1], z = o[2];
                if (z < 30f || z > 1200f || x < -10f || x > d.w + 10f || inWater(d, x, y)) continue;
                out.add(new float[]{z * (float) Math.sqrt(1 + Math.pow((x - d.w / 2f) / f, 2)), 0, x, y,
                        Math.max(0.4f, (6f + r.nextFloat() * 2f) / z * f)});
                made++;
            }
        }
    }

    private static boolean inWater(Data d, float x, float y) {
        for (int i = 0; i < d.ground.length; i++) if (d.groundKind[i] == WATER && inside(d.ground[i], x, y)) return true;
        return false;
    }

    /** A tree's crown as cut from paper (the video's), its side to the light lighter. */
    private static void crown(Canvas c, Paint p, Path path, float x, float y, float r, int col, int lit, Random rnd) {
        path.reset();
        for (int i = 0; i < 9; i++) {
            double a = (i + rnd.nextFloat() * 0.4f) / 9 * Math.PI * 2;
            float kk = r * (i % 2 == 0 ? 1 : 0.78f);
            float px = x + (float) Math.cos(a) * kk * 1.1f, py = y + (float) Math.sin(a) * kk * 0.9f;
            if (i == 0) path.moveTo(px, py); else path.lineTo(px, py);
        }
        path.close();
        p.setColor(col);
        c.drawPath(path, p);
        p.setColor(lit);
        c.drawOval(x - r * 0.9f, y - r * 0.85f, x + r * 0.35f, y + r * 0.1f, p);
    }

    /** A box blur, {@code passes} times, over a small picture (the glow, at a quarter of the cell's size). */
    /** The punch pass's strengths (2.33): local contrast (clarity) and vibrance, matched to the graded reference. */
    static final float PUNCH_CLARITY = 0f, PUNCH_VIBRANCE = 0.3f; // no local contrast: at a widget's size it read as processed

    /**
     * The punch pass (2.33, after the graded reference): local contrast on the lightness only, each pixel pushed away
     * from its surroundings (a 2 px blur at full size, weighted by what's drawn, so the see-through sky never counts as
     * black and no edge rings) by {@code clarity}, the push softly limited on strong edges (crisper texture and windows,
     * no halos or bands); then vibrance, the duller colours made richer by up to {@code vibrance}, the rich ones left
     * nearly alone. On a picture with see-through parts (the city over the sky), only what's drawn changes.
     */
    static void punch(Bitmap b, float clarity, float vibrance) {
        int w = b.getWidth(), h = b.getHeight();
        if (w < 8 || h < 8) return;
        int n = w * h;
        int[] px = new int[n];
        b.getPixels(px, 0, w, 0, 0, w, h);
        float[] ya = null, aa = null;
        if (clarity > 0f) {
            ya = new float[n];
            aa = new float[n];
            for (int i = 0; i < n; i++) {
                int c = px[i];
                float al = (c >>> 24) / 255f;
                ya[i] = al * (0.299f * ((c >> 16) & 255) + 0.587f * ((c >> 8) & 255) + 0.114f * (c & 255));
                aa[i] = al;
            }
            float[] tmp = new float[n];
            for (int pass = 0; pass < 2; pass++) { // two box passes of radius 2: close to a gaussian
                boxBlur(ya, tmp, w, h, 2);
                boxBlur(aa, tmp, w, h, 2);
            }
        }
        for (int i = 0; i < n; i++) {
            int c = px[i], a = c >>> 24;
            if (a == 0) continue;
            float r = (c >> 16) & 255, g = (c >> 8) & 255, bb = c & 255;
            if (ya != null && aa[i] > 0.02f) {
                float y = 0.299f * r + 0.587f * g + 0.114f * bb, around = ya[i] / aa[i];
                float d = y - around;
                d = d / (1f + Math.abs(d) / 18f); // strong edges are pushed little: no ringing
                float k = clarity * aa[i] * aa[i]; // and nothing at the edge of what's drawn
                r += k * d;
                g += k * d;
                bb += k * d;
            }
            if (vibrance > 0f) {
                float mx = Math.max(r, Math.max(g, bb)), mn = Math.min(r, Math.min(g, bb));
                float sat = mx <= 0 ? 0 : (mx - mn) / Math.max(1f, mx), avg = (r + g + bb) / 3f;
                float f = 1f + vibrance * (1f - Math.min(1f, sat));
                r = avg + (r - avg) * f;
                g = avg + (g - avg) * f;
                bb = avg + (bb - avg) * f;
            }
            px[i] = (a << 24) | (clamp255(r) << 16) | (clamp255(g) << 8) | clamp255(bb);
        }
        b.setPixels(px, 0, w, 0, 0, w, h);
    }

    /** A box blur of radius {@code r} across then down, in place ({@code tmp} as large), its edges clamped. */
    private static void boxBlur(float[] v, float[] tmp, int w, int h, int r) {
        float norm = 1f / (2 * r + 1);
        for (int y = 0; y < h; y++) {
            int o = y * w;
            float sum = 0;
            for (int k = -r; k <= r; k++) sum += v[o + Math.min(w - 1, Math.max(0, k))];
            for (int x = 0; x < w; x++) {
                tmp[o + x] = sum * norm;
                sum += v[o + Math.min(w - 1, x + r + 1)] - v[o + Math.max(0, x - r)];
            }
        }
        for (int x = 0; x < w; x++) {
            float sum = 0;
            for (int k = -r; k <= r; k++) sum += tmp[Math.min(h - 1, Math.max(0, k)) * w + x];
            for (int y = 0; y < h; y++) {
                v[y * w + x] = sum * norm;
                sum += tmp[Math.min(h - 1, y + r + 1) * w + x] - tmp[Math.max(0, y - r) * w + x];
            }
        }
    }

    private static int clamp255(float v) { return v <= 0 ? 0 : v >= 255 ? 255 : Math.round(v); }

    private static void blur(Bitmap b, int passes) {
        int w = b.getWidth(), h = b.getHeight();
        int[] px = new int[w * h], tmp = new int[w * h];
        b.getPixels(px, 0, w, 0, 0, w, h);
        for (int pass = 0; pass < passes; pass++) {
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int a = 0, r = 0, g = 0, bl = 0, n = 0;
                    for (int dx = -1; dx <= 1; dx++) {
                        int xx = Math.min(w - 1, Math.max(0, x + dx));
                        int v = px[y * w + xx];
                        a += v >>> 24; r += (v >> 16) & 0xFF; g += (v >> 8) & 0xFF; bl += v & 0xFF; n++;
                    }
                    tmp[y * w + x] = ((a / n) << 24) | ((r / n) << 16) | ((g / n) << 8) | (bl / n);
                }
            }
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int a = 0, r = 0, g = 0, bl = 0, n = 0;
                    for (int dy = -1; dy <= 1; dy++) {
                        int yy = Math.min(h - 1, Math.max(0, y + dy));
                        int v = tmp[yy * w + x];
                        a += v >>> 24; r += (v >> 16) & 0xFF; g += (v >> 8) & 0xFF; bl += v & 0xFF; n++;
                    }
                    px[y * w + x] = ((a / n) << 24) | ((r / n) << 16) | ((g / n) << 8) | (bl / n);
                }
            }
        }
        b.setPixels(px, 0, w, 0, 0, w, h);
    }

    /**
     * The cars on the main roads at night (2.32): small lights moving along each road's edges at a city's pace, warm white
     * headlights where they're coming towards us and red tail lights where they're going away, smaller and fainter
     * further off; only where the road shows (a building in front hides them). Each car keeps its own place and speed,
     * so the traffic flows the same way each frame.
     */
    static final class Traffic {
        byte[] mask;
        int mw, mh;
        /** Each run along a road's edge: x, y (pixels), X, Z (metres on the ground), s (metres along), five per point. */
        float[][] runs;
        float[] len;
        float[][] start, speed;
        /** Each car's colour by day (2.43). */
        int[][] col;
        float lights, fk;

        static Traffic make(Data d, byte[] mask, int mw, int mh, float x0, float k, float f, CityScene.Light l, long seed,
                            boolean left) {
            Traffic tr = new Traffic();
            tr.mask = mask;
            tr.mw = mw;
            tr.mh = mh;
            tr.lights = l.lights;
            // the cars in the scene's own tones, not paint colours (2.43): pale, light, mid, deep
            int[] tones = {Scene.mix(l.lit, 0xFFFFFFFF, 0.35f), l.lit, l.near, l.shade, Scene.mix(l.shade, l.front, 0.5f)};
            tr.fk = f * k;
            float[] lane = new float[d.roads.length];
            for (int i = 0; i < lane.length; i++) lane[i] = Math.max(1.6f, d.roadW[i] / 4f);
            List<float[]> runs = runs(d, d.roads, d.roadH, d.roadM, lane, null, x0, k, f, left);
            float total = 0;
            for (float[] q : runs) total += runLength(q);
            float gap = Math.max(15f, total / 2000f); // a car every 15 m or so per lane (only those in sight are drawn), at most 2,000
            Random r = new Random(seed ^ 0xCA75L);
            tr.runs = runs.toArray(new float[0][]);
            tr.len = new float[tr.runs.length];
            tr.start = new float[tr.runs.length][];
            tr.speed = new float[tr.runs.length][];
            tr.col = new int[tr.runs.length][];
            for (int i = 0; i < tr.runs.length; i++) {
                float L = runLength(tr.runs[i]);
                tr.len[i] = L;
                float want = L / gap;
                int c = (int) want + (r.nextFloat() < want - (int) want ? 1 : 0);
                tr.start[i] = new float[c];
                tr.speed[i] = new float[c];
                tr.col[i] = new int[c];
                for (int j = 0; j < c; j++) {
                    tr.start[i][j] = r.nextFloat() * L;
                    tr.speed[i][j] = 7f + r.nextFloat() * 6f; // 25 to 47 km/h
                    tr.col[i][j] = pick(tones, CAR_SHARE, r.nextFloat());
                }
            }
            return tr;
        }

    }

    /** The share of each of the cars' tones by day (2.43): pale, light, mid, deep, deepest. */
    private static final float[] CAR_SHARE = {0.3f, 0.2f, 0.2f, 0.15f, 0.15f};

    private static int pick(int[] cols, float[] share, float u) {
        float acc = 0;
        for (int i = 0; i < cols.length; i++) {
            acc += share[i];
            if (u < acc) return cols[i];
        }
        return cols[cols.length - 1];
    }

    /** A distance as a colour for the depth map (2.43): its log, in the colour's 24 bits. */
    private static int depthCode(float dist) {
        return 0xFF000000 | (int) Math.min(0xFFFFFF, Math.max(0, Math.log1p(Math.max(0f, dist)) * 100000.0));
    }

    /** One channel of the half-size mask (16 red: the roads, 8 green: the water), any of it counting. */
    private static byte[] channel(int[] px, int shift) {
        byte[] m = new byte[px.length];
        for (int i = 0; i < px.length; i++) m[i] = (byte) (((px[i] >>> shift) & 0xFF) > 40 ? 1 : 0);
        return m;
    }

    /** A run's stride: x, y (pixels), X, Z (metres on the ground), s (metres along), height (m), mode. */
    private static final int RS = 7;

    /**
     * The lanes along {@code lines} (screen points of lines, at {@code heights} metres up, null: on the ground): each way
     * in its own lane {@code lane[i]} metres to the right of the line (or the left where they drive so; a boat keeps to
     * the right as well), each run as {@link #RS} numbers a point; {@code modes} (may be null) each point's mode (0 shown
     * where the road shows, 1 on a landmark bridge: always, 2 hidden). {@code owner} (may be null) gets each run's line.
     */
    private static List<float[]> runs(Data d, float[][] lines, float[][] heights, byte[][] modes, float[] laneM,
                                      List<Integer> owner, float x0, float k, float f, boolean left) {
        float hzr = HZ * d.h;
        List<float[]> runs = new ArrayList<>();
        float[] buf = new float[256];
        for (int i = 0; i < lines.length; i++) {
            float[] s = lines[i];
            int n = s.length / 2;
            if (n < 2) continue;
            float[] gx = new float[n], gz = new float[n], gh = new float[n], gm = new float[n];
            boolean[] ok = new boolean[n];
            for (int j = 0; j < n; j++) {
                float y = s[2 * j + 1];
                gh[j] = heights != null && heights[i] != null && j < heights[i].length ? heights[i][j] : 0f;
                gm[j] = modes != null && modes[i] != null && j < modes[i].length ? modes[i][j] : 0f;
                float over = d.hc - gh[j];
                ok[j] = y > hzr + 0.3f && over > 1f;
                gz[j] = ok[j] ? over * f / (y - hzr) : 0f;
                gx[j] = (s[2 * j] - d.w / 2f) * gz[j] / f;
            }
            float lane = laneM[i];
            for (int dir = 0; dir < 2; dir++) {
                int cnt = 0;
                for (int e = 0; e < n; e++) {
                    int j = dir == 0 ? e : n - 1 - e, jn = dir == 0 ? Math.min(n - 1, e + 1) : Math.max(0, n - 2 - e);
                    int jp = dir == 0 ? Math.max(0, e - 1) : Math.min(n - 1, n - e);
                    if (!ok[j]) { cnt = flush(runs, buf, cnt); continue; }
                    int a0 = ok[jp] ? jp : j, a1 = ok[jn] ? jn : j;
                    float tx = gx[a1] - gx[a0], tz = gz[a1] - gz[a0], tl = (float) Math.hypot(tx, tz);
                    if (tl < 0.01f) continue;
                    float sd = left ? -1f : 1f; // the right-hand side of the way it goes is (tz, -tx)
                    float px0 = gx[j] + tz / tl * lane * sd, pz0 = Math.max(5f, gz[j] - tx / tl * lane * sd);
                    if (RS * cnt + RS > buf.length) buf = java.util.Arrays.copyOf(buf, buf.length * 2);
                    buf[RS * cnt] = (d.w / 2f + px0 * f / pz0 - x0) * k;
                    buf[RS * cnt + 1] = (hzr + (d.hc - gh[j]) * f / pz0) * k;
                    buf[RS * cnt + 2] = px0;
                    buf[RS * cnt + 3] = pz0;
                    buf[RS * cnt + 5] = gh[j];
                    buf[RS * cnt + 6] = gm[j];
                    cnt++;
                }
                flush(runs, buf, cnt);
            }
            if (owner != null) while (owner.size() < runs.size()) owner.add(i);
        }
        return runs;
    }

    /** A run's length in metres (its last point's distance along). */
    private static float runLength(float[] q) { return q[q.length - RS + 4]; }

    /** Ends a run: its distances along added; kept if it's long enough. Returns 0 (the next run's count). */
    private static int flush(List<float[]> runs, float[] buf, int cnt) {
        if (cnt < 2) return 0;
        float[] q = java.util.Arrays.copyOf(buf, RS * cnt);
        float acc = 0;
        q[4] = 0;
        for (int i = 1; i < cnt; i++) {
            acc += (float) Math.hypot(q[RS * i + 2] - q[RS * (i - 1) + 2], q[RS * i + 3] - q[RS * (i - 1) + 3]);
            q[RS * i + 4] = acc;
        }
        if (acc > 5f) runs.add(q);
        return 0;
    }

    /**
     * Where a car or boat is along its run at {@code at} metres: x, y, z, the run's direction on screen, pixels per metre,
     * coming nearer; with room, X and its heading on the ground (7 to 9), its height (10) and its mode (11, the larger
     * of its two points': hidden wins, a landmark bridge's next).
     */
    private static boolean place(float[] q, float at, float[] o) {
        int np = q.length / RS, lo = 0, hi = np - 1;
        while (hi - lo > 1) {
            int mid = (lo + hi) >>> 1;
            if (q[RS * mid + 4] <= at) lo = mid; else hi = mid;
        }
        int L0 = RS * lo, H0 = RS * hi;
        float s0 = q[L0 + 4], s1 = q[H0 + 4], u = s1 > s0 ? (at - s0) / (s1 - s0) : 0f;
        float sx = q[H0] - q[L0], sy = q[H0 + 1] - q[L0 + 1], sl = (float) Math.hypot(sx, sy);
        o[0] = q[L0] + sx * u;
        o[1] = q[L0 + 1] + sy * u;
        o[2] = q[L0 + 3] + (q[H0 + 3] - q[L0 + 3]) * u;
        o[3] = sl > 0.01f ? sx / sl : 1f;
        o[4] = sl > 0.01f ? sy / sl : 0f;
        o[5] = s1 > s0 ? sl / (s1 - s0) : 0f;
        o[6] = q[H0 + 3] < q[L0 + 3] ? 1f : 0f; // coming nearer
        if (o.length >= 12) {
            o[7] = q[L0 + 2] + (q[H0 + 2] - q[L0 + 2]) * u;
            float gx = q[H0 + 2] - q[L0 + 2], gz = q[H0 + 3] - q[L0 + 3], gl = (float) Math.hypot(gx, gz);
            o[8] = gl > 1e-4f ? gx / gl : 0f;
            o[9] = gl > 1e-4f ? gz / gl : 1f;
            o[10] = q[L0 + 5] + (q[H0 + 5] - q[L0 + 5]) * u;
            o[11] = Math.max(q[L0 + 6], q[H0 + 6]);
        }
        return true;
    }

    private static boolean shows(byte[] mask, int mw, int mh, float x, float y) {
        int mx = (int) (x * 0.5f), my = (int) (y * 0.5f);
        return mx >= 0 && my >= 0 && mx < mw && my < mh && mask[my * mw + mx] != 0;
    }

    /**
     * The cars at {@code t} seconds (the city's {@code traffic}, or nothing): by day (2.43) small cars in their own
     * colours, a darker glass and a soft shadow under them; at night their lights (2.32); both in the half light.
     */
    static void traffic(Canvas c, Traffic tr, float t, Paint p, float px) {
        if (tr == null) return;
        p.setShader(null);
        float body = clamp(1f - tr.lights * 1.4f), lightsA = tr.lights < 0.05f ? 0f : tr.lights;
        if (body < 0.02f && lightsA <= 0f) return;
        float[] o = new float[12];
        for (int r = 0; r < tr.runs.length; r++) {
            float[] q = tr.runs[r];
            float L = tr.len[r];
            for (int j = 0; j < tr.start[r].length; j++) {
                place(q, (tr.start[r][j] + t * tr.speed[r][j]) % L, o);
                float x = o[0], y = o[1], z = o[2], ux = o[3], uy = o[4];
                if (o[11] >= 2f) continue; // in a tunnel (or through a landmark bridge's tower)
                if (o[11] < 1f && !shows(tr.mask, tr.mw, tr.mh, x, y)) continue; // on a landmark bridge: always

                if (body > 0.02f) {
                    float len = 4.6f * o[5], wid = 1.9f * tr.fk / z;
                    if (Math.max(len, wid) >= 0.9f) {
                        float a = body * (0.55f + 0.45f * Math.min(1f, 400f / z));
                        float hl = Math.max(0f, len - wid) / 2f, ww = Math.max(0.7f, wid);
                        p.setStyle(Paint.Style.STROKE);
                        p.setStrokeCap(Paint.Cap.ROUND);
                        p.setStrokeWidth(ww);
                        p.setColor(Scene.alpha(0xFF2B2F4C, 0.26f * a)); // its shadow on the road
                        c.drawLine(x - ux * hl, y - uy * hl + ww * 0.45f, x + ux * hl, y + uy * hl + ww * 0.45f, p);
                        int col = tr.col[r][j];
                        p.setColor(Scene.alpha(col, a));
                        c.drawLine(x - ux * hl, y - uy * hl, x + ux * hl, y + uy * hl, p);
                        if (ww > 1.6f) { // its glass, darker, on its middle
                            p.setStrokeWidth(ww * 0.55f);
                            p.setColor(Scene.alpha(Scene.mix(col, 0xFF39415F, 0.55f), a));
                            c.drawLine(x - ux * hl * 0.4f, y - uy * hl * 0.4f, x + ux * hl * 0.4f, y + uy * hl * 0.4f, p);
                        }
                        p.setStyle(Paint.Style.FILL);
                    }
                }
                if (lightsA <= 0f) continue;
                int col = o[6] > 0f ? HEAD : TAIL; // coming nearer: its headlights
                float rad = Math.max(0.55f, Math.min(1.2f * px, 0.75f * tr.fk / z));
                float a = Math.min(1f, lightsA * (0.65f + 0.45f * Math.min(1f, 500f / z)));
                // a short streak of light along its way, as a car's lights read at night
                float trail = Math.max(rad, Math.min(5f * px, 7f * tr.fk / z)); // about 7 m
                float ex = x - ux * trail, ey = y - uy * trail;
                if (rad > 0.7f) {
                    p.setColor(Scene.alpha(HALATION, 0.04f * a)); // its halation, faint and wide (2.33)
                    c.drawCircle(x, y, rad * 5.5f, p);
                    p.setColor(Scene.alpha(col, 0.18f * a));
                    c.drawCircle(x, y, rad * 2.6f, p);
                }
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeCap(Paint.Cap.ROUND);
                p.setStrokeWidth(rad * 1.5f);
                p.setColor(Scene.alpha(col, 0.55f * a));
                c.drawLine(ex, ey, x, y, p);
                p.setStyle(Paint.Style.FILL);
                p.setColor(Scene.alpha(Scene.mix(col, 0xFFFFFFFF, 0.35f), a));
                c.drawCircle(x, y, rad, p);
            }
        }
    }

    /**
     * The boats on the big rivers and canals (2.43): Kenney's Watercraft Kit (CC0, packed by tools/scenes/boats.py):
     * freighters, tow boats, tour boats and houseboats, speed boats, a fishing boat and sailing boats on a river,
     * houseboats and small boats on a canal; each drawn in 3D at its heading, lit by the scene's sun, keeping to the
     * right at a boat's pace with a pale wake behind it; at night dark with their cabins' lights. A far boat (a few
     * pixels) is drawn as a simple hull. Only where the water shows (a bridge or a building hides them).
     */
    static final class Boats {
        byte[] mask;
        int mw, mh;
        float[][] runs;
        float[] len;
        float[][] start, speed;
        /** Each boat's model ({@link #models()}), or -1 for the simple hull (no models). */
        int[][] model;
        float lights, fk, w2, hzr, hc, f, x0, k, night, lx, ly, lz;
        int haze, dark, light;
        /** How far off what stands at each pixel is (half size, {@link Float#MAX_VALUE} for the ground), to hide a boat
         * only where something nearer than it stands: under a bridge's deck, behind a building. */
        float[] depth;

        static Boats make(Data d, byte[] mask, int mw, int mh, float x0, float k, float f, CityScene.Light l, int haze,
                          long seed, boolean left) {
            Boats b = new Boats();
            b.mask = mask;
            b.mw = mw;
            b.mh = mh;
            b.lights = l.lights;
            b.night = l.night;
            b.haze = haze;
            // the boats in the scene's own tones (2.43): each model's colours by their lightness, from deep to pale
            b.dark = Scene.mix(l.shade, l.front, 0.4f);
            b.light = Scene.mix(l.lit, 0xFFFFFFFF, 0.4f);
            b.fk = f * k;
            b.w2 = d.w / 2f;
            b.hzr = HZ * d.h;
            b.hc = d.hc;
            b.f = f;
            b.x0 = x0;
            b.k = k;
            // the sun as the buildings have it (115 degrees round from the view), a little up
            double rel = Math.toRadians(l.sunLeft ? -115 : 115);
            float sx = (float) Math.sin(rel), sz = (float) Math.cos(rel), sy = 0.8f, sl = (float) Math.sqrt(sx * sx + sy * sy + sz * sz);
            b.lx = sx / sl;
            b.ly = sy / sl;
            b.lz = sz / sl;
            float[] lane = new float[d.boats.length];
            for (int i = 0; i < lane.length; i++) lane[i] = d.boatKind[i] == 1 ? 2.5f : 9f;
            List<Integer> owner = new ArrayList<>();
            List<float[]> runs = runs(d, d.boats, null, null, lane, owner, x0, k, f, left);
            Model[] ms = models();
            Random r = new Random(seed ^ 0xB0A7L);
            b.runs = runs.toArray(new float[0][]);
            b.len = new float[b.runs.length];
            b.start = new float[b.runs.length][];
            b.speed = new float[b.runs.length][];
            b.model = new int[b.runs.length][];
            for (int i = 0; i < b.runs.length; i++) {
                float L = runLength(b.runs[i]);
                b.len[i] = L;
                boolean canal = d.boatKind[owner.get(i)] == 1;
                float want = L / (canal ? 170f : 260f);
                int c = (int) want + (r.nextFloat() < want - (int) want ? 1 : 0);
                b.start[i] = new float[c];
                b.speed[i] = new float[c];
                b.model[i] = new int[c];
                for (int j = 0; j < c; j++) {
                    b.start[i][j] = r.nextFloat() * L;
                    float u = r.nextFloat();
                    // on a river a quarter freighters and tow boats, two fifths tour boats, the rest small; on a canal
                    // houseboats and small boats
                    int kind = canal ? (u < 0.55f ? 1 : 2) : (u < 0.25f ? 0 : u < 0.65f ? 1 : 2);
                    b.model[i][j] = pickModel(ms, kind, canal ? 2 : 1, r.nextFloat());
                    float pace = kind == 0 ? 2.5f : kind == 1 ? 3.2f : 5f; // metres a second
                    b.speed[i][j] = pace * (canal ? 0.6f : 1f) * (0.8f + 0.4f * r.nextFloat());
                }
            }
            return b;
        }
    }

    /** A boat as tools/scenes/boats.py packs it: its length 1 along +Z (the bow), its waterline at 0. */
    static final class Model {
        int kind, where, length;
        float[] v, n;
        int[] f, col;
        float beam;
    }

    private static Model[] models;

    /** The boats' models (assets/boats.bin), read once; none if it can't be read. */
    static synchronized Model[] models() {
        if (models != null) return models;
        List<Model> out = new ArrayList<>();
        try (InputStream in = assets.open("boats.bin")) {
            java.nio.ByteBuffer bb = java.nio.ByteBuffer.wrap(readAll(in)).order(java.nio.ByteOrder.LITTLE_ENDIAN);
            if (bb.get() == 'B' && bb.get() == 'T' && bb.get() == 'S' && bb.get() == '1') {
                int count = bb.get() & 0xFF;
                for (int i = 0; i < count; i++) {
                    Model m = new Model();
                    m.kind = bb.get() & 0xFF;
                    m.where = bb.get() & 0xFF;
                    m.length = bb.get() & 0xFF;
                    int nv = bb.getShort() & 0xFFFF, nf = bb.getShort() & 0xFFFF;
                    m.v = new float[3 * nv];
                    float minx = Float.MAX_VALUE, maxx = -Float.MAX_VALUE;
                    for (int j = 0; j < 3 * nv; j++) m.v[j] = bb.getFloat();
                    for (int j = 0; j < nv; j++) { minx = Math.min(minx, m.v[3 * j]); maxx = Math.max(maxx, m.v[3 * j]); }
                    m.beam = maxx - minx;
                    m.f = new int[3 * nf];
                    for (int j = 0; j < 3 * nf; j++) m.f[j] = bb.getShort() & 0xFFFF;
                    m.col = new int[nf];
                    for (int j = 0; j < nf; j++) m.col[j] = 0xFF000000 | ((bb.get() & 0xFF) << 16) | ((bb.get() & 0xFF) << 8) | (bb.get() & 0xFF);
                    m.n = new float[3 * nf];
                    for (int j = 0; j < nf; j++) {
                        int a0 = 3 * m.f[3 * j], a1 = 3 * m.f[3 * j + 1], a2 = 3 * m.f[3 * j + 2];
                        float ux = m.v[a1] - m.v[a0], uy = m.v[a1 + 1] - m.v[a0 + 1], uz = m.v[a1 + 2] - m.v[a0 + 2];
                        float vx = m.v[a2] - m.v[a0], vy = m.v[a2 + 1] - m.v[a0 + 1], vz = m.v[a2 + 2] - m.v[a0 + 2];
                        float nx = uy * vz - uz * vy, ny = uz * vx - ux * vz, nz = ux * vy - uy * vx;
                        float nl = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
                        if (nl < 1e-9f) nl = 1f;
                        m.n[3 * j] = nx / nl;
                        m.n[3 * j + 1] = ny / nl;
                        m.n[3 * j + 2] = nz / nl;
                    }
                    out.add(m);
                }
            }
        } catch (Exception ignored) { }
        models = out.toArray(new Model[0]);
        return models;
    }

    /** One of the models of {@code kind} that sails {@code where} (1 rivers, 2 canals), or -1. */
    private static int pickModel(Model[] ms, int kind, int where, float u) {
        int n = 0;
        for (Model m : ms) if (m.kind == kind && (m.where & where) != 0) n++;
        if (n == 0) return -1;
        int pick = Math.min(n - 1, (int) (u * n));
        for (int i = 0; i < ms.length; i++) if (ms[i].kind == kind && (ms[i].where & where) != 0 && pick-- == 0) return i;
        return -1;
    }

    private static final Path BOAT_PATH = new Path();
    private static float[] bx = new float[256], by = new float[256], bwx = new float[256], bwy = new float[256], bwz = new float[256];
    private static long[] bkeys = new long[512];

    /** The boats at {@code t} seconds (the city's {@code boats}, or nothing). */
    static void boats(Canvas c, Boats bt, float t, Paint p, float px) {
        if (bt == null) return;
        p.setShader(null);
        float day = clamp(1f - bt.lights * 1.4f), night = bt.lights < 0.05f ? 0f : bt.lights;
        Model[] ms = models();
        float[] o = new float[12];
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
        for (int r = 0; r < bt.runs.length; r++) {
            float[] q = bt.runs[r];
            float L = bt.len[r];
            for (int j = 0; j < bt.start[r].length; j++) {
                place(q, (bt.start[r][j] + t * bt.speed[r][j]) % L, o);
                // on screen from where it is on the ground (a lane's points can be far apart, and in perspective the
                // screen isn't straight between them)
                float z = o[2], x = (bt.w2 + o[7] * bt.f / z - bt.x0) * bt.k, y = (bt.hzr + bt.hc * bt.f / z) * bt.k;
                float ux = o[3], uy = o[4];
                float dist = (float) Math.sqrt(o[7] * o[7] + z * z + bt.hc * bt.hc);
                if (nearer(bt, x, y, dist)) continue; // its middle hidden: under a bridge, behind a building
                int mi = bt.model[r][j];
                Model m = mi >= 0 && mi < ms.length ? ms[mi] : null;
                float lm = m != null ? m.length : 12f, wm = m != null ? m.beam * m.length * (m.kind == 0 ? 0.6f : 0.85f) : 3.5f;
                float len = lm * o[5], wid = wm * bt.fk / z;
                if (Math.max(len, wid) < 1.1f) continue;
                float ww = Math.max(0.8f, wid), hl = Math.max(ww * 0.6f, len / 2f);
                float a = 0.6f + 0.4f * Math.min(1f, 600f / z);
                // what stands nearer than it over it (a bridge's deck, a pier, a building) is cut out of it, on a layer
                float R = Math.max(len * 1.6f + 3f * ww, 8f), tall = lm * bt.fk / z * 1.1f + 2f;
                android.graphics.RectF box = new android.graphics.RectF(x - R, y - tall - R * 0.2f, x + R, y + R * 0.6f);
                Bitmap cut = occluders(bt, box, dist);
                int saved = cut != null ? c.saveLayer(box, null) : -1;
                // its wake: a faint pale streak behind it, narrower than the boat, widening a little and fading out
                // (2.43: subtler, after the user)
                p.setStyle(Paint.Style.STROKE);
                float wake = Math.max(len * 0.9f, 2f * ww);
                for (int s2 = 0; s2 < 3; s2++) {
                    float w0 = hl * 0.85f + wake * s2 / 3f, w1 = hl * 0.85f + wake * (s2 + 1) / 3f;
                    p.setStrokeWidth(Math.max(0.6f, ww * (0.35f + 0.12f * s2)));
                    p.setColor(Scene.alpha(0xFFFFFFFF, (0.14f - 0.04f * s2) * a * (0.35f + 0.65f * day)));
                    c.drawLine(x - ux * w0, y - uy * w0, x - ux * w1, y - uy * w1, p);
                }
                p.setStyle(Paint.Style.FILL);
                if (m != null && len >= 3.5f) {
                    model(c, p, bt, m, o, day, night, a);
                } else { // far off: a simple pointed hull in a boat's white
                    float nx = -uy * ww / 2f, ny = ux * ww / 2f;
                    BOAT_PATH.reset();
                    float sh = hl - Math.min(hl, ww * 1.1f);
                    BOAT_PATH.moveTo(x - ux * hl + nx, y - uy * hl + ny);
                    BOAT_PATH.lineTo(x + ux * sh + nx, y + uy * sh + ny);
                    BOAT_PATH.lineTo(x + ux * hl, y + uy * hl);
                    BOAT_PATH.lineTo(x + ux * sh - nx, y + uy * sh - ny);
                    BOAT_PATH.lineTo(x - ux * hl - nx, y - uy * hl - ny);
                    BOAT_PATH.close();
                    p.setColor(Scene.alpha(Scene.mix(Scene.mix(bt.light, bt.haze, fog(z, bt.night)), 0xFF141A33, 0.75f * night), a));
                    c.drawPath(BOAT_PATH, p);
                }
                if (night > 0f) { // its lights: the cabin's warm windows, and their reflection down the water
                    int n = m != null && m.kind == 1 ? 4 : 1;
                    float rad = Math.max(0.6f, Math.min(1.2f * px, 0.7f * bt.fk / z));
                    for (int k2 = 0; k2 < n; k2++) {
                        float u = n == 1 ? (m != null && m.kind == 0 ? -0.75f : 0f) : -0.5f + 1.0f * k2 / (n - 1);
                        float lx = x + ux * hl * u, ly = y + uy * hl * u - Math.max(ww * 0.4f, 0.12f * lm * bt.fk / z);
                        p.setColor(Scene.alpha(WARM[k2 % WARM.length], Math.min(1f, 1.1f * night)));
                        c.drawCircle(lx, ly, rad, p);
                        p.setColor(Scene.alpha(WARM[1], 0.22f * night * a));
                        c.drawRect(lx - rad * 0.6f, ly + rad, lx + rad * 0.6f, ly + rad * 5f, p);
                    }
                }
                if (saved >= 0) {
                    Paint cp = new Paint(Paint.FILTER_BITMAP_FLAG);
                    cp.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));
                    int hx0 = (int) Math.floor(box.left / 2f), hy0 = (int) Math.floor(box.top / 2f);
                    c.drawBitmap(cut, null, new android.graphics.RectF(hx0 * 2f, hy0 * 2f, (hx0 + cut.getWidth()) * 2f,
                            (hy0 + cut.getHeight()) * 2f), cp);
                    c.restoreToCount(saved);
                    cut.recycle();
                }
            }
        }
    }

    /** Whether something nearer than {@code dist} stands at (x, y) (the depth map, half size). */
    private static boolean nearer(Boats bt, float x, float y, float dist) {
        if (bt.depth == null) return false;
        int mx = (int) (x * 0.5f), my = (int) (y * 0.5f);
        if (mx < 0 || my < 0 || mx >= bt.mw || my >= bt.mh) return false;
        return bt.depth[my * bt.mw + mx] < dist * 0.97f;
    }

    /** What stands nearer than {@code dist} within {@code box} (half size, opaque where it does), or null if nothing. */
    private static Bitmap occluders(Boats bt, android.graphics.RectF box, float dist) {
        if (bt.depth == null) return null;
        int x0 = Math.max(0, (int) Math.floor(box.left / 2f)), y0 = Math.max(0, (int) Math.floor(box.top / 2f));
        int x1 = Math.min(bt.mw, (int) Math.ceil(box.right / 2f)), y1 = Math.min(bt.mh, (int) Math.ceil(box.bottom / 2f));
        if (x1 <= x0 || y1 <= y0) return null;
        int w = x1 - x0, h = y1 - y0;
        int[] px = new int[w * h];
        boolean any = false;
        float lim = dist * 0.97f;
        for (int yy = 0; yy < h; yy++) {
            for (int xx = 0; xx < w; xx++) {
                if (bt.depth[(y0 + yy) * bt.mw + x0 + xx] < lim) {
                    px[yy * w + xx] = 0xFF000000;
                    any = true;
                }
            }
        }
        if (!any) return null;
        // its left and top edges at the box's (clipped to the map): pad so the drawing lines up with the box
        int bx0 = (int) Math.floor(box.left / 2f), by0 = (int) Math.floor(box.top / 2f);
        if (bx0 == x0 && by0 == y0) return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888);
        int W2 = x1 - bx0, H2 = y1 - by0;
        int[] full = new int[W2 * H2];
        for (int yy = 0; yy < h; yy++) System.arraycopy(px, yy * w, full, (yy + y0 - by0) * W2 + (x0 - bx0), w);
        return Bitmap.createBitmap(full, W2, H2, Bitmap.Config.ARGB_8888);
    }

    /**
     * One boat's model at its place (o: x, y, z, its way on screen, pixels a metre, coming, X, its heading on the ground):
     * every face turned to the camera, far to near, lit by the sun as the buildings are, softened to the scene's
     * colours by day, hazed with distance, dark at night. Faces under a sixth of a pixel are left out.
     */
    private static void model(Canvas c, Paint p, Boats bt, Model m, float[] o, float day, float night, float a) {
        int nv = m.v.length / 3, nf = m.f.length / 3;
        if (bx.length < nv) {
            bx = new float[nv]; by = new float[nv]; bwx = new float[nv]; bwy = new float[nv]; bwz = new float[nv];
        }
        if (bkeys.length < nf) bkeys = new long[nf];
        // the model's frame turned onto its heading; the scene's frame (x right, y up, z ahead) is left-handed and the
        // model's right-handed, so its x is taken the other way (else every face would turn inside out); a freighter
        // slimmed to a river barge's lines
        float X0 = o[7], Z0 = o[2], hx = o[8], hz = o[9], rx = -hz, rz = hx, Lm = m.length;
        float slim = m.kind == 0 ? 0.6f : 0.85f;
        for (int i = 0; i < nv; i++) {
            float mx = m.v[3 * i] * Lm * slim, my = m.v[3 * i + 1] * Lm, mz = m.v[3 * i + 2] * Lm;
            float X = X0 + mx * rx + mz * hx, Z = Z0 + mx * rz + mz * hz;
            if (Z < 2f) return;
            bwx[i] = X;
            bwy[i] = my;
            bwz[i] = Z;
            bx[i] = (bt.w2 + X * bt.f / Z - bt.x0) * bt.k;
            by[i] = (bt.hzr + (bt.hc - my) * bt.f / Z) * bt.k;
        }
        int nk = 0;
        for (int j = 0; j < nf; j++) {
            int i0 = m.f[3 * j], i1 = m.f[3 * j + 1], i2 = m.f[3 * j + 2];
            float nX = m.n[3 * j] * rx + m.n[3 * j + 2] * hx, nY = m.n[3 * j + 1], nZ = m.n[3 * j] * rz + m.n[3 * j + 2] * hz;
            float cx = (bwx[i0] + bwx[i1] + bwx[i2]) / 3f, cy = (bwy[i0] + bwy[i1] + bwy[i2]) / 3f, cz = (bwz[i0] + bwz[i1] + bwz[i2]) / 3f;
            if (nX * -cx + nY * (bt.hc - cy) + nZ * -cz <= 0f) continue; // turned away
            float area = Math.abs((bx[i1] - bx[i0]) * (by[i2] - by[i0]) - (bx[i2] - bx[i0]) * (by[i1] - by[i0])) * 0.5f;
            if (area < 0.16f) continue;
            float dist = cx * cx + (cy - bt.hc) * (cy - bt.hc) + cz * cz;
            bkeys[nk++] = ((long) Float.floatToIntBits(dist) << 16) | j;
        }
        java.util.Arrays.sort(bkeys, 0, nk);
        float fogA = fog(Z0, bt.night);
        p.setStyle(Paint.Style.FILL_AND_STROKE); // a hair larger, so the seams between faces never show
        p.setStrokeWidth(0.35f);
        for (int kk = nk - 1; kk >= 0; kk--) {
            int j = (int) (bkeys[kk] & 0xFFFF);
            int i0 = m.f[3 * j], i1 = m.f[3 * j + 1], i2 = m.f[3 * j + 2];
            float nX = m.n[3 * j] * rx + m.n[3 * j + 2] * hx, nY = m.n[3 * j + 1], nZ = m.n[3 * j] * rz + m.n[3 * j + 2] * hz;
            float lit = 0.62f + 0.38f * Math.max(0f, nX * bt.lx + nY * bt.ly + nZ * bt.lz);
            int mc = m.col[j];
            float lum = (0.299f * ((mc >> 16) & 0xFF) + 0.587f * ((mc >> 8) & 0xFF) + 0.114f * (mc & 0xFF)) / 255f;
            int col = Scene.mix(bt.dark, bt.light, (float) Math.pow(lum, 0.85));
            col = 0xFF000000 | (Math.round(((col >> 16) & 0xFF) * lit) << 16) | (Math.round(((col >> 8) & 0xFF) * lit) << 8)
                    | Math.round((col & 0xFF) * lit);
            col = Scene.mix(Scene.mix(col, bt.haze, fogA), 0xFF141A33, 0.72f * night);
            p.setColor(Scene.alpha(col, a));
            BOAT_PATH.reset();
            BOAT_PATH.moveTo(bx[i0], by[i0]);
            BOAT_PATH.lineTo(bx[i1], by[i1]);
            BOAT_PATH.lineTo(bx[i2], by[i2]);
            BOAT_PATH.close();
            c.drawPath(BOAT_PATH, p);
        }
        p.setStyle(Paint.Style.FILL);
    }
}
