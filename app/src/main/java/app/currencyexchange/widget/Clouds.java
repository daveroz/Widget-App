package app.currencyexchange.widget;

import android.graphics.Bitmap;

/**
 * The clouds (2.31): painted from noise, as the references' painted skies, in a comic way: their shapes from round puffs
 * separated by soft creases (the "Alligator" cell noise of Cloudscape, github.com/Fishypants/cloudscape, and Nubis), the
 * puffs gathered into clouds by a slower noise; low suns give long streaky bands, midday heaps of cumulus. Each cloud is
 * lit from the sun's side: the light is worked out from how much cloud lies between a point and the sun, then set in
 * three flat tones (lit, mid, shade) with a bright rim on the edges facing the light, so they read as drawn, not
 * rendered. Made once per light and weather, on a strip twice the sky's width that wraps round (so it can drift), at
 * half size and scaled up.
 */
final class Clouds {
    private Clouds() {}

    /**
     * The strip ({@code 2w} x {@code h}): {@code cover} 0 to 1; {@code heapy} 0 (a low sun's bands) to 1 (midday's
     * heaps); {@code lit}, {@code shade}: the clouds' two colours; {@code rim}: their sunlit edges'; the light from
     * below and the sun's side when {@code low}, from above otherwise; {@code wet}: rain or snow (heavier, flatter).
     */
    static Bitmap strip(int w, int h, float cover, float heapy, int lit, int shade, int rim, boolean low, boolean sunLeft,
                        boolean wet, long seed) {
        int sw = 2 * w;
        int gw = Math.max(16, sw / 2), gh = Math.max(16, h / 2);
        int[] out = new int[gw * gh];
        float hz = CityScene.HORIZON;
        int mid = Scene.mix(lit, shade, 0.5f);
        // cells across the strip for each noise (whole numbers, so the strip wraps)
        int bigX = Math.max(3, Math.round(sw / (0.34f * w))), smallX = bigX * 2, groupX = Math.max(2, Math.round(sw / (0.9f * w)));
        int bandX = Math.max(2, Math.round(sw / (1.3f * w))), fineX = bigX * 2;
        float aspect = h / (float) sw;
        float thr = (wet ? 0.4f : 0.66f) - (wet ? 0.3f : 0.5f) * cover;
        float lx = low ? (sunLeft ? -0.02f : 0.02f) : 0.0f, ly = low ? 0.06f : -0.055f; // towards the light
        long s1 = seed * 31 + 7, s2 = seed * 17 + 3, s3 = seed * 13 + 11;
        for (int y = 0; y < gh; y++) {
            float fy = (y + 0.5f) / gh;
            // where clouds can be: under the top, above the horizon (bands may come lower)
            float env = smooth(0.0f, 0.1f, fy) * (1f - smooth(hz - (low ? 0.06f : 0.16f), hz - 0.01f, fy));
            if (env <= 0) continue;
            for (int x = 0; x < gw; x++) {
                float u = (x + 0.5f) / gw;
                float d = density(u, fy, aspect, bigX, smallX, groupX, bandX, fineX, heapy, thr, env, s1, s2, s3);
                if (d <= 0) continue;
                float dl = density(wrap(u + lx), fy + ly, aspect, bigX, smallX, groupX, bandX, fineX, heapy, thr,
                        envAt(fy + ly, hz, low), s1, s2, s3);
                // how much cloud lies towards the light: the more, the darker; in three flat tones
                float sh = clamp01((dl - d) * 9f + 0.4f);
                float t1 = smooth(0.38f, 0.44f, sh), t2 = smooth(0.64f, 0.7f, sh);
                int col = Scene.mix(Scene.mix(lit, mid, t1), shade, t2);
                // the rim: the thin edge facing the light, bright
                float edge = (1f - smooth(0f, 0.07f, d)) * (1f - sh);
                if (edge > 0.05f) col = Scene.mix(col, rim, Math.min(0.85f, edge * 1.4f));
                float a = smooth(0f, 0.05f, d) * 0.94f;
                out[y * gw + x] = Scene.alpha(col, a);
            }
        }
        Bitmap small = Bitmap.createBitmap(out, gw, gh, Bitmap.Config.ARGB_8888);
        return Bitmap.createScaledBitmap(small, sw, h, true);
    }

