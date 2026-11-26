package app.currencyexchange.widget;

/**
 * The world clocks' colours for every light of the day (2.30): one rule, not tables. The reference video gives a single
 * light, its dusk; from it two things were measured and kept for every hour:
 * <ol>
 * <li><b>The sky's shape.</b> Deep and cool at the top, warm and light at the horizon. The sky at any moment is a blend,
 * in the perceptual OKLab space, of five lights that rise and fade smoothly with the sun's height (each a soft bell, so
 * the day glides with no step): the night, the blue hour, the video's dusk (violet #5E40A6, purple-magenta #A55FCB,
 * pink #EB8ECB, at the sun 1.5 degrees down), the golden hour and the day. Mornings follow the same rule turned a little
 * cooler and rosier (their warm colours rotated towards pink, a touch softer and lighter), so dawn isn't a copy of dusk.
 * </li>
 * <li><b>How the city takes the sky.</b> The city is never painted for an hour: it's worked out from the sky by the
 * video's relationships, so it follows every light. In OKLCH: every role's hue sits a fixed step bluer than the sky's
 * top (kept within the blue to indigo band), its lightness a fixed ratio of the sky's mean, its colour a fixed share of
 * the sky top's. Measured off the video: far hills 0.96 of the mean lightness and 23 degrees bluer, lit faces 0.89 and
 * 19, the body 0.81 and 16, streets 0.72 and 13, faces in shade 0.67 and 3, trees and palms in front 0.66 and 8, edges
 * and balconies 1.04 and 10 (a lavender), and the rims of roofs 1.34 in the horizon's own hue (its warmth).</li>
 * </ol>
 * By day the city is a little softer (less colour), at night lifted to a moonlit blue (2.31) so it never goes black and
 * the lit windows read against it, as the reference poster's.
 */
final class Palette {
    private Palette() {}

    /** The range of the sun's height the colours are worked out over (beyond it they hold). */
    static final float[] AT = {-18f, 25f};

    /** The city's colours, by role. */
    static final int FAR = 0, LIT = 1, NEAR = 2, SHADE = 3, STREET = 4, FRONT = 5, EDGE = 6, RIM = 7;

    /** The windows' warm glow (the video's peach), the far lights' cool cream, and the street lamps' warm light. */
    static final int WINDOW = 0xFFF8C08E, FAR_LIGHT = 0xFFE8E2FF, LAMP = 0xFFFFD6A6;

    // the five lights: the sky's top, middle and horizon. Since 2.33 after the graded reference (measured in OKLCH): the
    // night a deep navy (hue 265, not violet), darker and calmer; the blue hour navy to a violet horizon; dusk violet,
    // a crimson-magenta and orange; the golden hour plum, coral and amber; the day a quieter, deeper blue to a pale
    // haze. (2.31's punchier lights: night 0A0B30 141760 2B2486, blue hour 1B1672 3A2AA2 7E3FBE, dusk 4A2B9E BE46A8
    // FF7E78, golden 6A62D6 FF86A6 FFC160, day 3C7FE6 66BFEE D6F0E4.)
    private static final int[] NIGHT = {0xFF030A2B, 0xFF061645, 0xFF152861};
    private static final int[] BLUE_HOUR = {0xFF0B1649, 0xFF1E2769, 0xFF503C8A};
    private static final int[] DUSK = {0xFF442D7D, 0xFFBC467C, 0xFFF77C56};
    private static final int[] GOLDEN = {0xFFB5596D, 0xFFDA645C, 0xFFF89845};
    private static final int[] DAY = {0xFF3B70C2, 0xFF67A1D6, 0xFFB9D6E8};

    /** Where each light is at its fullest (the sun's height, degrees) and how broad its bell is. */
    private static float bell(float e, float at, float width) {
        float x = (e - at) / width;
        return (float) Math.exp(-x * x);
    }

    private static float smooth(float a, float b, float x) {
        float t = Math.max(0f, Math.min(1f, (x - a) / (b - a)));
        return t * t * (3 - 2 * t);
    }

