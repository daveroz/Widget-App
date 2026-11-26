package app.currencyexchange.widget;

/**
 * The world clocks' colours through the day (2.29), after the reference video's colour logic, measured off its dusk
 * frames. Its rules, kept at every hour so the cycle stays one family:
 * <ul>
 * <li>The sky runs from a deep cool colour at the top to a light warm one at the horizon (at the video's dusk: violet
 * #5E40A6, purple-magenta #A55FCB, pink #EB8ECB); by day blue to pale, at night navy to indigo.</li>
 * <li>The city stays in a cool blue to indigo band whatever the sky: the far hills and city a lighter periwinkle
 * (#5B74D4), the faces turned to the light blue (#5766C0), the faces away from it more violet and darker (#4B3D83),
 * the streets in between (#484893), the trees and palms in front the deepest indigo (#433C81), never black.</li>
 * <li>Edges and balconies catch a lavender light (#8384C2); the tops of roofs a rim of the horizon's warmth (#DBB8D1);
 * windows glow peach (#F2B48A); the far lights are a cool cream.</li>
 * </ul>
 * The video's own tones are the evening's dusk and sunset; every other hour (night, blue hour, first light, dawn,
 * sunrise, the golden hours, morning, afternoon, midday) is set by the same rules, and the colours glide between them
 * by the sun's height (and from the morning's set to the evening's by its direction).
 */
final class Palette {
    private Palette() {}

    /** The sun's heights (degrees) at which the colours below are set. */
    static final float[] AT = {-18f, -12f, -8f, -5f, -2f, 1f, 5f, 12f, 25f};

    /** The city's colours, by role. */
    static final int FAR = 0, LIT = 1, NEAR = 2, SHADE = 3, STREET = 4, FRONT = 5, EDGE = 6, RIM = 7;

    // ---------------------------------------------------------------- the evening
    /** The evening's skies: top, middle, horizon. */
    private static final int[][] SKY_EVENING = {
            {0xFF0C0F2E, 0xFF141A48, 0xFF242B68},   // night: navy to indigo
            {0xFF121546, 0xFF1E2262, 0xFF36347E},   // the night coming on
            {0xFF1C1F66, 0xFF2E2D88, 0xFF5448A8},   // the blue hour: indigo, a violet horizon
            {0xFF2E2A80, 0xFF573CA2, 0xFF9C58BE},   // late dusk: indigo, purple, magenta
            {0xFF5E40A6, 0xFFA55FCB, 0xFFEB8ECB},   // dusk: the video's violet, purple-magenta, pink
            {0xFF6A45AE, 0xFFC068C8, 0xFFFFA2B8},   // sunset: the video's, its horizon warmer
            {0xFF7A6CC4, 0xFFE08AB8, 0xFFFFC28E},   // the golden evening: lilac, rose, peach
            {0xFF5482D8, 0xFF8CB0EA, 0xFFE6DDEA},   // afternoon: blue to a warm white
            {0xFF3E7ADC, 0xFF6EA6EE, 0xFFBCDCF6},   // midday: blue to pale
    };
    /**
     * The evening's clouds: lit edge, body. As the video's, soft streaks a little lighter and pinker than the sky behind
     * them when the sun is low (not dark bodies with bright rims), white heaps by day, navy at night.
     */
    private static final int[][] CLOUD_EVENING = {
            {0xFF2C3170, 0xFF161A4C}, {0xFF363C82, 0xFF1E225E}, {0xFF5A58AE, 0xFF333384}, {0xFFB67ACC, 0xFF7450B0},
            {0xFFF0A0D4, 0xFFB46CCC}, {0xFFFFB0C8, 0xFFCC7EC8}, {0xFFFFD0B0, 0xFFE0A2BC}, {0xFFFFF6F0, 0xFFC6D2EE},
            {0xFFFFFFFF, 0xFFC8D8F0}};
    /** The evening's city: far, lit face, body, face in shade, streets, front, edges, rims. */
    private static final int[][] CITY_EVENING = {
            {0xFF262C68, 0xFF262E6C, 0xFF20265F, 0xFF181C4E, 0xFF151946, 0xFF0E1134, 0xFF343C86, 0xFF3E4690},
            {0xFF2C3474, 0xFF2E3878, 0xFF262E6C, 0xFF1E2258, 0xFF1A1F50, 0xFF12153C, 0xFF3E4794, 0xFF4C509C},
            {0xFF3A4896, 0xFF3A4A9C, 0xFF313E8C, 0xFF282E74, 0xFF232A6A, 0xFF191D52, 0xFF5260B0, 0xFF7064B8},
            {0xFF4A5CB8, 0xFF4A58B0, 0xFF414AA0, 0xFF393486, 0xFF383C88, 0xFF2E2A6E, 0xFF6C70BC, 0xFFB07EC8},
            {0xFF5B74D4, 0xFF5766C0, 0xFF5158A9, 0xFF4B3D83, 0xFF484893, 0xFF433C81, 0xFF8384C2, 0xFFDBB8D1},
            {0xFF6478D6, 0xFF5E6CC6, 0xFF5560AE, 0xFF4E428C, 0xFF4A4C98, 0xFF443E84, 0xFF8C8CCA, 0xFFF2C2C8},
            {0xFF7A88D8, 0xFF6C7ACC, 0xFF5E68B4, 0xFF534C96, 0xFF50529E, 0xFF463F86, 0xFFA0A4DA, 0xFFFFD6B0},
            {0xFF94A8E0, 0xFF7C92D8, 0xFF6A7EC8, 0xFF5260A8, 0xFF4E5CA4, 0xFF3E4888, 0xFFB4C2EE, 0xFFFFF2E6},
            {0xFFA2B8EA, 0xFF82A0E0, 0xFF7088D0, 0xFF5468B2, 0xFF5064AC, 0xFF3E4C90, 0xFFC0D0F4, 0xFFFFFFFF},
    };

