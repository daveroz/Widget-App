package app.currencyexchange.widget;

import android.graphics.Bitmap;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;

import java.util.Random;

/**
 * A world clock's background: its city's sky right now. Its colours follow the sun's height there, from the blue of
 * the day through golden hour, the purple and orange of sunset and the blue hour to the night; clouds as many as the
 * city's cloud cover, lit by the same light (white by day, purple with glowing undersides at dusk, grey-blue at
 * night); stars on a clear night; a veil for overcast, rain, snow or fog. A city's clouds and stars always sit in the
 * same places (its own seed), so its sky changes only with the light and the weather. A faint darkening keeps the
 * white time in front easy to read on a bright sky.
 * <p>
 * Since 2.05 a sky comes in layers ({@link Layers}), drawn once per light and weather, from which {@link SkyCell}
 * makes each frame: the stars twinkle, the clouds drift on the wind, the rain falls.
 */
final class Sky {
    private Sky() {}

    /** The sun's heights (degrees) at which the sky's colours below are set; in between, they blend. */
    private static final float[] AT = {-18f, -9f, -5f, -2f, 1f, 5f, 12f, 25f};
    /** The sky at each height: its top, its middle and its horizon. */
    private static final int[][] SKY = {
            {0xFF060A1A, 0xFF0A1230, 0xFF121D42},   // night
            {0xFF0A1230, 0xFF141E48, 0xFF213060},   // the end of dusk
            {0xFF19255A, 0xFF2D3C7A, 0xFF4E5C9C},   // blue hour
            {0xFF2A336E, 0xFF675C9E, 0xFFD07C74},   // dusk: purple, mauve, a rosy horizon
            {0xFF3B4A8E, 0xFF8A76A8, 0xFFF0985A},   // sunset: blue-purple, lilac, orange
            {0xFF3C62A8, 0xFF8A9CC6, 0xFFF0BE86},   // golden hour
            {0xFF2C66B8, 0xFF5C90D2, 0xFFB4D2EC},   // morning, afternoon
            {0xFF235EB8, 0xFF4D8AD6, 0xFF9AC6F0},   // day
    };
    /** The clouds at each height: their tops and their undersides (lit by the low sun at dusk). */
    private static final int[][] CLOUD = {
            {0xFF262D42, 0xFF323A50},
            {0xFF2C3456, 0xFF384064},
            {0xFF48547F, 0xFF5A648E},
            {0xFF6A5D96, 0xFFC07A86},
            {0xFF7B6DA4, 0xFFF09E78},
            {0xFFB6B0C8, 0xFFF2C498},
            {0xFFF0F2F7, 0xFFC8D1DF},
            {0xFFFFFFFF, 0xFFCCD5E3},
    };

    /** A sky's layers and what's needed to make its frames: drawn once per light and weather. */
    static final class Layers {
        int w, h;
        float px, e, cover, glow;
        /** The sky and the low sun's glow along the horizon. */
        Bitmap back;
        /** The clouds, twice as wide as the sky so they can drift and wrap round; null under a clear sky. */
        Bitmap clouds;
        /** Over the clouds: the overcast closing over the sky, the horizon's glow on them; null if neither. */
        Bitmap over;
        /** In front of everything: a fog's veil and the faint darkening that keeps the white text easy to read. */
        Bitmap front;
        /** The colours of the hour: the sky (top, middle, horizon) and the clouds (tops, undersides). */
        int[] sky, cloud;
        boolean rain, snow, fog;
        long seed;
        /** The stars (when the night is clear enough): where, how big, how bright, how fast each twinkles. */
        float[] sx, sy, ss, sa, sf, sp;
        boolean[] sb;
    }

    /**
     * The sky's layers, {@code w} × {@code h} pixels ({@code px} pixels per dp): the sun {@code elevation} degrees
     * above the horizon, {@code cloud} % cloud cover (-1: unknown), WMO weather {@code code} (-1: unknown), the city's
     * {@code seed}.
     */
    static Layers layers(int w, int h, float px, double elevation, int cloud, int code, long seed) {
        return layers(w, h, px, elevation, cloud, code, seed, false, 0L, Float.NaN);
    }

