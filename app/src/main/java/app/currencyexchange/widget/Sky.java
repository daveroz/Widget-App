package app.currencyexchange.widget;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;

import java.util.Random;

/**
 * A world clock's background: its city's sky right now, painted as an illustration (2.15, after the references): a
 * smooth wash of the hour's colours (dawn's blue, lavender and pink; sunrise's peach and gold; the midday blue;
 * sunset's orange and red; dusk's indigo, purple and magenta; the blue hour; the deep blue night), crisp stylised
 * clouds lit by the same light (glowing undersides when the sun is low, white heaps at midday, navy at night), a big
 * soft sun where the sun really is, the moon likewise, stars on a clear night, and the weather: heavy cloud and long
 * streaks of rain, snow, fog. A city's clouds and stars always sit in the same places (its own seed).
 * <p>
 * A sky comes in layers ({@link Layers}), drawn once per light and weather, from which {@link SkyCell} makes each frame:
 * the stars twinkle, the clouds drift on the wind, the rain falls.
 */
final class Sky {
    private Sky() {}

    /** How far the sky is zoomed in: its stars and rain drawn this much bigger. */
    static final float ZOOM = 1.2f;

    /**
     * The sun's heights (degrees) at which the colours are set ({@link Palette}, since 2.29 after the reference video's
     * colour logic; before, the sky had tables of its own here); in between, they blend.
     */
    private static final float[] AT = Palette.AT;

    static final class Layers {
        int w, h;
        float px, e, cover, glow;
        /** The sky, the sun or moon, the glow round them. */
        Bitmap back;
        /** The clouds, twice as wide as the sky so they can drift and wrap round; null under a clear sky. */
        Bitmap clouds;
        /** Over the clouds: the overcast closing over the sky; null if none. */
        Bitmap over;
        /** In front of everything: rain's mist and fog, and the soft shade that keeps the white text easy to read. */
        Bitmap front;
        /** The colours of the hour: the sky (top, middle, horizon) and the clouds (lit, in shade). */
        int[] sky, cloud;
        boolean rain, snow, fog;
        long seed;
        /** How much of the morning's colours there are (0: the evening's, 1: the morning's), for the city's (2.29). */
        float morning;
        /** The stars (when the night is clear enough): where, how big, how bright, how fast each twinkles. */
        float[] sx, sy, ss, sa, sf, sp;
        boolean[] sb;
    }

    static Layers layers(int w, int h, float px, double elevation, int cloud, int code, long seed) {
        return layers(w, h, px, elevation, cloud, code, seed, false, null, null);
    }

    static Layers layers(int w, int h, float px, double elevation, int cloud, int code, long seed, boolean sunLeft,
                         double[] moon) {
        return layers(w, h, px, elevation, cloud, code, seed, sunLeft, moon, null);
    }

