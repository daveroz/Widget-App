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

    /**
     * The sky {@code w} × {@code h} pixels ({@code px} pixels per dp): the sun {@code elevation} degrees above the
     * horizon, {@code cloud} % cloud cover (-1: unknown), WMO weather {@code code} (-1: unknown), the city's
     * {@code seed}.
     */
    static Bitmap draw(int w, int h, float px, double elevation, int cloud, int code, long seed) {
        Bitmap b = Bitmap.createBitmap(Math.max(2, w), Math.max(2, h), Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        float e = (float) Math.max(AT[0], Math.min(AT[AT.length - 1], elevation));
        int[] sky = blend(SKY, e), cl = blend(CLOUD, e);
        float cover = cloud < 0 ? 0.15f : Math.max(0f, Math.min(1f, cloud / 100f));
        boolean rain = (code >= 51 && code <= 67) || (code >= 80 && code <= 82) || code >= 95;
        boolean snow = (code >= 71 && code <= 77) || code == 85 || code == 86;
        boolean fog = code == 45 || code == 48;
        if (rain || snow) cover = Math.max(cover, 0.85f);

        // the sky, from its top to the horizon at the bottom of the cell
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        p.setShader(new LinearGradient(0, 0, 0, h, sky, new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        p.setShader(null);

        // the stars, on a night clear enough to see them
        float stars = clamp((-6f - e) / 6f) * clamp(1f - cover / 0.6f);
        if (stars > 0f) {
            Random r = new Random(seed ^ 0x5EEDL);
            int n = Math.round(46 * stars);
            for (int i = 0; i < n; i++) {
                float x = r.nextFloat() * w, y = r.nextFloat() * h * 0.92f;
                float size = (0.35f + r.nextFloat() * 0.6f) * px, a = (0.3f + r.nextFloat() * 0.7f) * stars;
                if (r.nextFloat() < 0.08f) { // a few brighter ones, with a faint glow round them
                    p.setShader(new RadialGradient(x, y, size * 3.5f, Scene.alpha(0xFFDCE6FF, 0.35f * a), 0x00DCE6FF,
                            Shader.TileMode.CLAMP));
                    c.drawCircle(x, y, size * 3.5f, p);
                    p.setShader(null);
                    size *= 1.3f;
                }
                p.setColor(Scene.alpha(0xFFF4F7FF, a));
                c.drawCircle(x, y, size, p);
            }
        }

        // the low sun's glow along the horizon, warm at sunset, rosy as the blue hour comes on
        float glow = (float) Math.exp(-Math.pow((e - 0.5f) / 5.5f, 2));
        if (glow > 0.02f) {
            int warm = e < -2f ? 0xFFC8708E : 0xFFFFA05A;
            p.setShader(new RadialGradient(w * 0.5f, h * 1.08f, Math.max(w, h) * 0.95f,
                    Scene.alpha(warm, 0.42f * glow), warm & 0x00FFFFFF, Shader.TileMode.CLAMP));
            c.drawRect(0, 0, w, h, p);
            p.setShader(null);
        }

        // the clouds: soft heaps of puffs, as many as the cover, in the light of the hour
        if (cover > 0.04f) {
            Random r = new Random(seed);
            int n = 1 + Math.round(cover * 13);
            Paint cp = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
            cp.setShader(new LinearGradient(0, 0, 0, h, cl[0], cl[1], Shader.TileMode.CLAMP));
            cp.setMaskFilter(new BlurMaskFilter(Math.max(1.5f, h * 0.045f), BlurMaskFilter.Blur.NORMAL));
            c.saveLayerAlpha(new RectF(0, 0, w, h), Math.round(255 * (0.38f + 0.47f * cover)));
            if (cover > 0.45f) { // a heavier sky: long bands of cloud under the heaps, a shade darker, for its texture
                Paint bp = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
                bp.setMaskFilter(new BlurMaskFilter(Math.max(2f, h * 0.06f), BlurMaskFilter.Blur.NORMAL));
                int bands = 3 + Math.round((cover - 0.45f) * 8);
                for (int i = 0; i < bands; i++) {
                    float y = h * (0.05f + r.nextFloat() * 0.9f), bh = h * (0.07f + r.nextFloat() * 0.09f);
                    float x = -0.3f * w + r.nextFloat() * 0.4f * w, bw = w * (0.9f + r.nextFloat() * 0.8f);
                    bp.setColor(Scene.mix(Scene.mix(cl[0], cl[1], y / h), sky[1], 0.25f + 0.3f * r.nextFloat()));
                    c.drawOval(x, y - bh / 2, x + bw, y + bh / 2, bp);
                }
            }
            for (int i = 0; i < n; i++) {
                float cw = w * (0.4f + r.nextFloat() * 0.45f) * (0.75f + 0.5f * cover);
                float ch = cw * (0.2f + r.nextFloat() * 0.14f);
                float cx = -0.15f * w + r.nextFloat() * 1.3f * w, cy = h * (0.08f + r.nextFloat() * 0.7f);
                int puffs = 5 + r.nextInt(4);
                for (int k = 0; k < puffs; k++) {
                    float t = puffs == 1 ? 0.5f : k / (float) (puffs - 1);
                    float rx = cw * (0.16f + r.nextFloat() * 0.12f);
                    float ry = ch * (0.55f + r.nextFloat() * 0.45f) * (1f - 0.35f * Math.abs(t - 0.5f) * 2f);
                    float x = cx + (t - 0.5f) * cw, y = cy - ry * 0.35f * (1f - Math.abs(t - 0.5f) * 2f);
                    c.drawOval(x - rx, y - ry, x + rx, y + ry, cp);
                }
            }
            c.restore();
            if (cover > 0.6f) { // overcast: the clouds close over the sky
                p.setColor(Scene.alpha(Scene.mix(cl[0], cl[1], 0.5f), 0.38f * (cover - 0.6f) / 0.4f));
                c.drawRect(0, 0, w, h, p);
            }
            if (glow > 0.02f) { // the horizon's glow on the clouds too
                int warm = e < -2f ? 0xFFC8708E : 0xFFFFA05A;
                p.setShader(new RadialGradient(w * 0.5f, h * 1.08f, Math.max(w, h) * 0.8f,
                        Scene.alpha(warm, 0.16f * glow * cover), warm & 0x00FFFFFF, Shader.TileMode.CLAMP));
                c.drawRect(0, 0, w, h, p);
                p.setShader(null);
            }
        }

        // the weather in front: a darker sky and faint streaks of rain, flakes of snow, a pale veil of fog
        if (rain) {
            p.setColor(0x26000000);
            c.drawRect(0, 0, w, h, p);
            Random r = new Random(seed ^ 0x7A1L);
            p.setColor(Scene.alpha(Scene.mix(cl[1], 0xFFFFFFFF, 0.4f), 0.18f));
            p.setStrokeWidth(Math.max(1f, 0.5f * px));
            for (int i = 0; i < 26; i++) {
                float x = r.nextFloat() * (w + h * 0.3f), y = r.nextFloat() * h, len = h * (0.08f + r.nextFloat() * 0.08f);
                c.drawLine(x, y, x - len * 0.3f, y + len, p);
            }
        } else if (snow) {
            Random r = new Random(seed ^ 0x5A0L);
            p.setColor(0xB3FFFFFF);
            for (int i = 0; i < 30; i++) c.drawCircle(r.nextFloat() * w, r.nextFloat() * h, (0.5f + r.nextFloat() * 0.6f) * px, p);
        }
        if (fog) {
            p.setShader(new LinearGradient(0, 0, 0, h, Scene.alpha(Scene.mix(cl[0], 0xFFFFFFFF, 0.3f), 0.2f),
                    Scene.alpha(Scene.mix(cl[1], 0xFFFFFFFF, 0.3f), 0.5f), Shader.TileMode.CLAMP));
            c.drawRect(0, 0, w, h, p);
            p.setShader(null);
        }

        // a faint darkening, more on a bright sky, so the white time in front reads
        float lum = Scene.luminance(sky[2]);
        p.setColor(Scene.alpha(0xFF000000, Math.max(0.1f, Math.min(0.36f, 0.12f + (lum - 0.3f) * 0.55f))));
        c.drawRect(0, 0, w, h, p);
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

    private static float clamp(float v) {
        return Math.max(0f, Math.min(1f, v));
    }
}
