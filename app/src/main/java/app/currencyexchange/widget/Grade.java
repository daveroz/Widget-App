package app.currencyexchange.widget;

import android.graphics.Bitmap;

/**
 * The world clocks' final grade (2.34), after the user's own edit of a screenshot in Lightroom (contrast +20,
 * saturation +40, brightness +0.76 for the daylight's too-dark shadows), measured against the same clocks as the app
 * drew them: every colour 1.35 times as saturated, and by full day the shadows lifted (in OKLab lightness, +0.07 round
 * the darkest tones, fading out by the mid-tones: 0.29 to 0.33, 0.33 to 0.40, 0.56 to 0.57), the night and the golden
 * hours unchanged in their lightness. Worked once on each still layer of a clock (its sky, clouds and city), never per
 * frame.
 */
final class Grade {
    private Grade() {}

    /** How much more saturated every colour is (the edit's +40, measured). */
    static final float SATURATION = 1.35f;

    private static final float[] LIN = new float[256];
    static {
        for (int i = 0; i < 256; i++) {
            float v = i / 255f;
            LIN[i] = v <= 0.04045f ? v / 12.92f : (float) Math.pow((v + 0.055f) / 1.055f, 2.4f);
        }
    }

    /** How much daylight there is in the light (the shadow lift's share): none below 8 degrees, all from 20. */
    static float day(float sunDegrees) {
        float t = Math.max(0f, Math.min(1f, (sunDegrees - 8f) / 12f));
        return t * t * (3 - 2 * t);
    }

    /**
     * The grade on {@code b} (its see-through parts left as they are), with {@code lift} of the day's shadow lift.
     * Returns the graded picture: {@code b} itself, or a copy when {@code b} can't be written to (a scaled bitmap can be
     * immutable).
     */
    static Bitmap apply(Bitmap b, float lift) {
        if (b == null) return null;
        if (!b.isMutable()) b = b.copy(Bitmap.Config.ARGB_8888, true);
        int w = b.getWidth(), h = b.getHeight();
        int[] px = new int[w * h];
        b.getPixels(px, 0, w, 0, 0, w, h);
        // the shadow lift as a gain for each lightness (on sRGB luma, worked out in OKLab lightness)
        float[] gain = new float[256];
        for (int y = 0; y < 256; y++) {
            float lin = LIN[y], L = (float) Math.cbrt(lin);
            float x = (L - 0.32f) / 0.25f, d = lift * 0.05f * Math.max(0f, 1f - x * x);
            if (L < 0.32f) d *= Math.min(1f, L / 0.12f); // never lifting black itself
            float L2 = L + d, lin2 = L2 * L2 * L2;
            float s2 = lin2 <= 0.0031308f ? 12.92f * lin2 : (float) (1.055f * Math.pow(lin2, 1 / 2.4) - 0.055f);
            gain[y] = y == 0 ? 1f : Math.max(1f, s2 * 255f / y);
        }
        for (int i = 0; i < px.length; i++) {
            int c = px[i], a = c >>> 24;
            if (a == 0) continue;
            float r = (c >> 16) & 255, g = (c >> 8) & 255, bb = c & 255;
            float Y = 0.299f * r + 0.587f * g + 0.114f * bb;
            float k = gain[Math.min(255, Math.round(Y))];
            r *= k; g *= k; bb *= k; Y *= k;
            r = Y + (r - Y) * SATURATION;
            g = Y + (g - Y) * SATURATION;
            bb = Y + (bb - Y) * SATURATION;
            px[i] = (a << 24) | (clamp(r) << 16) | (clamp(g) << 8) | clamp(bb);
        }
        b.setPixels(px, 0, w, 0, 0, w, h);
        return b;
    }

    private static int clamp(float v) { return v <= 0 ? 0 : v >= 255 ? 255 : Math.round(v); }
}