    /** How much of each light there is with the sun {@code e} degrees up: night, blue hour, dusk, golden, day. */
    private static float[] weights(float e) {
        float[] w = {1f - smooth(-15f, -9f, e), bell(e, -8f, 2.8f), bell(e, -1.5f, 2.6f), bell(e, 5f, 3.2f), smooth(6f, 20f, e)};
        float sum = 0;
        for (float v : w) sum += v;
        for (int i = 0; i < w.length; i++) w[i] /= Math.max(1e-4f, sum);
        return w;
    }

    /** The sky (top, middle, horizon) for the sun {@code e} degrees up, {@code morning} 0 (evening) to 1 (morning). */
    static int[] sky(float e, float morning) {
        float[] w = weights(e);
        int[][] lights = {NIGHT, BLUE_HOUR, DUSK, GOLDEN, DAY};
        int[] out = new int[3];
        for (int k = 0; k < 3; k++) {
            float L = 0, a = 0, b = 0;
            for (int i = 0; i < lights.length; i++) {
                float[] lab = lab(lights[i][k]);
                if (morning > 0f && (i == 2 || i == 3)) lab = dawn(lab, morning, k);
                L += w[i] * lab[0];
                a += w[i] * lab[1];
                b += w[i] * lab[2];
            }
            out[k] = rgb(L, a, b);
        }
        return out;
    }

    /**
     * The morning's turn on a warm light (the rule, not a table): its warmth rotated towards rose (the horizon most),
     * a little less colour and a little lighter, {@code m} of the way.
     */
    private static float[] dawn(float[] lab, float m, int stop) {
        double turn = Math.toRadians((stop == 2 ? 16 : stop == 1 ? 10 : 6) * m); // towards pink and lilac
        float c = (float) Math.cos(turn), s = (float) Math.sin(turn);
        float a = lab[1] * c - lab[2] * s, b = lab[1] * s + lab[2] * c;
        float soft = 1f - 0.1f * m;
        return new float[]{Math.min(0.97f, lab[0] + 0.02f * m), a * soft, b * soft};
    }

    /** The clouds: lit edge, body. As the video's: soft streaks a little lighter than the sky behind them. */
    static int[] cloud(float e, float morning) {
        int[] sky = sky(e, morning);
        float day = smooth(4f, 18f, e);
        float[] mid = lab(sky[1]), hor = lab(sky[2]);
        float[] body = lerp(mid, hor, 0.4f);
        body[0] += 0.035f;
        body = lerp(body, lab(0xFFE2E9F4), 0.55f * day); // 2.33: a cool steel white by day, not paper white
        float[] lit = lerp(hor, lab(0xFFFFFFFF), 0.12f + 0.78f * day); // 2.33: less white at night
        float night = 1f - smooth(-12f, -2f, e), deep = 1f + 0.08f * night; // a calm navy at night (more turned their shade electric blue)
        body[1] *= deep;
        body[2] *= deep;
        return new int[]{rgb(lit[0], lit[1], lit[2]), rgb(body[0], body[1], body[2])};
    }

    /** Each role's lightness (a share of the sky's mean), hue step bluer than the sky's top, and share of its colour. */
    private static final float[][] ROLES = {
            {0.96f, 23f, 0.95f},   // far
            {0.89f, 19f, 0.89f},   // lit face
            {0.81f, 16f, 0.82f},   // body
            {0.67f, 3f, 0.72f},    // face in shade
            {0.72f, 13f, 0.76f},   // streets
            {0.66f, 8f, 0.71f},    // front: trees, palms
            {1.04f, 10f, 0.59f},   // edges and balconies
    };

    /**
     * 2.33: each role's lightness share by full day (the night's are {@link #ROLES}'): the graded reference's daylight
     * is punchier, its sunlit faces bright and its shaded faces deep, so the spread between them widens with the day.
     */
    private static final float[] DAY_L = {0.96f, 1.0f, 0.8f, 0.46f, 0.6f, 0.52f, 1.04f};

