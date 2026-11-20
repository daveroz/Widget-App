package app.currencyexchange.widget;

/**
 * Worldly's look (chosen as "Glass" in 1.67): cards in a mildly see-through black with a hint of the song's colour
 * ({@link Stage}); text with the colour fringe a lens gives (no glow), lit things blooming softly, buttons and icons
 * solid, and the brightest things with the same fringe; and what a camera does to light ({@link Optics}): strikes,
 * reflections, haze and halation. With the rest of the visual language (everything in the playing song's colour, the
 * Carbon+ font, rounded square cards) this sets how strong each effect is.
 */
final class Look {
    /** The look as chosen (1.78: a little less see-through, 84%, and a stronger fringe on lit things, not on text). */
    static final Look GLASS = new Look(0.84f, 1.1f, 0.65f, 0.6f, 0.28f, 1.15f, 1f, 1f);

    private static Look current = GLASS;

    /** How opaque the widgets' black is (1 = solid); the app's cards are nearly opaque whatever this is. */
    final float opacity;
    /** The colour fringe on the brightest things: how far red and blue are pulled apart (dp), and how strongly. */
    final float fringeDp, fringe;
    /**
     * The fringe on text (all of it, the widgets' and the app's; none glows): subtler than on the bright things, its
     * colours pulled less far apart (dp) and fainter.
     */
    final float textFringeDp, textFringe;
    /** How strong the glow around lit things is (1 = as it was before the looks; the Glass look is 1.15). */
    final float bloom;
    /**
     * The haze, as a Black Pro-Mist filter makes it (1 = the Glass look's): how much of a light's glow scatters out
     * round it, the soft cores of the strikes, the light falling on the cards, the fog over the Sound widget's lamps.
     */
    final float haze;
    /**
     * Halation, the red-orange glow film gives round bright points (1 = the Glass look's: only a faint ring round each
     * strike). Above 1, every lit thing has it too: the lamps, the record's rim, the progress point, the trend lines,
     * the SBB face.
     */
    final float halation;

    Look(float opacity, float fringeDp, float fringe, float textFringeDp, float textFringe, float bloom, float haze,
         float halation) {
        this.opacity = opacity;
        this.fringeDp = fringeDp;
        this.fringe = fringe;
        this.textFringeDp = textFringeDp;
        this.textFringe = textFringe;
        this.bloom = bloom;
        this.haze = haze;
        this.halation = halation;
    }

    /** The look the widgets and the app are drawn in. */
    static Look get() {
        return current;
    }

    /** Draws everything in {@code look} from now on (for trying looks side by side). */
    static void use(Look look) {
        current = look == null ? GLASS : look;
    }

    /** How much stronger the glow is than the Glass look's: 1 in it. */
    float bloomGain() {
        return bloom / GLASS.bloom;
    }

    /** How strong the halation round every lit thing is: none in the Glass look, growing as halation passes 1. */
    float halationGlow() {
        return Math.max(0f, halation - 1f);
    }

    /** Text for a cache key: pictures drawn in one look aren't reused in another. */
    String tag() {
        return this == GLASS ? "" : "~" + Integer.toHexString(System.identityHashCode(this));
    }
}
