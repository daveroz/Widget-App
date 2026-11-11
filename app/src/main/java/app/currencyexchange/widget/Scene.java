package app.currencyexchange.widget;

import android.graphics.Color;

/**
 * The light of the Sound widget, as on the Deco launcher's stage: one colour, the primary colour of
 * what is playing (the album cover's, or the app's own), and the near-white of an LED's core. One
 * colour and white, never more. Art with no real colour (grey, black or white) gets a cool white.
 */
final class Scene {
    /** No real colour: the picture is grey, white or black. */
    static final int NONE = 0;

    final int key;
    final int led;
    /** The key, a little lighter, for text: labels stay easy to read on the night background. */
    final int label;

    Scene(int key) {
        this.key = key | 0xFF000000;
        this.led = mix(key, Color.WHITE, 0.88f);
        this.label = mix(key, Color.WHITE, 0.25f);
    }

    /** Worldly's own amber: the light while nothing plays. */
    static Scene amber() {
        return new Scene(Theme.AMBER);
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

    static int alpha(int colour, float a) {
        int value = Math.max(0, Math.min(255, Math.round(a * 255)));
        return (value << 24) | (colour & 0x00FFFFFF);
    }

    static int mix(int from, int to, float f) {
        int r = Math.round(Color.red(from) + (Color.red(to) - Color.red(from)) * f);
        int g = Math.round(Color.green(from) + (Color.green(to) - Color.green(from)) * f);
        int b = Math.round(Color.blue(from) + (Color.blue(to) - Color.blue(from)) * f);
        return Color.rgb(r, g, b);
    }
}