    // ---------------------------------------------------------------- the morning
    /** The morning's skies: cooler and pinker than the evening's at the same height. */
    private static final int[][] SKY_MORNING = {
            {0xFF0C0F2E, 0xFF141A48, 0xFF242B68},   // night
            {0xFF121546, 0xFF1E2462, 0xFF2E3A84},   // the night lifting
            {0xFF1C2468, 0xFF2A3A8C, 0xFF4C5CAE},   // the blue hour
            {0xFF2C3A8E, 0xFF5A5AB0, 0xFFA688C8},   // first light: indigo, periwinkle, lavender
            {0xFF4E58B8, 0xFFA07CC8, 0xFFF4A2C0},   // dawn: violet, lavender, pink
            {0xFF6A6EC4, 0xFFD88CC0, 0xFFFFC0A0},   // sunrise: lilac, rose, peach
            {0xFF7A88D0, 0xFFE8A8B8, 0xFFFFD8A8},   // the golden morning
            {0xFF4C88DC, 0xFF84B6EE, 0xFFDCEAF6},   // morning
            {0xFF3E7ADC, 0xFF6EA6EE, 0xFFBCDCF6},   // midday
    };
    private static final int[][] CLOUD_MORNING = {
            {0xFF2C3170, 0xFF161A4C}, {0xFF363C82, 0xFF1E2460}, {0xFF5A62B0, 0xFF333C88}, {0xFFB89AD4, 0xFF7066B4},
            {0xFFF6B0CC, 0xFFB08ED0}, {0xFFFFC8B8, 0xFFD69EC6}, {0xFFFFE0C0, 0xFFE6BCC2}, {0xFFFFFFFF, 0xFFC8D6EE},
            {0xFFFFFFFF, 0xFFC8D8F0}};
    private static final int[][] CITY_MORNING = {
            {0xFF262C68, 0xFF262E6C, 0xFF20265F, 0xFF181C4E, 0xFF151946, 0xFF0E1134, 0xFF343C86, 0xFF3E4690},
            {0xFF2C3676, 0xFF2E3A7A, 0xFF262F6E, 0xFF1E235A, 0xFF1A2052, 0xFF12163E, 0xFF3E4A96, 0xFF4C56A0},
            {0xFF3A4C98, 0xFF3A4E9E, 0xFF31418E, 0xFF283076, 0xFF232C6C, 0xFF191E54, 0xFF5266B2, 0xFF7070BC},
            {0xFF4A60BA, 0xFF4A5CB2, 0xFF414EA2, 0xFF393A88, 0xFF383E8A, 0xFF2E2C70, 0xFF7078C0, 0xFFB894CC},
            {0xFF5C78D6, 0xFF586CC4, 0xFF525CAC, 0xFF4A4288, 0xFF484C96, 0xFF433E84, 0xFF8890C8, 0xFFE8BED0},
            {0xFF6880D8, 0xFF6072C8, 0xFF5664B0, 0xFF4E4890, 0xFF4A509A, 0xFF444086, 0xFF96A0D2, 0xFFFFCCC0},
            {0xFF7E8EDA, 0xFF6E80CE, 0xFF606CB6, 0xFF545098, 0xFF5056A0, 0xFF464288, 0xFFA8B0DE, 0xFFFFE0BC},
            {0xFF96ACE2, 0xFF7E96DA, 0xFF6C82CA, 0xFF5464AA, 0xFF5060A6, 0xFF3E4A8A, 0xFFB8C8F0, 0xFFFFFFFF},
            {0xFFA2B8EA, 0xFF82A0E0, 0xFF7088D0, 0xFF5468B2, 0xFF5064AC, 0xFF3E4C90, 0xFFC0D0F4, 0xFFFFFFFF},
    };

    /** The windows' warm glow (the video's peach), the far lights' cool cream, and the street lamps' warm light. */
    static final int WINDOW = 0xFFF8C08E, FAR_LIGHT = 0xFFE8E2FF, LAMP = 0xFFFFD6A6;

    /** The sky (top, middle, horizon) for the sun {@code e} degrees up, {@code morning} 0 (evening) to 1 (morning). */
    static int[] sky(float e, float morning) {
        return mix(blend(SKY_EVENING, e), blend(SKY_MORNING, e), morning);
    }

    /** The clouds: lit edge and shaded body. */
    static int[] cloud(float e, float morning) {
        return mix(blend(CLOUD_EVENING, e), blend(CLOUD_MORNING, e), morning);
    }

    /** The city's colours, by role ({@link #FAR} ... {@link #RIM}). */
    static int[] city(float e, float morning) {
        return mix(blend(CITY_EVENING, e), blend(CITY_MORNING, e), morning);
    }

    private static int[] blend(int[][] set, float e) {
        float x = Math.max(AT[0], Math.min(AT[AT.length - 1], e));
        int i = 0;
        while (i < AT.length - 2 && x > AT[i + 1]) i++;
        float t = Math.max(0f, Math.min(1f, (x - AT[i]) / (AT[i + 1] - AT[i])));
        t = t * t * (3 - 2 * t); // eased, so no keyframe shows as a kink
        int[] out = new int[set[i].length];
        for (int k = 0; k < out.length; k++) out[k] = Scene.mix(set[i][k], set[i + 1][k], t);
        return out;
    }

    private static int[] mix(int[] a, int[] b, float t) {
        int[] out = new int[a.length];
        for (int k = 0; k < a.length; k++) out[k] = Scene.mix(a[k], b[k], t);
        return out;
    }
}