    /** The city's colours, by role ({@link #FAR} ... {@link #RIM}), worked out from the sky by the video's rule. */
    static int[] city(float e, float morning) {
        int[] sky = sky(e, morning);
        float[] top = lch(sky[0]), hor = lch(sky[2]);
        float mean = (top[0] + hor[0]) / 2f;
        float day = smooth(4f, 18f, e), night = 1f - smooth(-12f, -2f, e);
        // 2.33: with the sun low the city stands against the bright sky, backlit, deep (the reference's sunset)
        float backlit = bell(e, 3f, 6f);
        int[] out = new int[8];
        for (int i = 0; i < ROLES.length; i++) {
            // 2.33, after the graded reference: deeper, so the lights and the sky stand out (by day 0.88 of 2.31's, at
            // night a faint moonlit lift instead of 2.31's strong one); calmer at night
            float share = ROLES[i][0] + (DAY_L[i] - ROLES[i][0]) * day;
            float L = mean * share * (1f - 0.12f * day) * (1f - 0.34f * backlit) + 0.02f * night;
            float h = Math.max(258f, Math.min(290f, top[2] - ROLES[i][1])); // within the blue to indigo band
            float C = Math.max(0.03f + 0.015f * night, top[1] * ROLES[i][2] * (1f - 0.28f * day)) * 1.1f;
            if (i == LIT || i == NEAR || i == SHADE || i == EDGE || i == STREET) {
                // 2.36: by full day the buildings and streets a cool concrete grey (a hint of blue; faces in shade a
                // little bluer, as the sky lights them), not the blue of the illustration's other hours
                float g = smooth(6f, 16f, e);
                float greyC = i == SHADE ? 0.03f : 0.014f;
                C += (greyC - C) * g;
                h += (252f - h) * g;
            }
            out[i] = fromLch(Math.min(0.9f, L), C, h);
        }
        // the rim along the roofs: the horizon's warmth, light
        out[RIM] = fromLch(Math.min(0.94f, mean * 1.34f + 0.03f * night), hor[1] * 0.38f, hor[2]);
        return out;
    }

    // ---------------------------------------------------------------- OKLab

    private static float lin(int c) {
        float v = c / 255f;
        return v <= 0.04045f ? v / 12.92f : (float) Math.pow((v + 0.055f) / 1.055f, 2.4f);
    }

    private static int enc(float v) {
        v = Math.max(0f, Math.min(1f, v));
        float s = v <= 0.0031308f ? 12.92f * v : (float) (1.055f * Math.pow(v, 1 / 2.4) - 0.055f);
        return Math.round(Math.max(0f, Math.min(1f, s)) * 255f);
    }

    static float[] lab(int c) {
        float r = lin((c >> 16) & 0xFF), g = lin((c >> 8) & 0xFF), b = lin(c & 0xFF);
        float l = (float) Math.cbrt(0.4122214708f * r + 0.5363325363f * g + 0.0514459929f * b);
        float m = (float) Math.cbrt(0.2119034982f * r + 0.6806995451f * g + 0.1073969566f * b);
        float s = (float) Math.cbrt(0.0883024619f * r + 0.2817188376f * g + 0.6299787005f * b);
        return new float[]{0.2104542553f * l + 0.7936177850f * m - 0.0040720468f * s,
                1.9779984951f * l - 2.4285922050f * m + 0.4505937099f * s,
                0.0259040371f * l + 0.7827717662f * m - 0.8086757660f * s};
    }

    static int rgb(float L, float a, float b) {
        float l = L + 0.3963377774f * a + 0.2158037573f * b;
        float m = L - 0.1055613458f * a - 0.0638541728f * b;
        float s = L - 0.0894841775f * a - 1.2914855480f * b;
        l = l * l * l;
        m = m * m * m;
        s = s * s * s;
        float r = 4.0767416621f * l - 3.3077115913f * m + 0.2309699292f * s;
        float g = -1.2684380046f * l + 2.6097574011f * m - 0.3413193965f * s;
        float bb = -0.0041960863f * l - 0.7034186147f * m + 1.7076147010f * s;
        return 0xFF000000 | (enc(r) << 16) | (enc(g) << 8) | enc(bb);
    }

    static float[] lch(int c) {
        float[] lab = lab(c);
        float h = (float) Math.toDegrees(Math.atan2(lab[2], lab[1]));
        return new float[]{lab[0], (float) Math.hypot(lab[1], lab[2]), h < 0 ? h + 360 : h};
    }

    static int fromLch(float L, float C, float h) {
        double r = Math.toRadians(h);
        return rgb(L, C * (float) Math.cos(r), C * (float) Math.sin(r));
    }

    private static float[] lerp(float[] a, float[] b, float t) {
        return new float[]{a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t};
    }
}
