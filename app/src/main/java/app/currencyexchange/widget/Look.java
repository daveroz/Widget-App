package app.currencyexchange.widget;

/**
 * Worldly's look (chosen as "Glass" in 1.67): cards in a mildly see-through black with a hint of the song's colour
 * ({@link Stage}); text with the colour fringe a lens gives (no glow), lit things blooming softly, buttons and icons
 * solid, and the brightest things with the same fringe. With the rest of the visual language
 * (everything in the playing song's colour, the Carbon+ font, rounded square cards) this sets how strong each effect
 * is.
 */
final class Look {
    private static final Look GLASS = new Look(0.95f, 0.58f, 0.6f, 0.28f, 1.15f);

    /** The colour fringe on the brightest things: how far red and blue are pulled apart (dp), and how strongly. */
    final float fringeDp, fringe;
    /**
     * The fringe on text (all of it, the widgets' and the app's; none glows): subtler than on the bright things, its
     * colours pulled less far apart (dp) and fainter.
     */
    final float textFringeDp, textFringe;
    /** How strong the glow around lit things is (1 = as it was before the looks). */
    final float bloom;

    private Look(float fringeDp, float fringe, float textFringeDp, float textFringe, float bloom) {
        this.fringeDp = fringeDp;
        this.fringe = fringe;
        this.textFringeDp = textFringeDp;
        this.textFringe = textFringe;
        this.bloom = bloom;
    }

    /** The look the widgets and the app are drawn in. */
    static Look get() {
        return GLASS;
    }
}
