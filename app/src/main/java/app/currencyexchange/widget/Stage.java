package app.currencyexchange.widget;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.LruCache;


/**
 * The panels the widgets sit on, with no grain. The Sound widget's is a stage ({@link #paint}): night glass lit by
 * two washes of the light (a strong one from the top right, a faint one low on the left, swelling with the bass) and
 * a hairline edge; its wall of lamps lights its own fog.
 *
 * The other widgets' panels are frosted glass ({@link #glassPanel}), after the glass references: the same washes of
 * the light, a haze in the air ({@link #haze}), a milky, smoky frost with a soft gloss towards the top, and warm
 * lights far behind the glass, out of focus ({@link #glass}), no brighter overall than the night they frost. The
 * light is the colour of what's playing ({@link Ambience}), so all the widgets share it.
 */
final class Stage {
    private Stage() {}

    /** The night glass, as on the Sound widget. */
    static final int NIGHT = 0xF0070A10;
    static final int EDGE = 0x2EAADCFF;
    /** The glass: how strong the lights behind it are, and its frost and gloss. */
    private static final float LIGHTS = 0.15f, FROST = 0.1f, GLOSS = 0.09f;

    private static final LruCache<String, Bitmap> CACHE = new LruCache<>(10);

    /**
     * The light scattered in the air of a panel: a faint warmth of the light over the night, and two soft, wide banks
     * of fog lit by it, one high on the left, one low on the right. Draw it inside the panel's clip.
     */
    static void haze(Canvas c, RectF box, Scene scene) {
        Paint p = new Paint(Paint.DITHER_FLAG);
        p.setColor(Scene.alpha(scene.deep, 0.035f));
        c.drawRect(box, p);
        float w = box.width(), h = box.height();
        bank(c, p, scene, box.left + w * 0.3f, box.top + h * 0.36f, w * 0.75f, h * 0.28f, 0.07f);
        bank(c, p, scene, box.left + w * 0.72f, box.top + h * 0.78f, w * 0.7f, h * 0.24f, 0.055f);
    }

    /** One bank of fog: a wide, soft oval of the light, {@code strength} strong at its middle. */
    private static void bank(Canvas c, Paint p, Scene scene, float x, float y, float rx, float ry, float strength) {
        android.graphics.Matrix m = new android.graphics.Matrix();
        RadialGradient g = new RadialGradient(0, 0, 1, Scene.alpha(scene.deep, strength), scene.deep & 0x00FFFFFF,
                Shader.TileMode.CLAMP);
        m.setScale(rx, ry);
        m.postTranslate(x, y);
        g.setLocalMatrix(m);
        p.setShader(g);
        c.drawRect(x - rx, y - ry, x + rx, y + ry, p);
        p.setShader(null);
    }

