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

    private static final float HZ = 0.64f, FN = (float) (0.5 / Math.tan(Math.toRadians(20)));
    private static final int WALL = 0, ROOF = 1, WATER = 2, PARK = 3, ROAD = 4, LM = 5, LM_GLASS = 6, LM_LIGHT = 7,
            LM_ROOF = 9, LM_LATTICE = 10, LM_GIRDER = 11;

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

    /** The scene of {@code city} in country {@code cc}, or null when there's none (the drawn scene stays). */
    static synchronized Data load(String city, String cc) {
        if (assets == null || city == null) return null;
        String key = cc != null ? slug(city, cc) : byName(city);
        if (key == null) return null;
        Data d = CACHE.get(key);
        if (d == null) {
            try (InputStream in = new InflaterInputStream(assets.open("scenes/" + key + ".bin"))) {
                d = parse(readAll(in));
            } catch (Exception e) {
                d = NONE;
                String alt = cc != null ? byName(city) : null; // its country as saved may differ from the list's
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

    /** Reads formats 3 and 4 (see tools/scenes/README.md). */
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
            for (int i = 0; i < nt; i++) {
                d.roadW[i] = r.u8();
                d.roads[i] = r.pts();
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
    static CityScene.Built build(String city, String cc, int w, int h, float px, CityScene.Light l, long seed) {
        Data d = load(city, cc);
        if (d == null) return null;
        CityScene.Built b = new CityScene.Built();
        b.still = Bitmap.createBitmap(Math.max(2, w), Math.max(2, h), Bitmap.Config.ARGB_8888);
        draw(new Canvas(b.still), d, w, h, l, seed, b, cc != null && LEFT_HAND.contains(cc.toUpperCase(Locale.ROOT)),
                "Paris".equalsIgnoreCase(city) && (cc == null || "FR".equalsIgnoreCase(cc)));
        punch(b.still, PUNCH_CLARITY, PUNCH_VIBRANCE);
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
        // the hills on the horizon: the far line, then the near one
        for (int i = d.hills.length - 1; i >= 0; i--) {
            float[] line = d.hills[i];
            if (line.length < 4) continue;
            path.reset();
            path.moveTo((line[0] - x0) * k, hz + 4 * k);
            for (int j = 0; j < line.length; j += 2) path.lineTo((line[j] - x0) * k, Math.min(hz + 4 * k, line[j + 1] * k));
            path.lineTo((line[line.length - 2] - x0) * k, hz + 4 * k);
            path.close();
            p.setColor(Scene.mix(l.far, l.horizon, i == d.hills.length - 1 ? 0.55f : 0.35f));
            c.drawPath(path, p);
        }
        // the ground: hazy far off, darker near
        int landFar = Scene.mix(l.far, l.horizon, 0.3f), landNear = Scene.mix(l.street, l.far, 0.15f);
        p.setShader(new LinearGradient(0, hz, 0, hz + (h - hz) / 2.2f, landFar, landNear, Shader.TileMode.CLAMP));
        c.drawRect(0, hz - 0.5f * k, w, h, p);
        p.setShader(null);
        // the water: the sky in it, deeper (2.33: the graded reference's water is darker than the sky it mirrors)
        int waterFar = Scene.mix(Scene.mix(l.horizon, l.mid, 0.3f), l.near, 0.32f), waterNear = Scene.mix(Scene.mix(l.horizon, l.mid, 0.65f), l.near, 0.6f);
        int park = Scene.mix(l.front, l.street, 0.35f), road = Scene.mix(l.street, l.edge, 0.18f);
        int[] order = {PARK, WATER, ROAD};
        // the roads' traffic at night (2.32): where a road shows (it, less everything later drawn over it), at half size
        Bitmap maskBmp = lights > 0.05f ? Bitmap.createBitmap(Math.max(2, w / 2), Math.max(2, h / 2), Bitmap.Config.ARGB_8888) : null;
        Canvas mask = maskBmp == null ? null : new Canvas(maskBmp);
        if (mask != null) mask.scale(0.5f, 0.5f);
        Paint mp = new Paint(Paint.ANTI_ALIAS_FLAG);
        mp.setColor(0xFFFFFFFF);
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
            if (d.kind[i] == -1) {
                block(c, glow, mask, clear, gp, p, path, d, d.pts[i], d.height[i], f, x0, k, hz, sun, fog, l, litC, shadeC, roofC,
                        farHaze, share, lights);
                continue;
            }
            int kind = d.kind[i];
            float[] s = d.pts[i];
            int base;
            if (kind == LM_ROOF) base = roofC;
            else if (d.facing[i] < 0) base = l.near;
            else {
                double lit = Math.cos(Math.toRadians(d.facing[i] - sun));
                base = lit > 0.1 ? litC : lit < -0.1 ? shadeC : l.near;
            }
            if (kind == LM || kind == LM_ROOF || kind == LM_LATTICE || kind == LM_GIRDER) base = tint(base, d.hue[i], d.chroma[i]);
            if (kind == LM_GLASS) base = Scene.mix(base, shadeC, 0.4f);
            int col = Scene.mix(base, farHaze, fog);
            poly(path, s, x0, k);
            if (d.main[i] && kind != LM_GLASS && lights > 0.05f) col = screen(col, MOON, 0.1f * lights); // the hero: a touch of moonlight, to stand out
            p.setColor(col);
            if (d.main[i] && kind != LM_GLASS && lights > 0.05f && heroFoot > heroTop) {
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
        if (maskBmp != null && d.roads != null) built.traffic = Traffic.make(d, maskBmp, x0, k, f, lights, seed, left);
        else if (maskBmp != null) maskBmp.recycle();
    }

    private static float clamp(float v) { return Math.max(0f, Math.min(1f, v)); }

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
    private static void block(Canvas c, Canvas glow, Canvas mask, Paint clear, Paint gp, Paint p, Path path, Data d, float[] s, float hm,
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
        for (int a = 0; a < cnt; a++) {
            int i = wi[a], j = (i + 1) % n;
            double lit = Math.cos(Math.toRadians(wf[a] - sun));
            int base = lit > 0.1 ? litC : lit < -0.1 ? shadeC : l.near;
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
            p.setColor(Scene.mix(roofC, farHaze, fog));
            c.drawPath(path, p);
            if (glow != null) glow.drawPath(path, clear);
            if (mask != null) mask.drawPath(path, clear);
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
        out.sort((a, b) -> Float.compare(b[0], a[0]));
        return out;
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
    static final float PUNCH_CLARITY = 0.6f, PUNCH_VIBRANCE = 0.3f;

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
        float lights, fk;

        static Traffic make(Data d, Bitmap mb, float x0, float k, float f, float lights, long seed, boolean left) {
            Traffic tr = new Traffic();
            tr.mw = mb.getWidth();
            tr.mh = mb.getHeight();
            int[] px = new int[tr.mw * tr.mh];
            mb.getPixels(px, 0, tr.mw, 0, 0, tr.mw, tr.mh);
            mb.recycle();
            tr.mask = new byte[px.length];
            for (int i = 0; i < px.length; i++) tr.mask[i] = (byte) ((px[i] >>> 24) > 40 ? 1 : 0); // a far road is thin: any of it counts
            tr.lights = lights;
            tr.fk = f * k;
            float hzr = HZ * d.h;
            List<float[]> runs = new ArrayList<>();
            float[] buf = new float[256];
            int nr = d.roads == null ? 0 : d.roads.length;
            for (int i = 0; i < nr; i++) {
                float[] s = d.roads[i];
                int n = s.length / 2;
                if (n < 2) continue;
                float[] gx = new float[n], gz = new float[n];
                boolean[] ok = new boolean[n];
                for (int j = 0; j < n; j++) {
                    float y = s[2 * j + 1];
                    ok[j] = y > hzr + 0.3f;
                    gz[j] = ok[j] ? d.hc * f / (y - hzr) : 0f;
                    gx[j] = (s[2 * j] - d.w / 2f) * gz[j] / f;
                }
                float lane = Math.max(1.6f, d.roadW[i] / 4f);
                for (int dir = 0; dir < 2; dir++) { // each way in its own lane: on the right (or the left where they drive so)
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
                        if (5 * cnt + 5 > buf.length) buf = java.util.Arrays.copyOf(buf, buf.length * 2);
                        buf[5 * cnt] = (d.w / 2f + px0 * f / pz0 - x0) * k;
                        buf[5 * cnt + 1] = (hzr + d.hc * f / pz0) * k;
                        buf[5 * cnt + 2] = px0;
                        buf[5 * cnt + 3] = pz0;
                        cnt++;
                    }
                    flush(runs, buf, cnt);
                }
            }
            float total = 0;
            for (float[] q : runs) total += q[q.length - 1];
            float gap = Math.max(15f, total / 2000f); // a car every 15 m or so per lane (only those in sight are drawn), at most 2,000
            Random r = new Random(seed ^ 0xCA75L);
            tr.runs = runs.toArray(new float[0][]);
            tr.len = new float[tr.runs.length];
            tr.start = new float[tr.runs.length][];
            tr.speed = new float[tr.runs.length][];
            for (int i = 0; i < tr.runs.length; i++) {
                float L = tr.runs[i][tr.runs[i].length - 1];
                tr.len[i] = L;
                float want = L / gap;
                int c = (int) want + (r.nextFloat() < want - (int) want ? 1 : 0);
                tr.start[i] = new float[c];
                tr.speed[i] = new float[c];
                for (int j = 0; j < c; j++) {
                    tr.start[i][j] = r.nextFloat() * L;
                    tr.speed[i][j] = 7f + r.nextFloat() * 6f; // 25 to 47 km/h
                }
            }
            return tr;
        }

        /** Ends a run: its distances along added; kept if it's long enough. Returns 0 (the next run's count). */
        private static int flush(List<float[]> runs, float[] buf, int cnt) {
            if (cnt < 2) return 0;
            float[] q = java.util.Arrays.copyOf(buf, 5 * cnt);
            float acc = 0;
            q[4] = 0;
            for (int i = 1; i < cnt; i++) {
                acc += (float) Math.hypot(q[5 * i + 2] - q[5 * i - 3], q[5 * i + 3] - q[5 * i - 2]);
                q[5 * i + 4] = acc;
            }
            if (acc > 5f) runs.add(q);
            return 0;
        }
    }

    /** The cars at {@code t} seconds (the city's {@code traffic}, or nothing). */
    static void traffic(Canvas c, Traffic tr, float t, Paint p, float px) {
        if (tr == null || tr.lights < 0.05f) return;
        p.setShader(null);
        for (int r = 0; r < tr.runs.length; r++) {
            float[] q = tr.runs[r];
            float L = tr.len[r];
            int np = q.length / 5;
            for (int j = 0; j < tr.start[r].length; j++) {
                float at = (tr.start[r][j] + t * tr.speed[r][j]) % L;
                int lo = 0, hi = np - 1;
                while (hi - lo > 1) {
                    int mid = (lo + hi) >>> 1;
                    if (q[5 * mid + 4] <= at) lo = mid; else hi = mid;
                }
                float s0 = q[5 * lo + 4], s1 = q[5 * hi + 4], u = s1 > s0 ? (at - s0) / (s1 - s0) : 0f;
                float x = q[5 * lo] + (q[5 * hi] - q[5 * lo]) * u, y = q[5 * lo + 1] + (q[5 * hi + 1] - q[5 * lo + 1]) * u;
                int mx = (int) (x * 0.5f), my = (int) (y * 0.5f);
                if (mx < 0 || my < 0 || mx >= tr.mw || my >= tr.mh || tr.mask[my * tr.mw + mx] == 0) continue;
                float z = q[5 * lo + 3] + (q[5 * hi + 3] - q[5 * lo + 3]) * u;
                int col = q[5 * hi + 3] < q[5 * lo + 3] ? HEAD : TAIL; // coming nearer: its headlights
                float rad = Math.max(0.55f, Math.min(1.2f * px, 0.75f * tr.fk / z));
                float a = Math.min(1f, tr.lights * (0.65f + 0.45f * Math.min(1f, 500f / z)));
                // a short streak of light along its way, as a car's lights read at night
                float sx = q[5 * hi] - q[5 * lo], sy = q[5 * hi + 1] - q[5 * lo + 1], sl = (float) Math.hypot(sx, sy);
                float trail = Math.max(rad, Math.min(5f * px, 7f * tr.fk / z)); // about 7 m
                float ex = sl > 0.01f ? x - sx / sl * trail : x, ey = sl > 0.01f ? y - sy / sl * trail : y;
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
}
