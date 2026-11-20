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
 * The pictures of the Music widget's controls, in the light's colours and lit like light sources in haze (see
 * {@link Glow}): play / pause lit like the launcher's chosen tab (a touch of the light on the glass, a glowing
 * edge, an LED-white symbol); previous and next as quiet symbols; and the progress line, a hairline with a tick at
 * each end, lit from the left in the light up to where the song is. The bright things have the faint colour fringe
 * of a light through a lens ({@link Lens}). The buttons are drawn once per light and state.
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
     * The play / pause button, lit like a lamp: a touch of the light on the glass inside a ring of it, the ring
     * blooming out into the haze (a wide, soft bloom and a tight bright halo), and the symbol for what a tap does
     * LED-white, glowing.
     */
    static Bitmap playButton(Scene scene, boolean playing, int sizeDp) {
        String key = "p|" + scene.key + "|" + playing + "|" + sizeDp;
        Bitmap hit = CACHE.get(key);
        if (hit != null) return hit;
        int s = Math.round(sizeDp * PX);
        Bitmap b = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        float cx = s / 2f, glowR = 3 * PX, r = cx - glowR - PX;
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        // the bloom just outside its edge, and a tight bright halo hugging it
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(glowR * 1.2f);
        p.setColor(Scene.alpha(scene.deep, 0.8f));
        p.setMaskFilter(new BlurMaskFilter(glowR, BlurMaskFilter.Blur.NORMAL));
        c.drawCircle(cx, cx, r, p);
        p.setStrokeWidth(1.5f * PX);
        p.setColor(Scene.alpha(scene.hot, 0.65f));
        p.setMaskFilter(new BlurMaskFilter(PX, BlurMaskFilter.Blur.NORMAL));
        c.drawCircle(cx, cx, r, p);
        p.setMaskFilter(null);
        // a touch of the light on the glass, and its edge
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xF00B1220);
        c.drawCircle(cx, cx, r, p);
        p.setColor(Scene.alpha(scene.deep, 0.22f));
        c.drawCircle(cx, cx, r, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(PX);
        p.setColor(Scene.alpha(Scene.mix(scene.key, scene.hot, 0.4f), 0.85f));
        c.drawCircle(cx, cx, r - PX / 2, p);
        // the symbol, LED-white, with its glow
        float u = r * 0.36f;
        Path symbol = new Path();
        if (playing) { // pause: two bars
            float bw = u * 0.55f;
            symbol.addRoundRect(cx - u * 0.75f - bw / 2, cx - u, cx - u * 0.75f + bw / 2, cx + u, bw / 3, bw / 3, Path.Direction.CW);
            symbol.addRoundRect(cx + u * 0.75f - bw / 2, cx - u, cx + u * 0.75f + bw / 2, cx + u, bw / 3, bw / 3, Path.Direction.CW);
        } else { // play: a triangle, nudged right so it looks centred
            symbol.moveTo(cx - u * 0.7f, cx - u * 1.05f);
            symbol.lineTo(cx + u * 1.15f, cx);
            symbol.lineTo(cx - u * 0.7f, cx + u * 1.05f);
            symbol.close();
        }
        p.setStyle(Paint.Style.FILL);
        p.setColor(Scene.alpha(scene.hot, 0.8f));
        p.setMaskFilter(new BlurMaskFilter(u * 0.35f, BlurMaskFilter.Blur.NORMAL));
        c.drawPath(symbol, p);
        p.setMaskFilter(null);
        p.setColor(scene.led);
        c.drawPath(symbol, p);
        b = Lens.fringe(b, 0.9f * PX, 0.45f); // the faint colour fringe of a bright light through a lens
        CACHE.put(key, b);
        return b;
    }

    /** Previous or next: a triangle and a bar, quiet, in the soft text colour, glowing faintly in the light. */
    static Bitmap skip(Scene scene, boolean next, int sizeDp) {
        String key = "s|" + scene.key + "|" + next + "|" + sizeDp;
        Bitmap hit = CACHE.get(key);
        if (hit != null) return hit;
        int s = Math.round(sizeDp * PX);
        Bitmap b = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        float cx = s / 2f, u = s * 0.18f;
        float dir = next ? 1 : -1;
        Path symbol = new Path();
        symbol.moveTo(cx - dir * u * 0.9f, cx - u);
        symbol.lineTo(cx + dir * u * 0.7f, cx);
        symbol.lineTo(cx - dir * u * 0.9f, cx + u);
        symbol.close();
        float bx = cx + dir * u * 0.95f, bw = u * 0.32f;
        symbol.addRoundRect(bx - bw / 2, cx - u, bx + bw / 2, cx + u, bw / 2, bw / 2, Path.Direction.CW);
        p.setColor(Scene.alpha(scene.deep, 0.45f));
        p.setMaskFilter(new BlurMaskFilter(u * 0.45f, BlurMaskFilter.Blur.NORMAL));
        c.drawPath(symbol, p);
        p.setMaskFilter(null);
        p.setColor(Glow.soft(scene));
        c.drawPath(symbol, p);
        CACHE.put(key, b);
        return b;
    }

    /**
     * The progress line, {@code widthDp} wide: a hairline lit from the left up to {@code fraction}, blooming into the
     * haze, and where the song is, an LED-white point in a glowing ring of the light.
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
        if (head > x0) {
            line.setColor(Scene.alpha(scene.deep, 0.5f));
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
        line.setColor(Scene.alpha(scene.deep, 0.55f));
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
        return Lens.fringe(b, 0.8f * px, 0.4f); // the faint colour fringe of a bright light through a lens
    }

    static String clock(long ms) {
        long s = Math.max(0, ms / 1000);
        return s >= 3600 ? String.format(Locale.ROOT, "%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60)
                : String.format(Locale.ROOT, "%d:%02d", s / 60, s % 60);
    }
}