    /**
     * Frosted glass, as in the glass references: lights far behind the glass, out of focus (a few soft, wide blobs of
     * the light, one paler, one deeper), seen through a milky, smoky frost, with a soft gloss towards the top where
     * the glass catches the room's light. Faint enough that what's on the panel reads over it. Draw it inside the
     * panel's clip, after its haze.
     */
    static void glass(Canvas c, RectF box, Scene scene) {
        float w = box.width(), h = box.height(), s = Math.min(w, h);
        Paint p = new Paint(Paint.DITHER_FLAG);
        // x, y (share of the panel), radius (share of its short side), stretch across, strength, tone
        float[][] lights = {{0.08f, 0.9f, 0.85f, 1.5f, LIGHTS * 0.34f, 0}, {0.55f, 0.06f, 0.6f, 1.7f, LIGHTS * 0.2f, 1},
                {0.95f, 0.72f, 0.7f, 1.3f, LIGHTS * 0.3f, 2}, {0.34f, 0.5f, 0.42f, 1.1f, LIGHTS * 0.14f, 1},
                {0.72f, 0.3f, 0.3f, 1f, LIGHTS * 0.12f, 0}};
        for (float[] l : lights) {
            int colour = l[5] == 1 ? Scene.mix(scene.key, 0xFFFFFFFF, 0.5f) : l[5] == 2 ? scene.deep : scene.key;
            float x = box.left + w * l[0], y = box.top + h * l[1], r = s * l[2];
            android.graphics.Matrix m = new android.graphics.Matrix();
            RadialGradient g = new RadialGradient(0, 0, 1, new int[]{Scene.alpha(colour, l[4]), Scene.alpha(colour, l[4] * 0.55f),
                    colour & 0x00FFFFFF}, new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP);
            m.setScale(r * l[3], r);
            m.postTranslate(x, y);
            g.setLocalMatrix(m);
            p.setShader(g);
            c.drawRect(x - r * l[3], y - r, x + r * l[3], y + r, p);
        }
        p.setShader(null);
        p.setColor(Scene.alpha(0xFF9C8A76, FROST)); // the frost: milky and smoky
        c.drawRect(box, p);
        p.setShader(new LinearGradient(0, box.top, 0, box.top + h * 0.5f, Scene.alpha(0xFFFFF2DE, GLOSS), 0x00FFF2DE,
                Shader.TileMode.CLAMP));
        c.drawRect(box, p); // the gloss
        p.setShader(null);
    }

    /**
     * A frosted glass panel ({@code radii}, 8 values in pixels): the washes of the light, its haze, and
     * the glass. The canvas is left clipped to the shape for drawing more inside it (call {@code c.restore()} after).
     */
    static void glassPanel(Canvas c, RectF box, float[] radii, Scene scene, int fillLeft, int fillRight) {
        lit(c, box, radii, scene, 0f, fillLeft, fillRight);
        haze(c, box, scene);
        glass(c, box, scene);
    }

    /**
     * Paints the panel into {@code box} on {@code c}: its rounded shape ({@code radii}, 8 values in pixels as for
     * {@link Path#addRoundRect(RectF, float[], Path.Direction)}), filled with the night glass ({@code fillLeft} to
     * {@code fillRight}), the washes of the light, and the edge if {@code edgePx} > 0. The canvas is left
     * clipped to the shape when {@code keepClip}, for drawing more inside it (call {@code c.restore()} after).
     */
    static void paint(Canvas c, RectF box, float[] radii, Scene scene, float bass, int fillLeft, int fillRight,
                      float edgePx, boolean keepClip) {
        lit(c, box, radii, scene, bass, fillLeft, fillRight);
        if (!keepClip) c.restore();
        if (edgePx > 0) edge(c, box, radii, edgePx);
    }

    /** The night lit by the washes of the light, clipped to its shape (left clipped). */
    private static void lit(Canvas c, RectF box, float[] radii, Scene scene, float bass, int fillLeft, int fillRight) {
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
    }

    /** The hairline edge of a panel, just inside {@code box}, in the Sound widget's cool white. */
    static void edge(Canvas c, RectF box, float[] radii, float px) {
        edge(c, box, radii, px, EDGE);
    }

    /** The hairline edge of a panel, just inside {@code box}, in {@code colour}. */
    static void edge(Canvas c, RectF box, float[] radii, float px, int colour) {
        Paint e = new Paint(Paint.ANTI_ALIAS_FLAG);
        e.setStyle(Paint.Style.STROKE);
        e.setStrokeWidth(px);
        e.setColor(colour);
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
     * A frosted glass panel picture, {@code wDp} × {@code hDp} at {@code px} pixels per dp, with no edge
     * (the widget's own crisp edge goes over it): for the backgrounds of the Calendar and the Currency header, drawn
     * once per light and size.
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
        Canvas c = new Canvas(b);
        RectF box = new RectF(0, 0, w, h);
        glassPanel(c, box, radii, scene, fillLeft, fillRight);
        c.restore();
        CACHE.put(key, b);
        return b;
    }
}