    /**
     * The sky's layers, {@code w} × {@code h} pixels ({@code px} pixels per dp): the sun {@code elevation} degrees up, in
     * the morning ({@code sunLeft}) or the evening; {@code cloud} % cloud cover and WMO weather {@code code} (-1:
     * unknown); the city's {@code seed}; the moon and the sun where they really are ({@link SunCalc#moon},
     * {@link SunCalc#sun}: altitude and azimuth first; null when unknown, the sun then placed by its height alone).
     */
    static Layers layers(int w, int h, float px, double elevation, int cloud, int code, long seed, boolean sunLeft,
                         double[] moon, double[] sun) {
        Layers L = new Layers();
        L.w = w = Math.max(2, w);
        L.h = h = Math.max(2, h);
        L.px = px;
        L.seed = seed;
        float e = (float) Math.max(AT[0], Math.min(AT[AT.length - 1], elevation));
        L.e = e;
        // the morning's colours handing over to the evening's as the sun crosses the south (2.17: by its real direction,
        // half and half at noon, so there's no step even on a short winter day whose sun stays low)
        float morning = sun != null ? clamp(0.5f - (float) (sun[1] / (Math.PI / 3))) : (sunLeft ? 1f : 0f);
        L.morning = morning;
        // the hour's colours, after the video's colour logic (2.29, Palette)
        int[] sky = Palette.sky(e, morning);
        int[] clouds = Palette.cloud(e, morning);
        int lit = clouds[0], shade = clouds[1];
        float cover = cloud < 0 ? 0.25f : Math.max(0f, Math.min(1f, cloud / 100f));
        L.rain = (code >= 51 && code <= 67) || (code >= 80 && code <= 82) || code >= 95;
        L.snow = (code >= 71 && code <= 77) || code == 85 || code == 86;
        L.fog = code == 45 || code == 48;
        // the weather by the time of day (2.16): how much day and how much night there is in the light
        float day = clamp((e - 2f) / 10f), night = clamp((-2f - e) / 10f);
        if (L.rain) {
            cover = Math.max(cover, 0.85f);
            // the sky keeps its hour's glow behind the gaps at dawn and dusk; grey by day, a dark navy at night; the
            // clouds heavy over it, their edges warm when the sun is low, grey by day
            int grey = Scene.mix(Scene.mix(0xFF4A4C68, 0xFF6E7488, day), 0xFF141C36, night);
            sky = new int[]{Scene.mix(sky[0], grey, 0.35f + 0.3f * day), Scene.mix(sky[1], grey, 0.3f + 0.35f * day),
                    Scene.mix(sky[2], grey, 0.15f + 0.45f * day)};
            shade = Scene.mix(shade, Scene.mix(Scene.mix(0xFF3A3A58, 0xFF5A6074, day), 0xFF161E38, night), 0.75f);
            lit = Scene.mix(lit, Scene.mix(lit, 0xFFB4B8C6, day), 0.6f);
        } else if (L.snow) {
            cover = Math.max(cover, 0.85f);
            // a paler, softer sky: pale grey by day, mauve and rose at dawn and dusk, a soft blue at night; gentle clouds
            int snowy = Scene.mix(Scene.mix(0xFFB8A8C8, 0xFFC8CED8, day), 0xFF223060, night);
            sky = new int[]{Scene.mix(sky[0], snowy, 0.35f + 0.3f * day), Scene.mix(sky[1], snowy, 0.4f + 0.3f * day),
                    Scene.mix(sky[2], snowy, 0.3f + 0.3f * day)};
            shade = Scene.mix(Scene.mix(sky[1], snowy, 0.5f), 0xFF000000, 0.08f);
            lit = Scene.mix(lit, 0xFFFFFFFF, 0.3f);
        }
        L.sky = sky;
        L.cloud = new int[]{lit, shade};
        L.cover = cover;

        // the back: a smooth wash from the top down to the bright band along the skyline
        L.back = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(L.back);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        // its warm band rises behind the city, as the video's pink does (2.29); since 2.30 the horizon is the video's,
        // at 64% of the way down, the middle a little over halfway to it
        float hz = CityScene.HORIZON;
        p.setShader(new LinearGradient(0, 0, 0, h, new int[]{sky[0], sky[1], sky[2], sky[2]},
                new float[]{0f, hz * 0.56f, hz - 0.03f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        p.setShader(null);
        float glow = (float) Math.exp(-Math.pow((e - 0.5f) / 6f, 2));
        L.glow = glow;

        // the sun: a big soft disc (a seventh of the cell's width across... its radius), where it really is
        float sunR = 0.15f * w;
        float sAlt, sAz;
        if (sun != null) {
            sAlt = (float) Math.toDegrees(sun[0]);
            sAz = (float) sun[1];
        } else {
            sAlt = e;
            sAz = (float) (sunLeft ? -Math.PI / 3 : Math.PI / 3);
        }
        if (sAlt > -4f) {
            float sx = w * (0.5f + 0.42f * (float) Math.sin(sAz)), sy = h * (hz - (hz - 0.08f) * clamp(sAlt / 60f));
            float vis = (1f - 0.75f * cover) * clamp((sAlt + 4f) / 3f);
            int edge = sAlt < 0 ? 0xFFFFB896 : sAlt < 4 ? (sunLeft ? 0xFFFFE08A : 0xFFFFD27A) : sAlt < 15 ? 0xFFFFEFB8 : 0xFFFFF8E8;
            int core = Scene.mix(edge, 0xFFFFFFFF, 0.35f);
            // the sky brightening round it, then its halo, then the disc, lighter at its middle
            p.setShader(new RadialGradient(sx, sy, sunR * 4.2f, Scene.alpha(edge, 0.3f * vis), edge & 0x00FFFFFF, Shader.TileMode.CLAMP));
            c.drawCircle(sx, sy, sunR * 4.2f, p);
            p.setShader(new RadialGradient(sx, sy, sunR * 1.7f, new int[]{Scene.alpha(edge, 0.5f * vis), Scene.alpha(edge, 0.18f * vis),
                    edge & 0x00FFFFFF}, new float[]{0.55f, 0.75f, 1f}, Shader.TileMode.CLAMP));
            c.drawCircle(sx, sy, sunR * 1.7f, p);
            p.setShader(new RadialGradient(sx, sy, sunR, new int[]{Scene.alpha(core, 0.97f * vis), Scene.alpha(edge, 0.95f * vis),
                    Scene.alpha(edge, 0.9f * vis)}, new float[]{0f, 0.8f, 1f}, Shader.TileMode.CLAMP));
            c.drawCircle(sx, sy, sunR, p);
            p.setShader(null);
        }

        // the moon, as big as the sun, where it really is: lit as much as it is, its bright side towards the sun, the
        // dark part faintly there, a few darker seas on it; pale by day, bright at night, dimmed by clouds
        float moonX = 0, moonY = 0, moonR = 0; // where it is, for the stars to keep behind it (2.27)
        if (moon != null && cover < 0.9f) {
            float altD = (float) Math.toDegrees(moon[0]), litF = (float) moon[2];
            float dark = clamp((1f - e) / 7f);
            float vis = clamp((altD + 0.5f) / 1.5f) * (1f - 0.8f * cover) * (0.35f + 0.62f * dark);
            if (litF > 0.03f && vis > 0.02f) {
                float mx = w * (0.5f + 0.42f * (float) Math.sin(moon[1])), my = h * (hz - (hz - 0.08f) * clamp(altD / 60f));
                float mr = 0.11f * w;
                moonX = mx;
                moonY = my;
                moonR = mr;
                int pale = 0xFFF1EEE2;
                if (dark > 0.3f) {
                    p.setShader(new RadialGradient(mx, my, mr * 3.2f, Scene.alpha(0xFFDCE6FF, 0.22f * vis * litF), 0x00DCE6FF,
                            Shader.TileMode.CLAMP));
                    c.drawCircle(mx, my, mr * 3.2f, p);
                    p.setShader(null);
                }
                c.saveLayer(mx - mr * 1.5f, my - mr * 1.5f, mx + mr * 1.5f, my + mr * 1.5f, null);
                p.setColor(Scene.alpha(pale, 0.12f * vis * dark));
                c.drawCircle(mx, my, mr, p);
                double zen = moon[3];
                c.save();
                c.translate(mx, my);
                c.rotate((float) Math.toDegrees(Math.atan2(-Math.cos(zen), -Math.sin(zen)))); // its bright limb along +x
                Path litPart = new Path();
                float k = mr * (1 - 2 * litF);
                for (int i = 0; i <= 24; i++) {
                    double t = Math.toRadians(-90 + i * 7.5);
                    float x = mr * (float) Math.cos(t), y = mr * (float) Math.sin(t);
                    if (i == 0) litPart.moveTo(x, y); else litPart.lineTo(x, y);
                }
                for (int i = 24; i >= 0; i--) {
                    double t = Math.toRadians(-90 + i * 7.5);
                    litPart.lineTo(k * (float) Math.cos(t), mr * (float) Math.sin(t));
                }
                litPart.close();
                p.setShader(new RadialGradient(-mr * 0.25f, -mr * 0.25f, mr * 1.3f, Scene.alpha(0xFFFFFDF4, 0.97f * vis),
                        Scene.alpha(pale, 0.92f * vis), Shader.TileMode.CLAMP));
                c.drawPath(litPart, p);
                p.setShader(null);
                c.restore();
                Paint sea = new Paint(Paint.ANTI_ALIAS_FLAG);
                sea.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP));
                sea.setColor(Scene.alpha(0xFFB8B8C4, 0.45f));
                Random mr0 = new Random(7);
                for (int i = 0; i < 6; i++) {
                    float sx = mx + (mr0.nextFloat() - 0.5f) * mr * 1.1f, sy = my + (mr0.nextFloat() - 0.5f) * mr * 1.1f;
                    c.drawCircle(sx, sy, mr * (0.12f + mr0.nextFloat() * 0.16f), sea);
                }
                c.restore();
            }
        }

        // the stars, on a night clear enough to see them (drawn with each frame, so they can twinkle)
        float stars = clamp((-6f - e) / 6f) * clamp(1f - cover / 0.6f);
        int n = stars > 0f ? Math.round(38 * stars) : 0;
        L.sx = new float[n]; L.sy = new float[n]; L.ss = new float[n]; L.sa = new float[n];
        L.sf = new float[n]; L.sp = new float[n]; L.sb = new boolean[n];
        Random sr = new Random(seed ^ 0x5EEDL);
        for (int i = 0; i < n; i++) {
            L.sx[i] = sr.nextFloat() * w;
            L.sy[i] = sr.nextFloat() * h * (hz - 0.06f);
            // stars are behind the moon (2.27): none on its disc or just round it, where its light hides them
            for (int tries = 0; moonR > 0 && tries < 8
                    && Math.hypot(L.sx[i] - moonX, L.sy[i] - moonY) < moonR * 1.35f; tries++) {
                L.sx[i] = sr.nextFloat() * w;
                L.sy[i] = sr.nextFloat() * h * (hz - 0.06f);
            }
            L.ss[i] = (0.35f + sr.nextFloat() * 0.6f) * px * 1.15f;
            L.sa[i] = (0.3f + sr.nextFloat() * 0.7f) * stars;
            if (moonR > 0 && Math.hypot(L.sx[i] - moonX, L.sy[i] - moonY) < moonR * 1.35f) L.sa[i] = 0; // never over it
            L.sb[i] = sr.nextFloat() < 0.08f;
            L.sf[i] = 0.25f + sr.nextFloat() * 0.9f;
            L.sp[i] = sr.nextFloat() * 6.2832f;
        }

        // the clouds, crisp as painted: on a strip twice the sky's width (each drawn a strip's width either side too, so
        // it wraps round without a seam); lit along their undersides when the sun is low, their tops at midday
        if (cover > 0.04f) {
            int sw = 2 * w;
            L.clouds = Bitmap.createBitmap(sw, h, Bitmap.Config.ARGB_8888);
            Canvas cc = new Canvas(L.clouds);
            // the long bands of a low sun fading into midday's heaps as the sun climbs from 6 to 14 degrees (2.17), each
            // set always in its own places, so neither jumps while they cross
            float heapy = L.rain || L.snow ? 0f : clamp((e - 6f) / 8f);
            int alpha = Math.round(255 * (0.85f + 0.13f * cover));
            if (heapy < 1f) {
                java.util.List<float[]> ovals = new java.util.ArrayList<>(), clips = new java.util.ArrayList<>();
                bands(new Random(seed), ovals, clips, sw, w, h, cover);
                paintClouds(cc, ovals, clips, sw, h, lit, shade, sky[0], false, Math.round(alpha * (1f - heapy)), 1.5f * px);
            }
            if (heapy > 0f) {
                java.util.List<float[]> ovals = new java.util.ArrayList<>(), clips = new java.util.ArrayList<>();
                heaps(new Random(seed ^ 0xC10D5L), ovals, clips, sw, w, h, cover);
                paintClouds(cc, ovals, clips, sw, h, lit, shade, sky[0], true, Math.round(alpha * heapy), 1.4f * px);
            }
            if (cover > 0.6f) { // overcast: the clouds close over the sky
                L.over = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                Canvas oc = new Canvas(L.over);
                float k = (cover - 0.6f) / 0.4f * (L.rain || L.snow ? 0.6f : 1f);
                p.setShader(new LinearGradient(0, 0, 0, h * 0.8f, Scene.alpha(shade, 0.45f * k), Scene.alpha(shade, 0.1f * k),
                        Shader.TileMode.CLAMP));
                oc.drawRect(0, 0, w, h, p);
                p.setShader(null);
            }
        }

        // in front: rain's and fog's mist low over the city, and the soft shade behind the clock's text
        L.front = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas fc = new Canvas(L.front);
        if (L.fog || L.rain || L.snow) {
            int mist = Scene.mix(sky[2], L.snow ? 0xFFE8ECF4 : 0xFFB8C0D8, 0.4f);
            p.setShader(new LinearGradient(0, h * 0.6f, 0, h, mist & 0x00FFFFFF, Scene.alpha(mist, L.fog ? 0.55f : L.snow ? 0.38f : 0.32f),
                    Shader.TileMode.CLAMP));
            fc.drawRect(0, h * 0.6f, w, h, p);
            p.setShader(null);
        }
        p.setShader(new RadialGradient(w * 0.5f, h * 0.5f, Math.max(w, h) * 0.62f, 0x44000000, 0x00000000, Shader.TileMode.CLAMP));
        fc.drawRect(0, 0, w, h, p);
        p.setShader(null);
        return L;
    }

    /** Midday's cumulus heaps (lumps with flat bases) and a few thin streaks high up; a null starts each cloud. */
    private static void heaps(Random r, java.util.List<float[]> ovals, java.util.List<float[]> clips, int sw, int w, int h,
                              float cover) {
        int count = 2 * (1 + Math.round(cover * 5));
        for (int i = 0; i < count; i++) {
            ovals.add(null);
            clips.add(null);
            float cx = r.nextFloat() * sw, base = h * (0.22f + r.nextFloat() * 0.38f), size = w * (0.16f + r.nextFloat() * 0.16f);
            int puffs = 6 + r.nextInt(4);
            for (int k = 0; k < puffs; k++) {
                float px0 = cx + (r.nextFloat() - 0.5f) * size * 1.6f, rad = size * (0.22f + r.nextFloat() * 0.26f);
                float py0 = base - rad * (0.35f + r.nextFloat() * 0.5f) - (1 - Math.abs(px0 - cx) / size) * size * 0.25f;
                ovals.add(new float[]{px0 - rad, py0 - rad, px0 + rad, py0 + rad});
                clips.add(new float[]{base});
            }
        }
        int wisps = 2 + r.nextInt(3);
        for (int i = 0; i < wisps; i++) {
            ovals.add(null);
            clips.add(null);
            float cx = r.nextFloat() * sw, cy = h * (0.08f + r.nextFloat() * 0.18f), bw = w * (0.3f + r.nextFloat() * 0.4f);
            ovals.add(new float[]{cx - bw / 2, cy - h * 0.012f, cx + bw / 2, cy + h * 0.012f});
            clips.add(null);
        }
    }

    /** A low sun's long bands of cloud, lumps of all sizes scalloped on top (heavier in rain); a null starts each. */
    private static void bands(Random r, java.util.List<float[]> ovals, java.util.List<float[]> clips, int sw, int w, int h,
                              float cover) {
        boolean heavy = cover > 0.7f;
        int bands = 2 * (2 + Math.round(cover * (heavy ? 11 : 6)));
        for (int i = 0; i < bands; i++) {
            ovals.add(null);
            clips.add(null);
            float cx = r.nextFloat() * sw, cy = h * ((heavy ? 0f : 0.06f) + r.nextFloat() * (heavy ? 0.7f : 0.5f));
            float bw = w * (0.4f + r.nextFloat() * 0.55f) * (heavy ? 1.5f : 1f);
            float bh = h * (0.04f + r.nextFloat() * 0.035f) * (heavy ? 2.2f : 1f);
            int bits = 8 + r.nextInt(6);
            for (int k = 0; k < bits; k++) {
                float t = k / (float) (bits - 1);
                float x = cx + (t - 0.5f) * bw + (r.nextFloat() - 0.5f) * bw * 0.06f;
                float rx = bw * (0.06f + r.nextFloat() * 0.08f);
                float ry = bh * (0.55f + r.nextFloat() * 0.75f) * (1f - 0.55f * Math.abs(t - 0.5f));
                float y = cy + (float) Math.sin(t * 3 + i) * bh * 0.35f - ry * 0.25f;
                ovals.add(new float[]{x - rx, y - ry, x + rx, y + ry * 0.5f});
                clips.add(null);
            }
        }
    }

    /**
     * Paints the clouds one by one, from the highest down, each keeping its own lit edge over the ones behind it: its
     * body in the lit colour, then its shade shifted off the lit edge ({@code rim} pixels: up when the sun is low, down
     * for midday's {@code heaps}), at {@code alpha}.
     */
    private static void paintClouds(Canvas cc, java.util.List<float[]> ovals, java.util.List<float[]> clips, int sw, int h,
                                    int lit, int shade, int top, boolean heaps, int alpha, float rim) {
        if (alpha <= 0) return;
        Paint body = new Paint(Paint.ANTI_ALIAS_FLAG);
        body.setColor(lit);
        Paint dark = new Paint(Paint.ANTI_ALIAS_FLAG);
        dark.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP));
        dark.setShader(new LinearGradient(0, 0, 0, h, Scene.mix(shade, top, 0.15f), Scene.mix(shade, lit, heaps ? 0.15f : 0.05f),
                Shader.TileMode.CLAMP));
        java.util.List<int[]> clouds = new java.util.ArrayList<>();
        for (int i = 0; i < ovals.size(); i++) {
            if (ovals.get(i) == null) { clouds.add(new int[]{i + 1, i}); continue; }
            clouds.get(clouds.size() - 1)[1] = i;
        }
        java.util.List<float[]> order = new java.util.ArrayList<>();
        for (int k = 0; k < clouds.size(); k++) {
            float low = 0;
            for (int i = clouds.get(k)[0]; i <= clouds.get(k)[1]; i++) low = Math.max(low, ovals.get(i)[3]);
            order.add(new float[]{k, low});
        }
        java.util.Collections.sort(order, (a, b) -> Float.compare(a[1], b[1]));
        for (float[] o : order) {
            int[] cl = clouds.get((int) o[0]);
            if (cl[1] < cl[0]) continue;
            java.util.List<float[]> ov = ovals.subList(cl[0], cl[1] + 1), cp = clips.subList(cl[0], cl[1] + 1);
            cc.saveLayerAlpha(new RectF(0, 0, sw, h), alpha);
            drawOvals(cc, ov, cp, body, sw, 0);
            drawOvals(cc, ov, cp, dark, sw, heaps ? rim : -rim);
            cc.restore();
        }
    }

    /** The cloud lumps, each clipped to its heap's flat base when it has one, shifted {@code dy} down. */
    private static void drawOvals(Canvas c, java.util.List<float[]> ovals, java.util.List<float[]> clips, Paint p, int sw, float dy) {
        for (int i = 0; i < ovals.size(); i++) {
            float[] o = ovals.get(i), clip = clips.get(i);
            for (int k = -1; k <= 1; k++) {
                if (clip != null) {
                    c.save();
                    c.clipRect(o[0] + k * sw - 2, o[1] + dy - 2, o[2] + k * sw + 2, clip[0] + dy);
                }
                c.drawOval(o[0] + k * sw, o[1] + dy, o[2] + k * sw, o[3] + dy, p);
                if (clip != null) c.restore();
            }
        }
    }

    /** The stars at {@code t} seconds, each twinkling at its own pace. */
    static void stars(Canvas c, Layers L, float t, Paint p) {
        for (int i = 0; i < L.sx.length; i++) {
            float tw = 0.62f + 0.38f * (float) Math.sin(t * 6.2832f * L.sf[i] + L.sp[i]);
            float a = L.sa[i] * tw, size = L.ss[i];
            if (L.sb[i]) {
                p.setShader(new RadialGradient(L.sx[i], L.sy[i], size * 3.5f, Scene.alpha(0xFFDCE6FF, 0.35f * a),
                        0x00DCE6FF, Shader.TileMode.CLAMP));
                c.drawCircle(L.sx[i], L.sy[i], size * 3.5f, p);
                p.setShader(null);
                size *= 1.3f;
            }
            p.setColor(Scene.alpha(0xFFF4F7FF, a));
            c.drawCircle(L.sx[i], L.sy[i], size, p);
        }
    }

    /** The clouds at {@code t} seconds, drifted {@code speed} pixels a second (wrapping round their strip). */
    static void clouds(Canvas c, Layers L, float t, float speed, Paint p) {
        if (L.clouds == null) return;
        int sw = L.clouds.getWidth();
        float off = (float) ((t * (double) speed) % sw);
        if (off < 0) off += sw;
        c.drawBitmap(L.clouds, -off, 0, p);
        c.drawBitmap(L.clouds, sw - off, 0, p);
        if (L.over != null) c.drawBitmap(L.over, 0, 0, p);
    }

    /**
     * The rain falling in long, thin, slanting streaks (pale by day, faintly lit by the city at night), or the snow
     * drifting down at two depths (small, slow flakes far off; bigger, quicker ones near), at {@code t} seconds.
     */
    static void weather(Canvas c, Layers L, float t, Paint p) {
        int w = L.w, h = L.h;
        float night = clamp((-2f - L.e) / 10f);
        if (L.rain) {
            Random r = new Random(L.seed ^ 0x7A1L);
            int streak = Scene.mix(Scene.mix(L.sky[2], 0xFFFFFFFF, 0.5f), 0xFFFFD8A8, 0.35f * night);
            p.setColor(Scene.alpha(streak, 0.3f - 0.06f * night));
            p.setStrokeWidth(Math.max(1f, 0.45f * L.px));
            for (int i = 0; i < 48; i++) {
                float len = h * (0.18f + r.nextFloat() * 0.25f), speed = h * (1.6f + r.nextFloat() * 0.8f);
                float y = (r.nextFloat() * h + t * speed) % (h + len) - len;
                float x = (r.nextFloat() * (w + h * 0.3f) - (y + len) * 0.18f + w * 2f) % (w + h * 0.3f);
                c.drawLine(x, y, x - len * 0.18f, y + len, p);
            }
        } else if (L.snow) {
            Random r = new Random(L.seed ^ 0x5A0L);
            int flake = Scene.mix(0xFFFFFFFF, 0xFFDCE6FF, night);
            for (int i = 0; i < 64; i++) {
                boolean near = i % 3 == 0;
                float size = (near ? 1.1f + r.nextFloat() * 0.7f : 0.45f + r.nextFloat() * 0.45f) * L.px;
                float speed = h * (near ? 0.16f + r.nextFloat() * 0.08f : 0.07f + r.nextFloat() * 0.05f);
                float y = (r.nextFloat() * h + t * speed) % (h + 4 * size) - 2 * size;
                float x = r.nextFloat() * w + (float) Math.sin(t * (near ? 0.9f : 0.6f) + i * 1.7f) * (near ? 5f : 3f) * L.px;
                p.setColor(Scene.alpha(flake, near ? 0.92f : 0.6f + 0.25f * r.nextFloat()));
                c.drawCircle(x, y, size, p);
            }
        }
    }

    /** The sky as one still picture (the layers at one moment). */
    static Bitmap draw(int w, int h, float px, double elevation, int cloud, int code, long seed) {
        Layers L = layers(w, h, px, elevation, cloud, code, seed);
        Bitmap b = L.back.copy(Bitmap.Config.ARGB_8888, true);
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        stars(c, L, 0f, p);
        clouds(c, L, 0f, 0f, p);
        weather(c, L, 0f, p);
        c.drawBitmap(L.front, 0, 0, p);
        return b;
    }

    static float clamp(float v) {
        return Math.max(0f, Math.min(1f, v));
    }
}