    /**
     * As above, with the sun on the left ({@code sunLeft}, the morning) or the right near the horizon, and, at {@code ms}
     * (0: none), the moon in its phase on a clear night while it's up, {@code localHour} being the city's hour (2.06).
     */
    static Layers layers(int w, int h, float px, double elevation, int cloud, int code, long seed, boolean sunLeft,
                         long ms, float localHour) {
        Layers L = new Layers();
        L.w = w = Math.max(2, w);
        L.h = h = Math.max(2, h);
        L.px = px;
        L.seed = seed;
        float e = (float) Math.max(AT[0], Math.min(AT[AT.length - 1], elevation));
        L.e = e;
        int[] sky = blend(SKY, e), cl = blend(CLOUD, e);
        L.sky = sky;
        L.cloud = cl;
        float cover = cloud < 0 ? 0.15f : Math.max(0f, Math.min(1f, cloud / 100f));
        L.rain = (code >= 51 && code <= 67) || (code >= 80 && code <= 82) || code >= 95;
        L.snow = (code >= 71 && code <= 77) || code == 85 || code == 86;
        L.fog = code == 45 || code == 48;
        if (L.rain || L.snow) cover = Math.max(cover, 0.85f);
        L.cover = cover;

        // the back: the sky, from its top to the horizon at the bottom of the cell, and the low sun's glow
        L.back = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(L.back);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        p.setShader(new LinearGradient(0, 0, 0, h, sky, new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        float glow = (float) Math.exp(-Math.pow((e - 0.5f) / 5.5f, 2));
        L.glow = glow;
        int warm = e < -2f ? 0xFFC8708E : 0xFFFFA05A;
        if (glow > 0.02f) { // warm at sunset, rosy as the blue hour comes on
            p.setShader(new RadialGradient(w * 0.5f, h * 1.08f, Math.max(w, h) * 0.95f,
                    Scene.alpha(warm, 0.42f * glow), warm & 0x00FFFFFF, Shader.TileMode.CLAMP));
            c.drawRect(0, 0, w, h, p);
        }
        p.setShader(null);
        // the sun itself while it's low (behind the clouds, dimmed by them): orange at the horizon, paler as it climbs
        if (e > -1.5f && e < 24f) {
            float sx = (sunLeft ? 0.2f : 0.8f) * w, sy = h * (0.99f - clamp(e / 24f) * 0.66f);
            float vis = 1f - Math.min(0.85f, cover);
            int sun = e < 5f ? Scene.mix(0xFFFF9C55, 0xFFFFE2B0, clamp(e / 5f)) : 0xFFFFF4DE;
            p.setShader(new RadialGradient(sx, sy, 26 * px, Scene.alpha(sun, 0.38f * vis), sun & 0x00FFFFFF, Shader.TileMode.CLAMP));
            c.drawCircle(sx, sy, 26 * px, p);
            p.setShader(new RadialGradient(sx, sy, 8 * px, Scene.alpha(sun, 0.55f * vis), sun & 0x00FFFFFF, Shader.TileMode.CLAMP));
            c.drawCircle(sx, sy, 8 * px, p);
            p.setShader(null);
            p.setColor(Scene.alpha(Scene.mix(sun, 0xFFFFFFFF, 0.45f), 0.95f * vis));
            c.drawCircle(sx, sy, 2.8f * px, p);
        }
        // the moon on a clear night, in its phase (waxing lit on the right), away from the clock's text
        if (ms > 0 && e < -3f && cover < 0.8f && !Float.isNaN(localHour)) {
            double ph = ((ms / 86400000.0 - 10962.76) / 29.530588) % 1.0;
            if (ph < 0) ph += 1;
            float lit = (float) ((1 - Math.cos(2 * Math.PI * ph)) / 2);
            // it crosses the sky about six hours either side of its highest, which comes at noon at the new moon and
            // at midnight at the full: up from the left (east), highest over the middle (above the clock), down right
            double ha = ((localHour - (12 + ph * 24)) % 24 + 36) % 24 - 12;
            float vis = clamp((-3f - e) / 4f) * (1f - cover) * clamp((6.2f - (float) Math.abs(ha)) / 0.8f);
            if (lit > 0.04f && vis > 0.02f) {
                double a = ha / 6 * Math.PI / 2;
                float mr = 2.9f * px, mx = w * (0.5f + 0.4f * (float) Math.sin(a)), my = h * (0.075f + 0.3f * (1 - (float) Math.cos(a)));
                p.setShader(new RadialGradient(mx, my, mr * 5, Scene.alpha(0xFFE8ECF6, 0.16f * vis * lit), 0x00E8ECF6,
                        Shader.TileMode.CLAMP));
                c.drawCircle(mx, my, mr * 5, p);
                p.setShader(null);
                c.saveLayer(mx - mr * 2, my - mr * 2, mx + mr * 2, my + mr * 2, null);
                p.setColor(Scene.alpha(0xFFF1EEE2, 0.95f * vis));
                c.drawCircle(mx, my, mr, p);
                Paint cut = new Paint(Paint.ANTI_ALIAS_FLAG);
                cut.setXfermode(new android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.DST_OUT));
                float off = 2 * mr * lit * (ph < 0.5 ? -1 : 1);
                if (lit < 0.97f) c.drawCircle(mx + off, my, mr, cut);
                c.restore();
            }
        }

        // the stars, on a night clear enough to see them (drawn with each frame, so they can twinkle)
        float stars = clamp((-6f - e) / 6f) * clamp(1f - cover / 0.6f);
        int n = stars > 0f ? Math.round(46 * stars) : 0;
        L.sx = new float[n]; L.sy = new float[n]; L.ss = new float[n]; L.sa = new float[n];
        L.sf = new float[n]; L.sp = new float[n]; L.sb = new boolean[n];
        Random sr = new Random(seed ^ 0x5EEDL);
        for (int i = 0; i < n; i++) {
            L.sx[i] = sr.nextFloat() * w;
            L.sy[i] = sr.nextFloat() * h * 0.92f;
            L.ss[i] = (0.35f + sr.nextFloat() * 0.6f) * px;
            L.sa[i] = (0.3f + sr.nextFloat() * 0.7f) * stars;
            L.sb[i] = sr.nextFloat() < 0.08f; // a few brighter ones, with a faint glow round them
            L.sf[i] = 0.25f + sr.nextFloat() * 0.9f; // twinkles per second
            L.sp[i] = sr.nextFloat() * 6.2832f;
        }

        // the clouds: soft heaps of puffs, as many as the cover, in the light of the hour, on a strip twice as wide
        // as the sky (each puff also drawn a strip's width either side, so the strip wraps round without a seam)
        if (cover > 0.04f) {
            int sw = 2 * w;
            L.clouds = Bitmap.createBitmap(sw, h, Bitmap.Config.ARGB_8888);
            Canvas cc = new Canvas(L.clouds);
            Random r = new Random(seed);
            int puffsN = 2 * (1 + Math.round(cover * 13));
            Paint cp = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
            cp.setShader(new LinearGradient(0, 0, 0, h, cl[0], cl[1], Shader.TileMode.CLAMP));
            cp.setMaskFilter(new BlurMaskFilter(Math.max(1.5f, h * 0.045f), BlurMaskFilter.Blur.NORMAL));
            cc.saveLayerAlpha(new RectF(0, 0, sw, h), Math.round(255 * (0.38f + 0.47f * cover)));
            if (cover > 0.45f) { // a heavier sky: long bands of cloud under the heaps, a shade darker, for its texture
                Paint bp = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
                bp.setMaskFilter(new BlurMaskFilter(Math.max(2f, h * 0.06f), BlurMaskFilter.Blur.NORMAL));
                int bands = 2 * (3 + Math.round((cover - 0.45f) * 8));
                for (int i = 0; i < bands; i++) {
                    float y = h * (0.05f + r.nextFloat() * 0.9f), bh = h * (0.07f + r.nextFloat() * 0.09f);
                    float x = r.nextFloat() * sw, bw = w * (0.9f + r.nextFloat() * 0.8f);
                    bp.setColor(Scene.mix(Scene.mix(cl[0], cl[1], y / h), sky[1], 0.25f + 0.3f * r.nextFloat()));
                    for (int k = -1; k <= 1; k++) cc.drawOval(x + k * sw, y - bh / 2, x + bw + k * sw, y + bh / 2, bp);
                }
            }
            for (int i = 0; i < puffsN; i++) {
                float cw = w * (0.4f + r.nextFloat() * 0.45f) * (0.75f + 0.5f * cover);
                float ch = cw * (0.2f + r.nextFloat() * 0.14f);
                float cx = r.nextFloat() * sw, cy = h * (0.08f + r.nextFloat() * 0.7f);
                int puffs = 5 + r.nextInt(4);
                for (int k = 0; k < puffs; k++) {
                    float t = k / (float) (puffs - 1);
                    float rx = cw * (0.16f + r.nextFloat() * 0.12f);
                    float ry = ch * (0.55f + r.nextFloat() * 0.45f) * (1f - 0.35f * Math.abs(t - 0.5f) * 2f);
                    float x = cx + (t - 0.5f) * cw, y = cy - ry * 0.35f * (1f - Math.abs(t - 0.5f) * 2f);
                    for (int j = -1; j <= 1; j++) cc.drawOval(x - rx + j * sw, y - ry, x + rx + j * sw, y + ry, cp);
                }
            }
            cc.restore();
            boolean overcast = cover > 0.6f, lit = glow > 0.02f;
            if (overcast || lit) {
                L.over = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                Canvas oc = new Canvas(L.over);
                if (overcast) { // the clouds close over the sky
                    p.setColor(Scene.alpha(Scene.mix(cl[0], cl[1], 0.5f), 0.38f * (cover - 0.6f) / 0.4f));
                    oc.drawRect(0, 0, w, h, p);
                }
                if (lit) { // the horizon's glow on the clouds too
                    p.setShader(new RadialGradient(w * 0.5f, h * 1.08f, Math.max(w, h) * 0.8f,
                            Scene.alpha(warm, 0.16f * glow * cover), warm & 0x00FFFFFF, Shader.TileMode.CLAMP));
                    oc.drawRect(0, 0, w, h, p);
                    p.setShader(null);
                }
            }
        }

        // in front: a fog's pale veil, and a faint darkening, more on a bright sky, so the white time in front reads
        L.front = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas fc = new Canvas(L.front);
        if (L.fog) {
            p.setShader(new LinearGradient(0, 0, 0, h, Scene.alpha(Scene.mix(cl[0], 0xFFFFFFFF, 0.3f), 0.2f),
                    Scene.alpha(Scene.mix(cl[1], 0xFFFFFFFF, 0.3f), 0.5f), Shader.TileMode.CLAMP));
            fc.drawRect(0, 0, w, h, p);
            p.setShader(null);
        }
        // a very soft shade behind the clock's text, fading out well before the edges (the corner's landmark and
        // floodlights stay as bright as they are)
        p.setShader(new RadialGradient(w * 0.5f, h * 0.42f, Math.max(w, h) * 0.62f, 0x30000000, 0x00000000,
                Shader.TileMode.CLAMP));
        fc.drawRect(0, 0, w, h, p);
        p.setShader(null);
        float lum = Scene.luminance(sky[2]);
        p.setColor(Scene.alpha(0xFF000000, Math.max(0.1f, Math.min(0.36f, 0.12f + (lum - 0.3f) * 0.55f))));
        fc.drawRect(0, 0, w, h, p);
        if (L.rain) fc.drawColor(0x1A000000); // a darker sky under the rain
        return L;
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

    /** The rain falling or the snow drifting down at {@code t} seconds. */
    static void weather(Canvas c, Layers L, float t, Paint p) {
        int w = L.w, h = L.h;
        if (L.rain) {
            Random r = new Random(L.seed ^ 0x7A1L);
            p.setColor(Scene.alpha(Scene.mix(L.cloud[1], 0xFFFFFFFF, 0.4f), 0.18f));
            p.setStrokeWidth(Math.max(1f, 0.5f * L.px));
            for (int i = 0; i < 26; i++) {
                float len = h * (0.08f + r.nextFloat() * 0.08f), speed = h * (1.1f + r.nextFloat() * 0.5f);
                float y = (r.nextFloat() * h + t * speed) % (h + len) - len;
                float x = (r.nextFloat() * (w + h * 0.3f) - (y + len) * 0.3f + w * 2f) % (w + h * 0.3f);
                c.drawLine(x, y, x - len * 0.3f, y + len, p);
            }
        } else if (L.snow) {
            Random r = new Random(L.seed ^ 0x5A0L);
            p.setColor(0xB3FFFFFF);
            for (int i = 0; i < 30; i++) {
                float size = (0.5f + r.nextFloat() * 0.6f) * L.px, speed = h * (0.08f + r.nextFloat() * 0.06f);
                float y = (r.nextFloat() * h + t * speed) % (h + 4 * size) - 2 * size;
                float x = r.nextFloat() * w + (float) Math.sin(t * 0.7f + i) * 3f * L.px;
                c.drawCircle(x, y, size, p);
            }
        }
    }

    /** The sky as one still picture (the layers at one moment): what 2.04 drew. */
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

    /** The colours set at each height, blended for the sun at {@code e} degrees. */
    private static int[] blend(int[][] set, float e) {
        int i = 0;
        while (i < AT.length - 2 && e > AT[i + 1]) i++;
        float t = clamp((e - AT[i]) / (AT[i + 1] - AT[i]));
        int[] out = new int[set[i].length];
        for (int k = 0; k < out.length; k++) out[k] = Scene.mix(set[i][k], set[i + 1][k], t);
        return out;
    }

    static float clamp(float v) {
        return Math.max(0f, Math.min(1f, v));
    }
}
