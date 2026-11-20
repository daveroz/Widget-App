package app.currencyexchange.widget;

/**
 * The colours the Sound and Music widgets' text is drawn in, warmed a little by the light: text drawn in the light
 * (labels), softer text, and quiet text. No text glows; it has the lens's colour fringe instead ({@link Lens}).
 */
final class Glow {
    private Glow() {}

    /** The softer text colour, lit a little less. */
    static int soft(Scene s) {
        return Scene.mix(Theme.TEXT_SOFT, s.hot, 0.14f);
    }

    /** Quiet text (secondary lines), barely lit. */
    static int muted(Scene s) {
        return Scene.mix(Theme.MUTED, s.hot, 0.15f);
    }

    /** Text drawn in the light (labels, today, "in 5 minutes"): its own bright shade, kept easy to read. */
    static int label(Scene s) {
        return Scene.readable(Scene.gain(s.deep, 1.25f), 0.4f);
    }
}
