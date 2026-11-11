package app.currencyexchange.widget;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.LruCache;

import java.util.Random;

/**
 * The panel every widget sits on, as on the Deco launcher's stage: night glass, lit by two washes of the light (a
 * strong one from the top right, a faint one low on the left), with a fine film grain over it and a hairline edge.
 * The light is the colour of what's playing ({@link Ambience}), so all the widgets share it. The Sound widget's
 * washes also swell with the bass.
 */
final class Stage {
    private Stage() {}

    /** The night glass, as on the Sound widget. */
    static final int NIGHT = 0xF0070A10;
    static final int EDGE = 0x2EAADCFF;

    private static final Paint GRAIN = new Paint();
    private static final LruCache<String, Bitmap> CACHE = new LruCache<>(10);

    static {
        int size = 96;
        int[] pixels = new int[size * size];
        Random random = new Random(7);
        for (int i = 0; i < pixels.length; i++) {
            float v = random.nextFloat();
            int a = v > 0.5f ? Math.round((v - 0.5f) * 2 * 12) : 0;
            pixels[i] = (a << 24) | 0x00FFFFFF;
        }
        GRAIN.setShader(new BitmapShader(Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888),
                Shader.TileMode.REPEAT, Shader.TileMode.REPEAT));
    }

    /**
     * Paints the panel into {@code box} on {@code c}: its rounded shape ({@code radii}, 8 values in pixels as for
     * {@link Path#addRoundRect(RectF, float[], Path.Direction)}), filled with the night glass ({@code fillLeft} to
     * {@code fillRight}), the washes of the light, the grain, and the edge if {@code edgePx} > 0. The canvas is left
     * clipped to the shape when {@code keepClip}, for drawing more inside it (call {@code c.restore()} after).
     */
    static void paint(Canvas c, RectF box, float[] radii, Scene scene, float bass, int fillLeft, int fillRight,
                      float edgePx, boolean keepClip) {
        Path shape = new Path();
        shape.addRoundRect(box, radii, Path.Direction.CW);
        c.save();
        c.clipPath(shape);
        Paint p = new Paint(Paint.DITHER_FLAG);
        if (fillLeft == fillRight) p.setColor(fillLeft);
        else p.setShader(new LinearGradient(box.left, 0, box.right, 0, fillLeft, fillRight, Shader.TileMode.CLAMP));
        c.drawRect(box, p);
        float w = box.width(), h = box.height(), big = Math.max(w, h);
        p.setShader(new RadialGradient(box.left + w * 0.86f, box.top, big * 0.62f,
                new int[]{Scene.alpha(scene.key, 0.16f + 0.08f * bass), Scene.alpha(scene.key, 0.05f), scene.key & 0x00FFFFFF},
                new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(box, p);
        p.setShader(new RadialGradient(box.left + w * 0.12f, box.bottom, big * 0.5f,
                new int[]{Scene.alpha(scene.key, 0.07f + 0.05f * bass), scene.key & 0x00FFFFFF}, null, Shader.TileMode.CLAMP));
        c.drawRect(box, p);
        c.drawRect(box, GRAIN);
        if (!keepClip) c.restore();
        if (edgePx > 0) edge(c, box, radii, edgePx);
    }

    /** The hairline edge of a panel, just inside {@code box}. */
    static void edge(Canvas c, RectF box, float[] radii, float px) {
        Paint e = new Paint(Paint.ANTI_ALIAS_FLAG);
        e.setStyle(Paint.Style.STROKE);
        e.setStrokeWidth(px);
        e.setColor(EDGE);
        float in = px / 2;
        float[] r = new float[8];
        for (int i = 0; i < 8; i++) r[i] = Math.max(0, radii[i] - in);
        Path edge = new Path();
        edge.addRoundRect(new RectF(box.left + in, box.top + in, box.right - in, box.bottom - in), r, Path.Direction.CW);
        c.drawPath(edge, e);
    }

    /** Eight equal corner radii. */
    static float[] round(float r) {
        return new float[]{r, r, r, r, r, r, r, r};
    }

    /** Rounded top corners only. */
    static float[] roundTop(float r) {
        return new float[]{r, r, r, r, 0, 0, 0, 0};
    }

    /**
     * A panel picture, {@code wDp} × {@code hDp} at {@code px} pixels per dp, with no edge (the widget's own crisp
     * edge goes over it): for the backgrounds of the Calendar, the Currency header and rows, drawn once per light
     * and size.
     */
    static Bitmap panel(Scene scene, int wDp, int hDp, float px, float[] radiiDp, int fillLeft, int fillRight) {
        StringBuilder k = new StringBuilder().append(scene.key).append('|').append(wDp).append('x').append(hDp)
                .append('|').append(px).append('|').append(fillLeft).append('|').append(fillRight);
        for (float r : radiiDp) k.append(',').append(r);
        String key = k.toString();
        Bitmap hit = CACHE.get(key);
        if (hit != null) return hit;
        int w = Math.max(2, Math.round(wDp * px)), h = Math.max(2, Math.round(hDp * px));
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        float[] radii = new float[8];
        for (int i = 0; i < 8; i++) radii[i] = radiiDp[i] * px;
        paint(new Canvas(b), new RectF(0, 0, w, h), radii, scene, 0f, fillLeft, fillRight, 0f, false);
        CACHE.put(key, b);
        return b;
    }
}
