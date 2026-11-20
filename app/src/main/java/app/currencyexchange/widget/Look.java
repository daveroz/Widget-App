package app.currencyexchange.widget;

/**
 * Worldly's look, Glass (chosen from three in 1.67, after the two cypher samples and Glow_Icon_Text): frosted, smoky
 * glass with the song's light far behind it, out of focus; card edges a fine line swept in from the top left corner;
 * text hazed by its own light, lit things blooming softly, the big numbers glowing more, buttons and icons solid, and the
 * brightest things with the colour fringe a lens gives them. With the rest of the visual language (everything in the
 * playing song's colour, the Carbon+ font, rounded square cards) this sets how strong each effect is.
 */
final class Look {
    private static final Look GLASS = new Look(0.95f, 0.58f, 0.34f, 0.55f, 0.75f, 1f, 1.15f);

    /** The colour fringe on the brightest things: how far red and blue are pulled apart (dp), and how strongly. */
    final float fringeDp, fringe;
    /** The same fringe along a card's lit edge, as a share of the edge's own strength. */
    final float edgeFringe;
    /** How much the widgets' text blooms into the haze around it (Haze_Bloom_Text): 0 keeps it plain. */
    final float textHaze;
    /** How much the big numbers (the clocks, today's date) bloom: more than the rest of the text. */
    final float textGlow;
    /** How strong the lights behind the glass are (1 = as drawn in {@link Stage}). */
    final float pool;
    /** How strong the glow around lit things is (1 = as it was before the looks). */
    final float bloom;

    private Look(float fringeDp, float fringe, float edgeFringe, float textHaze, float textGlow, float pool, float bloom) {
        this.fringeDp = fringeDp;
        this.fringe = fringe;
        this.edgeFringe = edgeFringe;
        this.textHaze = textHaze;
        this.textGlow = textGlow;
        this.pool = pool;
        this.bloom = bloom;
    }

    /** The look the widgets and the app are drawn in. */
    static Look get() {
        return GLASS;
    }
}
