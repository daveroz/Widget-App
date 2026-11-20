package app.currencyexchange.widget;

import android.content.Context;

/**
 * The three looks to vote between, chosen in Settings → Look. All three share what was decided: OLED-black panels,
 * edges and lines in the playing song's colour (nothing stays gold), the Carbon+ font, the rounded square cards, the
 * Sound widget's wall and LP. They differ in where the light sits, how a card's edge is lit, how much the big text
 * blooms, and how strong the colour fringe (chromatic aberration) is on the brightest things.
 *
 * <ul>
 * <li><b>Haze</b> (Glow_Cards, Haze_Bloom_Text): light falling from above through haze, as on the lit slab. A soft
 *     pool of the light under each card's top edge, the edge a luminous line brightest along the top and fading down
 *     the sides, light rising off it into the card, the big numbers and icons blooming softly, a faint fringe.</li>
 * <li><b>Optic</b> (Chromatic, Chromatic_2, Background_Minimalism): an instrument seen through a lens. Flat black, a
 *     faint hairline edge whose four corners catch the light, a clear red and blue fringe there and on everything lit;
 *     the text stays crisp, glows stay tight.</li>
 * <li><b>Glass</b> (the two cypher samples, Glow_Icon_Text): dark smoked glass with the light far behind it, out of
 *     focus, a few soft blurs of the song's colour; the edge a fine line swept in from the top left corner, starting
 *     from a small glowing node; lit things bloom like the cypher's nodes, the big numbers and icons glow.</li>
 * </ul>
 */
final class Look {
    static final int HAZE = 0, OPTIC = 1, GLASS = 2;
    static final String[] NAMES = {"Haze", "Optic", "Glass"};
    static final String[] NOTES = {
            "Light from above through haze: luminous top edges, soft bloom on the big numbers and icons.",
            "A lens on an instrument: flat black, lit corners, crisp text, a clear colour fringe.",
            "Dark smoked glass, the light out of focus behind it; fine edges that start from a glowing node."};

    private static final Look[] ALL = {
            //         fringe dp, fringe, edge fringe, text bloom, icon bloom, pool, bloom
            new Look(HAZE, 0.6f, 0.3f, 0.14f, 0.3f, 0.55f, 1f, 1f),
            new Look(OPTIC, 1f, 0.55f, 0.3f, 0f, 0.25f, 0.35f, 0.75f),
            new Look(GLASS, 0.75f, 0.4f, 0.22f, 0.2f, 0.6f, 1.6f, 1.15f)};

    final int id;
    /** The colour fringe on the brightest things: how far red and blue are pulled apart (dp), and how strongly. */
    final float fringeDp, fringe;
    /** The same fringe along a card's lit edge, as a share of the edge's own strength. */
    final float edgeFringe;
    /** How much the big numbers (the clocks, today's date) bloom into the haze: 0 keeps them plain. */
    final float textGlow;
    /** How much the small icons drawn in the line colour (the weather bar's, the Calendar's "+") glow. */
    final float iconGlow;
    /** How strong the pool of the light on each panel is (1 = Haze's). */
    final float pool;
    /** How strong the glow around lit things is (1 = as before). */
    final float bloom;

    private Look(int id, float fringeDp, float fringe, float edgeFringe, float textGlow, float iconGlow, float pool,
                 float bloom) {
        this.id = id;
        this.fringeDp = fringeDp;
        this.fringe = fringe;
        this.edgeFringe = edgeFringe;
        this.textGlow = textGlow;
        this.iconGlow = iconGlow;
        this.pool = pool;
        this.bloom = bloom;
    }

    private static volatile Look current = ALL[HAZE];

    /** The look the widgets are drawn in. */
    static Look get() {
        return current;
    }

    static int id() {
        return current.id;
    }

    static Look of(int id) {
        return ALL[Math.max(0, Math.min(ALL.length - 1, id))];
    }

    /** Reads the chosen look (once per process start; see {@link App}). */
    static void load(Context ctx) {
        current = of(Store.sp(ctx).getInt("look", HAZE));
    }

    /** Chooses a look: saved, and every widget is drawn again in it. */
    static void set(Context ctx, int id) {
        Look next = of(id);
        Store.sp(ctx).edit().putInt("look", next.id).apply();
        if (next == current) return;
        current = next;
        Ambience.redrawAll(ctx.getApplicationContext());
    }
}
