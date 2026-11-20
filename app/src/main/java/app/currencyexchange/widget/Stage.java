package app.currencyexchange.widget;

import android.graphics.Bitmap;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.LruCache;


/**
 * The panels the widgets sit on, in the Glass look ({@link Look}): near OLED black smoked glass with the light far
 * behind it, out of focus ({@link #night}), and an edge lit in the light rather than a solid border ({@link #rim}),
 * swept in from the top left corner. The light is the colour of what's playing ({@link Ambience}), so every widget
 * shares it. No gloss, no grain.
 */
final class Stage {
    private Stage() {}

    /** The panels' black: as deep as an OLED screen's unlit pixels, with a breath of blue so it isn't flat. */
    static final int NIGHT = 0xFF010203;

    private static final int RED = 0xFFFF4A3A, BLUE = 0xFF3AA8FF;
    private static final LruCache<String, Bitmap> CACHE = new LruCache<>(14);

    /**
     * A panel: its rounded shape ({@code radii}, 8 values in pixels) filled with {@code fill}, smoked, with the light
     * behind it, which swells with {@code bass} (0 to 1, the Sound widget's). The canvas is left clipped to the shape
     * for drawing more inside it (call {@code c.restore()} after).
     */
    static void night(Canvas c, RectF box, float[] radii, Scene scene, int fill, float bass) {
        Path shape = new Path();
        shape.addRoundRect(box, radii, Path.Direction.CW);
        c.save();
        c.clipPath(shape);
        Paint p = new Paint(Paint.DITHER_FLAG);
        p.setColor(fill);
        c.drawRect(box, p);
        pool(c, box, scene, bass);
    }

    /** Smoked glass, with the light far behind it, out of focus: a few soft blurs of it (inside the panel's clip). */
    private static void pool(Canvas c, RectF box, Scene scene, float bass) {
        float w = box.width(), h = box.height(), sh = Math.min(w, h);
        float s = 0.12f * Look.get().pool * (1f + 0.6f * bass);
        Paint p = new Paint(Paint.DITHER_FLAG);
        p.setColor(Scene.alpha(0xFF8A8078, 0.03f)); // the smoke
        c.drawRect(box, p);
        int pale = Scene.mix(scene.key, 0xFFFFFFFF, 0.45f);
        // x, y (share of the panel), radius (share of its short side), stretch across, strength, tone
        float[][] lights = {{0.1f, 0.86f, 0.8f, 1.4f, 0.9f, 0}, {0.58f, 0.06f, 0.55f, 1.6f, 0.55f, 1},
                {0.95f, 0.7f, 0.62f, 1.25f, 0.8f, 2}, {0.36f, 0.48f, 0.34f, 1.1f, 0.4f, 1},
                {0.76f, 0.28f, 0.26f, 1f, 0.35f, 0}};
        for (float[] l : lights) {
            int colour = l[5] == 1 ? pale : l[5] == 2 ? scene.deep : scene.key;
            float r = sh * l[2];
            bokeh(c, p, box.left + w * l[0], box.top + h * l[1], r * l[3], r, colour, s * l[4]);
        }
    }

    /** A light far behind the glass, out of focus: a soft disc, nearly even across, its edge melting away. */
    private static void bokeh(Canvas c, Paint p, float x, float y, float rx, float ry, int colour, float strength) {
        android.graphics.Matrix m = new android.graphics.Matrix();
        RadialGradient g = new RadialGradient(0, 0, 1, new int[]{Scene.alpha(colour, strength),
                Scene.alpha(colour, strength * 0.8f), Scene.alpha(colour, strength * 0.3f), colour & 0x00FFFFFF},
                new float[]{0f, 0.45f, 0.75f, 1f}, Shader.TileMode.CLAMP);
        m.setScale(rx, ry);
        m.postTranslate(x, y);
        g.setLocalMatrix(m);
        p.setShader(g);
        c.drawRect(x - rx, y - ry, x + rx, y + ry, p);
        p.setShader(null);
    }

