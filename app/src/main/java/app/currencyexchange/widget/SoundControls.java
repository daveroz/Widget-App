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
 * The pictures of the Sound widget's controls, in the light's colours: play / pause lit like the
 * launcher's chosen tab (a touch of the light on the glass, a glowing edge, an LED-white symbol),
 * previous and next as quiet symbols, and the progress line, a hairline lit from the left in the light
 * up to where the song is. The buttons are drawn once per light and state.
 */
final class SoundControls {
    private SoundControls() {}

    /** Pixels per dp the pictures are drawn at. */
    private static final float PX = 3f;
    /** Height of the progress picture, in dp. */
    static final int BAR_DP = 28;

    private static final LruCache<String, Bitmap> CACHE = new LruCache<>(12);

    /** The play / pause button: a lit circle with the symbol for what a tap does. */
    static Bitmap playButton(Scene scene, boolean playing, int sizeDp) {
        String key = "p|" + scene.key + "|" + playing + "|" + sizeDp;
        Bitmap hit = CACHE.get(key);
        if (hit != null) return hit;
        int s = Math.round(sizeDp * PX);
        Bitmap b = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        float cx = s / 2f, glowR = 3 * PX, r = cx - glowR - PX;
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        // the soft glow just outside its edge
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(glowR);
        p.setColor(Scene.alpha(scene.key, 0.45f));
        p.setMaskFilter(new BlurMaskFilter(glowR, BlurMaskFilter.Blur.NORMAL));
        c.drawCircle(cx, cx, r, p);
        p.setMaskFilter(null);
        // a touch of the light on the glass, and its edge
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xF00B1220);
        c.drawCircle(cx, cx, r, p);
        p.setColor(Scene.alpha(scene.key, 0.18f));
        c.drawCircle(cx, cx, r, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(PX);
        p.setColor(Scene.alpha(scene.key, 0.75f));
        c.drawCircle(cx, cx, r - PX / 2, p);
        // the symbol, LED-white
        p.setStyle(Paint.Style.FILL);
        p.setColor(scene.led);
        float u = r * 0.36f;
        if (playing) { // pause: two bars
            float bw = u * 0.55f;
            c.drawRoundRect(cx - u * 0.75f - bw / 2, cx - u, cx - u * 0.75f + bw / 2, cx + u, bw / 3, bw / 3, p);
            c.drawRoundRect(cx + u * 0.75f - bw / 2, cx - u, cx + u * 0.75f + bw / 2, cx + u, bw / 3, bw / 3, p);
        } else { // play: a triangle, nudged right so it looks centred
            Path t = new Path();
            t.moveTo(cx - u * 0.7f, cx - u * 1.05f);
            t.lineTo(cx + u * 1.15f, cx);
            t.lineTo(cx - u * 0.7f, cx + u * 1.05f);
            t.close();
            c.drawPath(t, p);
        }
        CACHE.put(key, b);
        return b;
    }

    /** Previous or next: a triangle and a bar, quiet, in the soft text colour. */
    static Bitmap skip(boolean next, int sizeDp) {
        String key = "s|" + next + "|" + sizeDp;
        Bitmap hit = CACHE.get(key);
        if (hit != null) return hit;
        int s = Math.round(sizeDp * PX);
        Bitmap b = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(Theme.TEXT_SOFT);
        float cx = s / 2f, u = s * 0.18f;
        float dir = next ? 1 : -1;
        Path t = new Path();
        t.moveTo(cx - dir * u * 0.9f, cx - u);
        t.lineTo(cx + dir * u * 0.7f, cx);
        t.lineTo(cx - dir * u * 0.9f, cx + u);
        t.close();
        c.drawPath(t, p);
        float bx = cx + dir * u * 0.95f, bw = u * 0.32f;
        c.drawRoundRect(bx - bw / 2, cx - u, bx + bw / 2, cx + u, bw / 2, bw / 2, p);
        CACHE.put(key, b);
        return b;
    }

    /** The progress line, {@code widthDp} wide: a hairline lit from the left up to {@code fraction}, with a soft glow. */
    static Bitmap bar(Scene scene, float fraction, int widthDp) {
        float px = 2f;
        int w = Math.max(8, Math.round(widthDp * px)), h = Math.round(BAR_DP * px);
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        float y = h / 2f, x0 = 3 * px, x1 = w - 3 * px;
        float head = x0 + (x1 - x0) * Math.max(0f, Math.min(1f, fraction));
        Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
        line.setStrokeCap(Paint.Cap.ROUND);
        line.setColor(Theme.BORDER_STRONG);
        line.setStrokeWidth(Math.max(1f, 1.4f * px));
        c.drawLine(x0, y, x1, y, line);
        if (head > x0) {
            line.setColor(Scene.alpha(scene.key, 0.22f));
            line.setStrokeWidth(6 * px);
            c.drawLine(x0, y, head, y, line); // the glow around it
            line.setShader(new LinearGradient(x0, 0, Math.max(x0 + 1, head), 0, Scene.alpha(scene.key, 0.5f), scene.key,
                    Shader.TileMode.CLAMP));
            line.setStrokeWidth(2.4f * px);
            c.drawLine(x0, y, head, y, line);
            line.setShader(null);
        }
        // where the song is: an LED-white point with a little glow, which a tap on the line moves
        line.setColor(Scene.alpha(scene.key, 0.35f));
        c.drawCircle(head, y, 5.5f * px, line);
        line.setColor(scene.led);
        c.drawCircle(head, y, 3.4f * px, line);
        return b;
    }

    static String clock(long ms) {
        long s = Math.max(0, ms / 1000);
        return s >= 3600 ? String.format(Locale.ROOT, "%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60)
                : String.format(Locale.ROOT, "%d:%02d", s / 60, s % 60);
    }
}
