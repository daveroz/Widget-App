package app.currencyexchange.widget;

/**
 * Worldly's look, Glass (chosen from three in 1.67, after the two cypher samples and Glow_Icon_Text): dark smoked glass
 * with the song's light far behind it, out of focus; card edges a fine line swept in from the top left corner; lit
 * things bloom softly, the big numbers and the small icons glow, and the brightest things have a restrained colour
 * fringe, as a lens gives them. With the rest of the visual language (OLED black, everything in the playing song's
 * colour, the Carbon+ font, rounded square cards) this sets how strong each effect is.
 */
final class Look {
    private static final Look GLASS = new Look(0.75f, 0.4f, 0.22f, 0.2f, 0.6f, 1.6f, 1.15f);

    /** The colour fringe on the brightest things: how far red and blue are pulled apart (dp), and how strongly. */
    final float fringeDp, fringe;
    /** The same fringe along a card's lit edge, as a share of the edge's own strength. */
    final float edgeFringe;
    /** How much the big numbers (the clocks, today's date) bloom into the haze: 0 keeps them plain. */
    final float textGlow;
    /** How much the small icons drawn in the line colour (the weather bar's, the Calendar's "+") glow. */
    final float iconGlow;
    /** How strong the light behind the glass is. */
    final float pool;
    /** How strong the glow around lit things is (1 = as it was before the looks). */
    final float bloom;

    private Look(float fringeDp, float fringe, float edgeFringe, float textGlow, float iconGlow, float pool, float bloom) {
        this.fringeDp = fringeDp;
        this.fringe = fringe;
        this.edgeFringe = edgeFringe;
        this.textGlow = textGlow;
        this.iconGlow = iconGlow;
        this.pool = pool;
        this.bloom = bloom;
    }

    /** The look the widgets and the app are drawn in. */
    static Look get() {
        return GLASS;
    }
}