    /**
     * The edge of a panel, just inside {@code box}, lit in the light instead of a solid border: a hairline in the
     * line colour, brightest at the top left corner where the light comes in and sweeping along both edges from there,
     * fading out by the far corner, a soft glow inside the card where it's brightest, and the faint red and blue
     * fringe of a lens (red just outside, blue just inside). {@code px} is pixels per dp.
     */
    static void rim(Canvas c, RectF box, float[] radii, Scene scene, float px) {
        Look look = Look.get();
        float line = Math.max(1f, px);
        float apart = Math.max(1f, look.fringeDp * px); // how far the fringe's colours are pulled apart
        float at = apart + line / 2; // the edge's middle, in from the box: room for the red just outside it
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStyle(Paint.Style.STROKE);

        // the glow inside the card, where the edge is brightest
        Path shape = new Path();
        shape.addRoundRect(box, radii, Path.Direction.CW);
        c.save();
        c.clipPath(shape);
        p.setStrokeWidth(8 * px);
        p.setMaskFilter(new BlurMaskFilter(7 * px, BlurMaskFilter.Blur.NORMAL));
        Path middle = inset(box, radii, at);
        for (Shader s : light(box, scene.deep, 0.32f * look.bloom)) {
            p.setShader(s);
            c.drawPath(middle, p);
        }
        p.setMaskFilter(null);
        c.restore();

        p.setStrokeWidth(line);
        if (look.edgeFringe > 0f) {
            Path outside = inset(box, radii, at - apart), inside = inset(box, radii, at + apart);
            for (Shader s : light(box, RED, look.edgeFringe)) {
                p.setShader(s);
                c.drawPath(outside, p);
            }
            for (Shader s : light(box, BLUE, look.edgeFringe)) {
                p.setShader(s);
                c.drawPath(inside, p);
            }
        }
        for (Shader s : light(box, scene.line, 1f)) {
            p.setShader(s);
            c.drawPath(middle, p);
        }
    }

    /**
     * How the light falls on the edge, in {@code colour}, {@code strength} strong at its brightest: in at the top left
     * corner, sweeping along both edges from there, with a faint answer at the far corner.
     */
    private static Shader[] light(RectF box, int colour, float strength) {
        float reach = (float) Math.hypot(box.width(), box.height());
        return new Shader[]{
                new RadialGradient(box.left, box.top, reach * 0.8f,
                        new int[]{Scene.alpha(colour, 0.8f * strength), Scene.alpha(colour, 0.42f * strength),
                                Scene.alpha(colour, 0.1f * strength), Scene.alpha(colour, 0.04f * strength)},
                        new float[]{0f, 0.2f, 0.5f, 1f}, Shader.TileMode.CLAMP),
                new RadialGradient(box.right, box.bottom, reach * 0.3f,
                        new int[]{Scene.alpha(colour, 0.16f * strength), colour & 0x00FFFFFF},
                        null, Shader.TileMode.CLAMP)};
    }

    /** The rounded outline {@code by} pixels in from {@code box}, its corners following. */
    private static Path inset(RectF box, float[] radii, float by) {
        float[] r = new float[8];
        for (int i = 0; i < 8; i++) r[i] = Math.max(0, radii[i] - by);
        Path path = new Path();
        path.addRoundRect(new RectF(box.left + by, box.top + by, box.right - by, box.bottom - by), r, Path.Direction.CW);
        return path;
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
     * A panel picture, {@code wDp} × {@code hDp} at {@code px} pixels per dp: the black, its pool of the light (when
     * {@code lit}) and its lit edge (when {@code edged}). Drawn once per light, look and size.
     */
    static Bitmap panel(Scene scene, int wDp, int hDp, float px, float[] radiiDp, int fill, boolean lit, boolean edged) {
        StringBuilder k = new StringBuilder("p|").append(scene.tag()).append('|').append(wDp).append('x').append(hDp)
                .append('|').append(px).append('|').append(fill).append('|').append(lit).append(edged);
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
        if (lit) {
            night(c, box, radii, scene, fill, 0f);
        } else {
            Path shape = new Path();
            shape.addRoundRect(box, radii, Path.Direction.CW);
            c.save();
            c.clipPath(shape);
            c.drawColor(fill);
        }
        c.restore();
        if (edged) rim(c, box, radii, scene, px);
        CACHE.put(key, b);
        return b;
    }

    /**
     * Just the lit edge, on a clear picture {@code w} × {@code h} pixels: for laying over a card drawn by views (the
     * Currency widget's clocks) or over a picture redrawn every frame (the Sound widget). Drawn once per light, look and
     * size.
     */
    static Bitmap edge(Scene scene, int w, int h, float[] radii, float px) {
        StringBuilder k = new StringBuilder("e|").append(scene.tag()).append('|').append(w).append('x').append(h)
                .append('|').append(px);
        for (float r : radii) k.append(',').append(r);
        String key = k.toString();
        Bitmap hit = CACHE.get(key);
        if (hit != null) return hit;
        Bitmap b = Bitmap.createBitmap(Math.max(2, w), Math.max(2, h), Bitmap.Config.ARGB_8888);
        rim(new Canvas(b), new RectF(0, 0, b.getWidth(), b.getHeight()), radii, scene, px);
        CACHE.put(key, b);
        return b;
    }
}
