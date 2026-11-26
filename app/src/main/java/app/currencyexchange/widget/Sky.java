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
        /** The sun on screen (2.31): where, how big, how much of it shows (0 when it's down or hidden); for the flare. */
        float sunX, sunY, sunR, sunVis;
        /** The lens flare (2.31): ghosts along the line from the sun through the middle, a halo, a streak; null if none. */
        Bitmap flare;
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
            int grey = Scene.mix(Scene.mix(0xFF414468, 0xFF5F749E, day), 0xFF0B152D, night); // 2.33: steel blue, not grey
            sky = new int[]{Scene.mix(sky[0], grey, 0.35f + 0.3f * day), Scene.mix(sky[1], grey, 0.3f + 0.35f * day),
                    Scene.mix(sky[2], grey, 0.15f + 0.45f * day)};
            shade = Scene.mix(shade, Scene.mix(Scene.mix(0xFF3A3A58, 0xFF5A6074, day), 0xFF161E38, night), 0.75f);
            lit = Scene.mix(lit, Scene.mix(lit, 0xFF9DABC5, day), 0.6f);
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

        // the sun (2.31, after the references): a soft-edged disc, white-gold and small high up, growing and turning a
        // pale yellow as it sets, in a warm glow that reddens round it low down, with a long glow along the horizon; no
        // rays. Sized by the cell's shorter side (so a wide clock's sun isn't huge)
        float S = Math.min(w, h * 1.4f);
        // its height is always the light's (so the sky and the sun never disagree); where it stands across, the real one's
        float sAlt = e, sAz = sun != null ? (float) sun[1] : (float) (sunLeft ? -Math.PI / 3 : Math.PI / 3);
        if (sAlt > -4f) {
            float low = 1f - clamp((sAlt - 1f) / 11f); // 1 at the horizon, 0 from 12 degrees up
            float sunR = S * (0.042f + 0.05f * low);
            float sx = w * (0.5f + 0.42f * (float) Math.sin(sAz)), sy = h * (hz - (hz - 0.08f) * clamp(sAlt / 60f));
            float vis = (1f - 0.75f * cover) * clamp((sAlt + 4f) / 3f);
            float under = clamp(-sAlt / 4f); // just below the horizon: deeper
            int core = Scene.mix(Scene.mix(0xFFFFFFF6, 0xFFFFF6B4, low), 0xFFFFE6A0, under);
            int edge = Scene.mix(Scene.mix(0xFFFFF2D2, 0xFFFFDA74, low), 0xFFFFB066, under);
            int warm = Scene.mix(Scene.mix(edge, 0xFFFFFFFF, 0.3f), 0xFFFF6A46, low); // the glow: pale by day, red low down
            // the wide glow
            float g = S * (0.55f + 0.3f * low);
            p.setShader(new RadialGradient(sx, sy, g, new int[]{Scene.alpha(Scene.mix(edge, 0xFFFFFFFF, 0.25f), (0.34f + 0.12f * low) * vis),
                    Scene.alpha(warm, (0.13f + 0.08f * low) * vis), warm & 0x00FFFFFF}, new float[]{0f, 0.32f, 1f},
                    Shader.TileMode.CLAMP));
            c.drawCircle(sx, sy, g, p);
            if (low > 0.05f) { // the glow along the horizon as it sets
                c.save();
                c.scale(3.2f, 1f, sx, h * hz);
                p.setShader(new RadialGradient(sx, h * hz, h * 0.22f, Scene.alpha(Scene.mix(edge, warm, 0.5f), 0.5f * vis * low),
                        edge & 0x00FFFFFF, Shader.TileMode.CLAMP));
                c.drawCircle(sx, h * hz, h * 0.22f, p);
                c.restore();
            }
            // the corona close round it
            p.setShader(new RadialGradient(sx, sy, sunR * 2.4f, new int[]{Scene.alpha(edge, 0.55f * vis), Scene.alpha(edge, 0.55f * vis),
                    Scene.alpha(Scene.mix(edge, warm, 0.5f), 0.16f * vis), edge & 0x00FFFFFF}, new float[]{0f, 0.38f, 0.62f, 1f},
                    Shader.TileMode.CLAMP));
            c.drawCircle(sx, sy, sunR * 2.4f, p);
            // the disc: even in the middle, its edge soft
            p.setShader(new RadialGradient(sx, sy, sunR * 1.06f, new int[]{Scene.alpha(core, vis), Scene.alpha(core, vis),
                    Scene.alpha(edge, vis), edge & 0x00FFFFFF}, new float[]{0f, 0.55f, 0.92f, 1f}, Shader.TileMode.CLAMP));
            c.drawCircle(sx, sy, sunR * 1.06f, p);
            p.setShader(null);
            L.sunX = sx;
            L.sunY = sy;
            L.sunR = sunR;
            L.sunVis = vis * clamp((sAlt + 2f) / 3f) * (1f - 0.75f * low); // the flare: faint, and gone as it sets
        }

        // the moon, as big as the sun (smaller since 2.31), where it really is: lit as much as it is, its bright side towards the sun, the
        // dark part faintly there, a few darker seas on it; pale by day, bright at night, dimmed by clouds
        float moonX = 0, moonY = 0, moonR = 0; // where it is, for the stars to keep behind it (2.27)
        if (moon != null && cover < 0.9f) {
            float altD = (float) Math.toDegrees(moon[0]), litF = (float) moon[2];
            float dark = clamp((1f - e) / 7f);
            float vis = clamp((altD + 0.5f) / 1.5f) * (1f - 0.55f * cover) * (0.42f + 0.58f * dark);
            if (litF > 0.03f && vis > 0.02f) {
                float mx = w * (0.5f + 0.42f * (float) Math.sin(moon[1])), my = h * (hz - (hz - 0.08f) * clamp(altD / 60f));
                float mr = 0.058f * S;
                moonX = mx;
                moonY = my;
                moonR = mr;
                int pale = 0xFFF1EEE2;
                if (dark > 0.3f) { // its glow on the night: wide and faint, brighter close round it
                    float hl = (0.4f + 0.6f * litF) * vis * dark;
                    p.setAlpha(255);
                    p.setShader(new RadialGradient(mx, my, mr * 3.6f, new int[]{Scene.alpha(0xFFEEF2FF, 0.5f * hl),
                            Scene.alpha(0xFFDCE4FF, 0.14f * hl), 0x00DCE6FF}, new float[]{0.25f, 0.45f, 1f}, Shader.TileMode.CLAMP)); // brighter, tighter (2.33)
                    c.drawCircle(mx, my, mr * 3.6f, p);
                    p.setShader(null);
                }
                c.saveLayer(mx - mr * 1.5f, my - mr * 1.5f, mx + mr * 1.5f, my + mr * 1.5f, null);
                p.setColor(Scene.alpha(pale, 0.06f * vis * dark)); // its unlit part only a hint (2.33: more read as a ghost disc)
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
                float ma = Math.min(1f, 1.2f * vis) * (0.35f + 0.65f * dark); // solid at night, the brightest thing in it; faint by day
                ma *= dark + (1f - dark) * clamp(altD / 10f); // and by day gone near the horizon, never a second sun
                p.setAlpha(255); // (2.33: the dark side's faint alpha used to carry over and dim the lit part to a grey)
                p.setShader(new RadialGradient(-mr * 0.25f, -mr * 0.25f, mr * 1.3f, Scene.alpha(0xFFFFFFFC, ma),
                        Scene.alpha(0xFFFBF8EE, ma), Shader.TileMode.CLAMP));
                c.drawPath(litPart, p);
                p.setShader(null);
                c.restore();
                Paint sea = new Paint(Paint.ANTI_ALIAS_FLAG);
                sea.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP));
                sea.setColor(Scene.alpha(0xFFCFCFD8, 0.24f)); // its seas faint, so it reads bright (2.33)
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
            // the long bands of a low sun fading into midday's heaps as the sun climbs from 6 to 14 degrees (2.17); since
            // 2.31 painted from noise in three flat tones with a bright rim towards the light (Clouds)
            float heapy = L.rain || L.snow ? 0f : clamp((e - 6f) / 8f);
            float lowSun = 1f - clamp((e - 2f) / 12f);
            int rim = Scene.mix(Scene.mix(0xFFFFFFFF, Scene.mix(lit, 0xFFFFC8A8, 0.55f), lowSun), Scene.mix(lit, sky[2], 0.35f),
                    night);
            L.clouds = Clouds.strip(w, h, cover, heapy, lit, shade, rim, e < 8f, sunLeft, L.rain || L.snow, seed);
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
            float mistA = L.fog ? 0.55f : L.snow ? 0.38f : 0.32f * (1f - 0.5f * night); // a rainy night keeps its contrast (2.33)
            p.setShader(new LinearGradient(0, h * 0.6f, 0, h, mist & 0x00FFFFFF, Scene.alpha(mist, mistA),
                    Shader.TileMode.CLAMP));
            fc.drawRect(0, h * 0.6f, w, h, p);
            p.setShader(null);
        }
        p.setShader(new RadialGradient(w * 0.5f, h * 0.5f, Math.max(w, h) * 0.62f, 0x44000000, 0x00000000, Shader.TileMode.CLAMP));
        fc.drawRect(0, 0, w, h, p);
        p.setShader(null);
        WorldScene.punch(L.back, 0f, 0.25f); // the sky a little more vivid, as the graded reference's (2.33)
        L.flare = flare(L, w, h);
        return L;
    }

    /**
     * The lens flare (2.31, subtle, after Cloudscape's: github.com/Fishypants/cloudscape): a few faint coloured ghosts
     * along the line from the sun through the middle of the picture, a soft halo ring and a thin streak through the
     * sun, as a camera's lens makes; only while the sun shows. Drawn over the city.
     */
    private static Bitmap flare(Layers L, int w, int h) {
        if (L.sunVis < 0.08f || L.sunX < -0.15f * w || L.sunX > 1.15f * w) return null;
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        float cx = w / 2f, cy = h / 2f, dx = L.sunX - cx, dy = L.sunY - cy, k = L.sunVis;
        float[] at = {-0.35f, 0.45f, 0.8f, 1.25f, 1.6f}, size = {0.03f, 0.06f, 0.02f, 0.1f, 0.045f};
        int[] tint = {0xFFFFD9A0, 0xFFA0E0FF, 0xFFFFA0D8, 0xFFB8FFC8, 0xFFFFC890};
        for (int i = 0; i < at.length; i++) {
            float gx = cx - dx * at[i], gy = cy - dy * at[i], r = size[i] * w;
            p.setShader(new RadialGradient(gx, gy, r, new int[]{Scene.alpha(tint[i], 0.07f * k / (1 + 0.3f * i)),
                    Scene.alpha(tint[i], 0.11f * k / (1 + 0.3f * i)), tint[i] & 0x00FFFFFF}, new float[]{0f, 0.75f, 1f},
                    Shader.TileMode.CLAMP));
            c.drawCircle(gx, gy, r, p);
        }
        float hx = cx - dx * 0.3f, hy = cy - dy * 0.3f, hr = 0.42f * w; // the halo ring
        p.setShader(new RadialGradient(hx, hy, hr * 1.08f, new int[]{0x00A8C8FF, 0x00A8C8FF, Scene.alpha(0xFFA8C8FF, 0.06f * k),
                0x00A8C8FF}, new float[]{0f, 0.86f, 0.93f, 1f}, Shader.TileMode.CLAMP));
        c.drawCircle(hx, hy, hr * 1.08f, p);
        return b;
    }

    /** The stars at {@code t} seconds, each twinkling at its own pace. */
    static void stars(Canvas c, Layers L, float t, Paint p) {
        for (int i = 0; i < L.sx.length; i++) {
            float tw = 0.74f + 0.26f * (float) Math.sin(t * 6.2832f * L.sf[i] + L.sp[i]); // a gentler twinkle (2.33)
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
     * The rain (2.31: in three depths, after the particle libraries' layering): far, faint fine streaks, quicker and
     * longer ones nearer, and small splashes flickering on the ground; pale by day, faintly lit by the city at night.
     * Or the snow (2.31: flakes of many sizes, after react-snowfall's): small and slow far off, big and quick near, each
     * swaying on a wind that drifts slowly from one strength to another; at {@code t} seconds.
     */
    static void weather(Canvas c, Layers L, float t, Paint p) {
        int w = L.w, h = L.h;
        float night = clamp((-2f - L.e) / 10f);
        if (L.rain) {
            Random r = new Random(L.seed ^ 0x7A1L);
            int streak = Scene.mix(Scene.mix(L.sky[2], 0xFFFFFFFF, 0.5f), 0xFFFFD8A8, 0.35f * night);
            float slant = 0.16f;
            for (int i = 0; i < 64; i++) {
                int depth = i % 3; // 0 far, 1 middle, 2 near
                float len = h * (0.08f + 0.07f * depth + r.nextFloat() * 0.08f), speed = h * (1.1f + 0.45f * depth + r.nextFloat() * 0.5f);
                float y = (r.nextFloat() * h + t * speed) % (h + len) - len;
                float x = (r.nextFloat() * (w + h * 0.3f) - (y + len) * slant + w * 2f) % (w + h * 0.3f);
                p.setStrokeWidth(Math.max(1f, (0.3f + 0.2f * depth) * L.px));
                p.setColor(Scene.alpha(streak, (0.14f + 0.08f * depth) - 0.04f * night));
                c.drawLine(x, y, x - len * slant, y + len, p);
            }
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(Math.max(1f, 0.35f * L.px));
            for (int i = 0; i < 14; i++) { // splashes on the ground, each a ring for a moment
                float period = 0.5f + r.nextFloat() * 0.6f, phase = r.nextFloat();
                float k = ((t / period + phase) % 1f);
                if (k > 0.35f) continue;
                long n = (long) Math.floor(t / period + phase);
                Random rs = new Random(L.seed * 977 + i * 131 + n);
                float sx = rs.nextFloat() * w, sy = h * (0.84f + rs.nextFloat() * 0.15f), rad = (0.6f + 2.4f * k / 0.35f) * L.px;
                p.setColor(Scene.alpha(streak, 0.35f * (1 - k / 0.35f)));
                c.drawOval(sx - rad, sy - rad * 0.35f, sx + rad, sy + rad * 0.35f, p);
            }
            p.setStyle(Paint.Style.FILL);
        } else if (L.snow) {
            Random r = new Random(L.seed ^ 0x5A0L);
            int flake = Scene.mix(0xFFFFFFFF, 0xFFDCE6FF, night);
            float wind = (float) (Math.sin(t * 0.13) * 0.6 + Math.sin(t * 0.047 + 1.3) * 0.4); // drifting, -1 to 1
            for (int i = 0; i < 72; i++) {
                int depth = i % 3;
                float size = (0.35f + 0.45f * depth + r.nextFloat() * (0.3f + 0.35f * depth)) * L.px;
                float speed = h * (0.05f + 0.06f * depth + r.nextFloat() * 0.04f);
                float y = (r.nextFloat() * h + t * speed) % (h + 4 * size) - 2 * size;
                float sway = (float) Math.sin(t * (0.5f + 0.25f * depth) + i * 1.7f) * (2f + 2f * depth) * L.px;
                float x = ((r.nextFloat() * w + sway + wind * (6f + 8f * depth) * L.px * (y / h)) % w + w) % w;
                p.setColor(Scene.alpha(flake, 0.5f + 0.18f * depth + 0.15f * r.nextFloat()));
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
