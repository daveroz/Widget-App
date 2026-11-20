package app.currencyexchange.widget;

import android.graphics.Bitmap;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.util.LruCache;

import java.util.Locale;

/**
 * The pictures of the Music widget's controls, in the light's colours: play / pause a solid button in the light's
 * colour; previous and next round icon buttons, dark and solid, their symbols pale; and the progress line, a hairline
 * with a tick at each end, lit from the left in the light up to where the song is (the only place the song's progress
 * shows). The buttons are drawn once per light, look and state.
 */
final class SoundControls {
    private SoundControls() {}

    /** Pixels per dp the pictures are drawn at. */
    private static final float PX = 3f;
    /** Height of the progress picture, in dp. */
    static final int BAR_DP = 34;
    /** The line stops this far (dp) short of each end of its picture, so the point fits at the start and the end. */
    static final float END_DP = 9f;

    /** How far through the song a point {@code x} dp along a line picture {@code widthDp} wide stands for. */
    static float fractionAt(float x, int widthDp) {
        return Math.max(0f, Math.min(1f, (x - END_DP) / Math.max(1f, widthDp - 2 * END_DP)));
    }

    private static final LruCache<String, Bitmap> CACHE = new LruCache<>(12);

    /**
     * The play / pause button: a solid circle in the light's colour, a shade lighter to the right, as on the solid button
     * reference (no bloom), the symbol for what a tap does in white (near black on a light colour), and a small light
     * strike where its gloss catches the light ({@link Optics#star}).
     */
    static Bitmap playButton(Scene scene, boolean playing, int sizeDp) {
        String key = "p|" + scene.tag() + "|" + playing + "|" + sizeDp;
        Bitmap hit = CACHE.get(key);
        if (hit != null) return hit;
        int s = Math.round(sizeDp * PX);
        Bitmap b = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        float cx = s / 2f, r = cx - 2 * PX;
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new LinearGradient(cx - r, 0, cx + r, 0, Scene.mix(scene.key, 0xFF000000, 0.1f),
                Scene.mix(scene.key, 0xFFFFFFFF, 0.12f), Shader.TileMode.CLAMP));
        c.drawCircle(cx, cx, r, p);
        p.setShader(null);
        p.setColor(Theme.onLit(scene.key));
        c.drawPath(symbol(cx, r * 0.36f, playing), p);
        // where its gloss catches the light, up at the top left: a small light strike
        float g = r * 0.72f * 0.7071f;
        Optics.star(c, cx - g, cx - g, Math.min(5 * PX, (cx - g) / 1.6f), PX, scene.hot, 0.5f);
        CACHE.put(key, b);
        return b;
    }

    /** Pause (two bars) or play (a triangle, nudged right so it looks centred), {@code u} the half-height. */
    private static Path symbol(float cx, float u, boolean pause) {
        Path symbol = new Path();
        if (pause) {
            float bw = u * 0.55f;
            symbol.addRoundRect(cx - u * 0.75f - bw / 2, cx - u, cx - u * 0.75f + bw / 2, cx + u, bw / 3, bw / 3, Path.Direction.CW);
            symbol.addRoundRect(cx + u * 0.75f - bw / 2, cx - u, cx + u * 0.75f + bw / 2, cx + u, bw / 3, bw / 3, Path.Direction.CW);
        } else {
            symbol.moveTo(cx - u * 0.7f, cx - u * 1.05f);
            symbol.lineTo(cx + u * 1.15f, cx);
            symbol.lineTo(cx - u * 0.7f, cx + u * 1.05f);
            symbol.close();
        }
        return symbol;
    }

    /**
     * Previous or next: a round icon button, as on the icon reference: a dark solid circle (a deep, dark shade of the
     * light) with the symbol (a triangle and a bar) in a pale shade of it.
     */
    static Bitmap skip(Scene scene, boolean next, int sizeDp) {
        String key = "s|" + scene.tag() + "|" + next + "|" + sizeDp;
        Bitmap hit = CACHE.get(key);
        if (hit != null) return hit;
        int s = Math.round(sizeDp * PX);
        Bitmap b = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        float cx = s / 2f, u = s * 0.15f;
        p.setColor(Theme.iconFill(scene));
        c.drawCircle(cx, cx, cx - 3 * PX, p);
        float dir = next ? 1 : -1;
        Path symbol = new Path();
        symbol.moveTo(cx - dir * u * 0.9f, cx - u);
        symbol.lineTo(cx + dir * u * 0.7f, cx);
        symbol.lineTo(cx - dir * u * 0.9f, cx + u);
        symbol.close();
        float bx = cx + dir * u * 0.95f, bw = u * 0.32f;
        symbol.addRoundRect(bx - bw / 2, cx - u, bx + bw / 2, cx + u, bw / 2, bw / 2, Path.Direction.CW);
        p.setColor(Theme.iconGlyph(scene));
        c.drawPath(symbol, p);
        CACHE.put(key, b);
        return b;
    }

    /**
     * The progress line, {@code widthDp} wide: a hairline lit from the left up to {@code fraction}, blooming into the
     * haze, and where the song is, an LED-white point in a glowing ring of the light, with a light strike on it.
     */
    static Bitmap bar(Scene scene, float fraction, int widthDp) {
        float px = 2f;
        int w = Math.max(8, Math.round(widthDp * px)), h = Math.round(BAR_DP * px);
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        float y = h / 2f, x0 = END_DP * px, x1 = w - END_DP * px; // room for the point at either end
        float head = x0 + (x1 - x0) * Math.max(0f, Math.min(1f, fraction));
        Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
        line.setStrokeCap(Paint.Cap.ROUND);
        line.setColor(Scene.alpha(scene.line, 0.4f));
        line.setStrokeWidth(Math.max(1f, 1.4f * px));
        c.drawLine(x0, y, x1, y, line);
        // a tick at each end, so the line reads as a scale from the start of the song to its end
        line.setStrokeWidth(Math.max(1f, px));
        c.drawLine(x0, y - 4 * px, x0, y + 4 * px, line);
        c.drawLine(x1, y - 4 * px, x1, y + 4 * px, line);
        line.setStrokeWidth(Math.max(1f, 1.4f * px));
        float bloom = Look.get().bloom;
        if (head > x0) {
            line.setColor(Scene.alpha(scene.deep, 0.5f * bloom));
            line.setStrokeWidth(5 * px);
            line.setMaskFilter(new BlurMaskFilter(3.5f * px, BlurMaskFilter.Blur.NORMAL));
            c.drawLine(x0, y, head, y, line); // its bloom
            line.setMaskFilter(null);
            line.setShader(new LinearGradient(x0, 0, Math.max(x0 + 1, head), 0, Scene.alpha(scene.key, 0.55f), scene.hot,
                    Shader.TileMode.CLAMP));
            line.setStrokeWidth(2.4f * px);
            c.drawLine(x0, y, head, y, line);
            line.setShader(null);
        }
        // where the song is: a big LED-white point in a glowing ring of the light, which a tap on the line moves
        line.setColor(Scene.alpha(scene.deep, 0.55f * bloom));
        line.setMaskFilter(new BlurMaskFilter(4f * px, BlurMaskFilter.Blur.NORMAL));
        c.drawCircle(head, y, 8f * px, line);
        line.setMaskFilter(null);
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeWidth(1.4f * px);
        line.setColor(Scene.alpha(Scene.mix(scene.key, scene.hot, 0.4f), 0.9f));
        c.drawCircle(head, y, 7f * px, line);
        line.setStyle(Paint.Style.FILL);
        line.setColor(Scene.alpha(scene.hot, 0.8f));
        line.setMaskFilter(new BlurMaskFilter(2f * px, BlurMaskFilter.Blur.NORMAL));
        c.drawCircle(head, y, 5f * px, line);
        line.setMaskFilter(null);
        line.setColor(scene.led);
        c.drawCircle(head, y, 5f * px, line);
        Bitmap out = Lens.fringe(b, px, 0.9f); // the colour fringe of a bright light through a lens
        Optics.star(new Canvas(out), head, y, 13f * px, px, scene.hot, 0.6f); // and the strike a star filter makes of it
        return out;
    }

    static String clock(long ms) {
        long s = Math.max(0, ms / 1000);
        return s >= 3600 ? String.format(Locale.ROOT, "%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60)
                : String.format(Locale.ROOT, "%d:%02d", s / 60, s % 60);
    }
}
