package app.currencyexchange.widget;

import android.graphics.Color;

/**
 * The light of the Sound widget, as on the Deco launcher's stage: one colour, the primary colour of
 * what is playing (the album cover's, or the app's own), and the near-white of an LED's core. One
 * colour and white, never more: the colour's shades are the same light seen differently, deeper where
 * it scatters in the haze, hot (overexposed) at a lamp's core. Art with no real colour (grey, black or
 * white) gets a cool white.
 */
final class Scene {
    /** No real colour: the picture is grey, white or black. */
    static final int NONE = 0;

    final int key;
    final int led;
    /**
     * The key overexposed, as a bright light reads to the eye or a camera: the warm core of a lit lamp (for amber, a
     * saturated warm yellow), white-hot only at its very brightest. Its channels clip, so it isn't simply paler.
     */
    final int hot;
    /** The key, richer and a little deeper: light seen through the haze around a lamp (its bloom, halo and glow). */
    final int deep;
    /** The key, a little lighter, for text: labels stay easy to read on the night background. */
    final int label;
    /**
     * The line colour, for structure (card edges, rules, ticks, the header's icons, section titles): the key paled
     * towards a warm white, so it follows the song (for amber it's the old gold) and still reads on black.
     */
    final int line;
    /** Quiet labels (what a value is, not the value: day names, "Market", a song's length): soft text, tinted by the key. */
    final int quiet;

    Scene(int key) {
        this(key, deepOf(key), mix(gain(key, 1.2f), Color.WHITE, 0.06f));
    }

    /** A light whose deep and hot shades are given rather than worked out from its key (Worldly's own, {@link #idle}). */
    private Scene(int key, int deep, int hot) {
        this.key = key | 0xFF000000;
        this.led = mix(key, Color.WHITE, 0.88f);
        this.hot = hot | 0xFF000000;
        this.deep = deep | 0xFF000000;
        this.label = mix(key, Color.WHITE, 0.25f);
        this.line = readable(mix(this.key, 0xFFF4EFE6, 0.3f), 0.42f);
        this.quiet = readable(mix(this.key, Theme.TEXT_SOFT, 0.62f), 0.5f);
    }

    /** The key, richer and a little deeper (see {@link #deep}). */
    private static int deepOf(int key) {
        float[] hsv = new float[3];
        Color.colorToHSV(key | 0xFF000000, hsv);
        hsv[1] = Math.min(0.95f, hsv[1] * 1.5f);
        hsv[2] *= 0.92f;
        return Color.HSVToColor(hsv);
    }

    /**
     * Worldly's own light, while nothing plays (since 1.89): the glowing orange of the filaments in the reference
     * picture, measured off its pixels: #F37613 where they're lit, a deeper red-orange where the glow fades into the
     * dark (its haze), and yellow at the white-hot cores (its hot shade).
     */
    static final int IDLE = 0xFFF37613, IDLE_DEEP = 0xFFE05A10, IDLE_HOT = 0xFFFCD467;

    /** Worldly's own light: the light while nothing plays ({@link #IDLE}). */
    static Scene idle() {
        return new Scene(IDLE, IDLE_DEEP, IDLE_HOT);
    }

    /** A cool white, for apps and covers with no real colour of their own. */
    static Scene neutral() {
        return new Scene(0xFFD2DEEA);
    }

    /** The scene for a primary colour ({@link #NONE} for none): the colour lifted so it reads as light. */
    static Scene of(int primary) {
        if (primary == NONE) return neutral();
        float[] hsv = new float[3];
        Color.colorToHSV(primary, hsv);
        if (hsv[1] < 0.2f || hsv[2] < 0.12f) return neutral(); // grey, black or white
        hsv[1] = Math.max(0.55f, Math.min(0.85f, hsv[1]));
        hsv[2] = Math.max(0.92f, hsv[2]);
        return new Scene(Color.HSVToColor(hsv));
    }

    boolean sameAs(Scene other) {
        return other != null && key == other.key;
    }

    /** What a cached picture drawn in this light depends on: its colour. */
    String tag() {
        // its colour and hot shade (Worldly's own light gives its own), and the look: pictures aren't reused across them
        return Integer.toHexString(key) + "." + Integer.toHexString(hot) + Look.get().tag();
    }

    static int alpha(int colour, float a) {
        int value = Math.max(0, Math.min(255, Math.round(a * 255)));
        return (value << 24) | (colour & 0x00FFFFFF);
    }

    /** A colour {@code g} times as bright, each channel clipping at full, as an overexposed light does. */
    static int gain(int colour, float g) {
        return Color.rgb(Math.min(255, Math.round(Color.red(colour) * g)), Math.min(255, Math.round(Color.green(colour) * g)),
                Math.min(255, Math.round(Color.blue(colour) * g)));
    }

    /** A colour lifted toward white just enough to read on the night (relative luminance at least {@code least}). */
    static int readable(int colour, float least) {
        float l = luminance(colour);
        return l >= least ? colour : mix(colour, Color.WHITE, (least - l) / (1 - l));
    }

    static float luminance(int colour) {
        return 0.2126f * linear(Color.red(colour)) + 0.7152f * linear(Color.green(colour)) + 0.0722f * linear(Color.blue(colour));
    }

    private static float linear(int channel) {
        float c = channel / 255f;
        return c <= 0.04045f ? c / 12.92f : (float) Math.pow((c + 0.055f) / 1.055f, 2.4f);
    }

    static int mix(int from, int to, float f) {
        int r = Math.round(Color.red(from) + (Color.red(to) - Color.red(from)) * f);
        int g = Math.round(Color.green(from) + (Color.green(to) - Color.green(from)) * f);
        int b = Math.round(Color.blue(from) + (Color.blue(to) - Color.blue(from)) * f);
        return Color.rgb(r, g, b);
    }
}
