package app.currencyexchange.widget;

import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Paint;

/**
 * The lighting every widget and screen shares, first worked out on the Sound widget: things that give light are
 * drawn as light sources in a dark, hazy room, not as a brighter picture. Text glows: a wide, faint bloom, a softer
 * glow close around and a tight halo hugging the letters, in the light's deeper shade (light seen through haze),
 * while the letters themselves take a little of the light's warmth. Text drawn in the light glows most, the main
 * text softly, quiet text faintly or not at all.
 */
final class Glow {
    private Glow() {}

    /** How strongly each kind of text glows (0 to 1). */
    static final float LABEL = 1f, MAIN = 0.6f, SOFT = 0.35f, QUIET = 0.18f;

    /** The main text colour, a warm white lit by the light. */
    static int white(Scene s) {
        return Scene.mix(Theme.TEXT, s.hot, 0.16f);
    }

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

    /**
     * Draws the glow of {@code text} at ({@code x}, {@code y}) with {@code paint}'s typeface and size (not the letters
     * themselves): in {@code light}, {@code strength} strong, around letters that will be {@code colour}. The glow's
     * reach is {@code reach} pixels for its wide bloom (the softer glow and the halo are a share of it).
     */
    static void text(Canvas c, Paint paint, String text, float x, float y, int colour, int light, float strength,
                     float reach) {
        if (strength <= 0f || text == null || text.isEmpty()) return;
        int was = paint.getColor();
        paint.setMaskFilter(new BlurMaskFilter(reach, BlurMaskFilter.Blur.NORMAL));
        paint.setColor(Scene.alpha(light, 0.7f * strength));
        c.drawText(text, x, y, paint);
        paint.setMaskFilter(new BlurMaskFilter(reach * 0.38f, BlurMaskFilter.Blur.NORMAL));
        paint.setColor(Scene.alpha(light, 0.75f * strength));
        c.drawText(text, x, y, paint);
        paint.setMaskFilter(new BlurMaskFilter(Math.max(0.5f, reach * 0.125f), BlurMaskFilter.Blur.NORMAL));
        paint.setColor(Scene.alpha(Scene.mix(light, colour, 0.5f), 0.8f * strength));
        c.drawText(text, x, y, paint);
        paint.setMaskFilter(null);
        paint.setColor(was);
    }
}