    private static float envAt(float fy, float hz, boolean low) {
        return smooth(0.0f, 0.1f, fy) * (1f - smooth(hz - (low ? 0.06f : 0.16f), hz - 0.01f, fy));
    }

    /** How much cloud there is at (u, fy), less the threshold for the cover: above 0 is cloud. */
    private static float density(float u, float fy, float aspect, int bigX, int smallX, int groupX, int bandX, int fineX,
                                 float heapy, float thr, float env, long s1, long s2, long s3) {
        if (env <= 0) return -1;
        float heaps = 0, bands = 0;
        if (heapy > 0) { // midday: big puffs with smaller ones carved round them, gathered into clouds
            float big = alligator(u * bigX, fy / aspect * bigX * 0.55f, bigX, s1);
            float small = alligator(u * smallX, fy / aspect * smallX * 0.55f, smallX, s2);
            float group = fbm(u * groupX, fy / aspect * groupX * 0.6f, groupX, s3);
            heaps = (0.8f * big + 0.28f * small) * (0.25f + 1.05f * group) - 0.18f;
            heaps *= 1f - 0.5f * smooth(0.25f, 0.6f, fy); // flatter, fewer, lower down
        }
        if (heapy < 1) { // a low sun: long streaks, scalloped with fine puffs
            float streak = fbm(u * bandX, fy / aspect * bandX * 3.2f, bandX, s3 ^ 0x5A);
            float fine = alligator(u * fineX, fy / aspect * fineX * 0.9f, fineX, s2 ^ 0x77);
            bands = 0.88f * streak + 0.2f * fine - 0.12f;
        }
        return (bands + (heaps - bands) * heapy) * env - thr * (0.7f + 0.3f * env);
    }

    // ------------------------------------------------------------------ noise, periodic across the strip

    private static float hash(long a, long b, long s) {
        long h = a * 374761393L + b * 668265263L + s * 2246822519L;
        h = (h ^ (h >>> 13)) * 1274126177L;
        h ^= h >>> 16;
        return (h & 0xFFFFFF) / (float) 0x1000000;
    }

    /** Round puffs separated by soft creases: every cell a bump of its own size and strength; the highest wins. */
    private static float alligator(float x, float y, int period, long seed) {
        int ix = (int) Math.floor(x), iy = (int) Math.floor(y);
        float fx = x - ix, fy = y - iy, v = 0;
        for (int oy = -1; oy <= 1; oy++) {
            for (int ox = -1; ox <= 1; ox++) {
                long cx = Math.floorMod(ix + ox, period), cy = iy + oy;
                float px = ox + hash(cx, cy, seed) - fx, py = oy + hash(cx, cy, seed + 1) - fy;
                float rad = 0.75f + 0.6f * hash(cx, cy, seed + 2), amp = 0.45f + 0.55f * hash(cx, cy, seed + 3);
                float d = (px * px + py * py) / (rad * rad);
                if (d < 1) v = Math.max(v, amp * (float) Math.sqrt(1 - d) * (1 - d * 0.35f));
            }
        }
        return v;
    }

    private static float value(float x, float y, int period, long seed) {
        int ix = (int) Math.floor(x), iy = (int) Math.floor(y);
        float fx = x - ix, fy = y - iy;
        fx = fx * fx * fx * (fx * (fx * 6 - 15) + 10);
        fy = fy * fy * fy * (fy * (fy * 6 - 15) + 10);
        int x0 = Math.floorMod(ix, period), x1 = Math.floorMod(ix + 1, period);
        float a = hash(x0, iy, seed), b = hash(x1, iy, seed), c = hash(x0, iy + 1, seed), d = hash(x1, iy + 1, seed);
        return a + (b - a) * fx + (c - a) * fy + (a - b - c + d) * fx * fy;
    }

    private static float fbm(float x, float y, int period, long seed) {
        float v = 0, amp = 0.55f, sum = 0;
        int p = period;
        for (int o = 0; o < 4; o++) {
            v += amp * value(x, y, p, seed + o * 101);
            sum += amp;
            x *= 2;
            y *= 2;
            p *= 2;
            amp *= 0.5f;
        }
        return v / sum;
    }

    private static float wrap(float u) { return u - (float) Math.floor(u); }

    private static float clamp01(float v) { return Math.max(0f, Math.min(1f, v)); }

    private static float smooth(float a, float b, float x) {
        float t = clamp01((x - a) / (b - a));
        return t * t * (3 - 2 * t);
    }
}
