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

    /** Where the sky's six colours stand, from its top (0) to the horizon (1) (2.31). */
    static final float[] BAND = {0f, 0.3f, 0.55f, 0.75f, 0.9f, 1f};

    // the five lights, each six colours from the top of the sky to the horizon. Since 2.31 the sunset is the reference
    // photo's (measured off it): indigo at the top, lavender, mauve, salmon and peach low down, a coral-red band, and a
    // thin crimson-magenta line on the horizon itself. The other lights are built the same way: night a faintly lighter
    // horizon, the blue hour a plum glow on it, the golden hour peach and orange, the day blue to pale
    private static final int[] NIGHT = {0xFF0C0F2E, 0xFF10143A, 0xFF141A48, 0xFF1B2156, 0xFF232968, 0xFF2C3074};
    private static final int[] BLUE_HOUR = {0xFF1C1F66, 0xFF242676, 0xFF2F2E88, 0xFF3F3896, 0xFF573E96, 0xFF6B3C80};
    private static final int[] DUSK = {0xFF4A459C, 0xFF6E60AD, 0xFF9376AC, 0xFFB9878A, 0xFFC65E60, 0xFF8E2A52};
    private static final int[] GOLDEN = {0xFF5F78D0, 0xFF8A8ED6, 0xFFB8A0CC, 0xFFEBB39F, 0xFFFFAE74, 0xFFFF8E58};
    private static final int[] DAY = {0xFF3E7ADC, 0xFF5590E6, 0xFF6EA6EE, 0xFF8CBCF2, 0xFFA8CFF5, 0xFFBCDCF6};

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

    /**
     * The sky (top, middle, low: the light over the city, for the haze, the water and the city's own colours) for the
     * sun {@code e} degrees up, {@code morning} 0 (evening) to 1 (morning). The whole sky is {@link #band}.
     */
    static int[] sky(float e, float morning) {
        int[] b = band(e, morning);
        return new int[]{b[0], b[2], b[3]};
    }

    /** The sky's six colours (at {@link #BAND}, top to horizon) for the sun {@code e} degrees up. */
    static int[] band(float e, float morning) {
        float[] w = weights(e);
        int[][] lights = {NIGHT, BLUE_HOUR, DUSK, GOLDEN, DAY};
        int[] out = new int[BAND.length];
        for (int k = 0; k < BAND.length; k++) {
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
        double turn = Math.toRadians(new int[]{6, 6, 10, 12, 16, 16}[stop] * m); // towards pink and lilac
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
        body = lerp(body, lab(0xFFF4F6FA), 0.62f * day);
        float[] lit = lerp(hor, lab(0xFFFFFFFF), 0.25f + 0.65f * day);
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

    /** The city's colours, by role ({@link #FAR} ... {@link #RIM}), worked out from the sky by the video's rule. */
    static int[] city(float e, float morning) {
        int[] sky = sky(e, morning);
        float[] top = lch(sky[0]), hor = lch(sky[2]);
        float mean = (top[0] + hor[0]) / 2f;
        float day = smooth(4f, 18f, e), night = 1f - smooth(-12f, -2f, e);
        int[] out = new int[8];
        for (int i = 0; i < ROLES.length; i++) {
            float L = mean * ROLES[i][0] + 0.085f * night; // 2.31: a moonlit blue at night, so lit windows read
            float h = Math.max(258f, Math.min(296f, top[2] - ROLES[i][1])); // within the blue to indigo band
            float C = Math.max(0.035f + 0.03f * night, top[1] * ROLES[i][2] * (1f - 0.28f * day));
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
