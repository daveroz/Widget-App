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
 * The panels the widgets sit on, in the Glass look ({@link Look}), after the cypher samples: frosted, smoky glass with
 * the light far behind it, out of focus ({@link #night}): soft blurred shapes of the light, like windows of light seen
 * through frosted glass, under a milky veil of smoke warmed by them. The edge is lit in the light rather than drawn as a
 * solid border ({@link #rim}), swept in from the top left corner. The light is the colour of what's playing
 * ({@link Ambience}), so every widget shares it. No gloss, no grain.
 */
final class Stage {
    private Stage() {}

    /** The black under the glass: as deep as an OLED screen's unlit pixels, with a breath of blue so it isn't flat. */
    static final int NIGHT = 0xFF010203;

    private static final int RED = 0xFFFF4A3A, BLUE = 0xFF3AA8FF;
    /** The smoke's own colour, a warm grey, before the light warms it. */
    private static final int SMOKE = 0xFF8C8279;
    private static final LruCache<String, Bitmap> CACHE = new LruCache<>(14);
    /** The glass, drawn once per light, size and pattern (the Sound widget lays it under every frame). */
    private static final LruCache<String, Bitmap> GLASS = new LruCache<String, Bitmap>(12 * 1024 * 1024) {
        @Override
        protected int sizeOf(String key, Bitmap b) { return b.getByteCount(); }
    };

    /**
     * The lights behind the glass: x, y (their middle, as a share of the panel), width and height (as a share of the
     * panel's size), corner (a share of their short side), blur (a share of the panel's size), strength, and tone
     * (0 the light's colour, 1 a paler, 2 a deeper shade, 3 a warm neutral). After the cypher samples: one big soft
     * glow low on the left, a tall one on the right, a wide pale one along the top, a few small brighter ones.
     */
    private static final float[][] LIGHTS = {
            {0.16f, 0.66f, 0.62f, 0.75f, 0.3f, 0.2f, 0.42f, 0}, {0.82f, 0.42f, 0.36f, 0.95f, 0.25f, 0.18f, 0.36f, 2},
            {0.5f, 0.06f, 0.7f, 0.32f, 0.3f, 0.2f, 0.2f, 1}, {0.4f, 0.8f, 0.22f, 0.22f, 0.35f, 0.1f, 0.3f, 1},
            {0.97f, 0.94f, 0.5f, 0.5f, 0.3f, 0.2f, 0.24f, 0}, {0.64f, 0.66f, 0.16f, 0.16f, 0.4f, 0.08f, 0.22f, 3},
            {0.28f, 0.2f, 0.18f, 0.24f, 0.35f, 0.1f, 0.16f, 3}};

    /**
     * A panel: its rounded shape ({@code radii}, 8 values in pixels) filled with {@code fill}, behind frosted smoky
     * glass: the phone's wallpaper behind it, blurred ({@code behind}, see {@link Backdrop}; null or unreadable: lights
     * in the song's colour and the wallpaper's), the song's light far behind it, which swells with {@code bass} (0 to 1,
     * the Sound widget's), and the light of the bright things in front falling on the glass ({@code spills}, in pixels
     * inside the box). {@code pattern} moves the song's lights about, so panels side
     * by side (the currency rows) don't repeat. The canvas is left clipped to the shape for drawing more inside it (call
     * {@code c.restore()} after).
     */
    static void night(Canvas c, RectF box, float[] radii, Scene scene, int fill, float bass, int pattern,
                      Backdrop.Behind behind, Spill[] spills) {
        Path shape = new Path();
        shape.addRoundRect(box, radii, Path.Direction.CW);
        c.save();
        c.clipPath(shape);
        Paint p = new Paint(Paint.DITHER_FLAG | Paint.FILTER_BITMAP_FLAG);
        p.setColor(fill);
        c.drawRect(box, p);
        c.drawBitmap(glass(scene, Math.round(box.width()), Math.round(box.height()), pattern, behind), box.left, box.top, p);
        if (bass > 0.01f) { // the light behind the glass swells with the bass
            float w = box.width(), h = box.height(), big = Math.max(w, h);
            p.setShader(new RadialGradient(box.left + w * 0.8f, box.top + h * 0.3f, big * 0.6f,
                    Scene.alpha(scene.deep, 0.12f * Math.min(1f, bass)), scene.deep & 0x00FFFFFF, Shader.TileMode.CLAMP));
            c.drawRect(box, p);
            p.setShader(null);
        }
        spill(c, box.left, box.top, spills, 1f);
    }

    static void night(Canvas c, RectF box, float[] radii, Scene scene, int fill, float bass, Backdrop.Behind behind) {
        night(c, box, radii, scene, fill, bass, 0, behind, null);
    }

    /**
     * The light of a bright thing in front falling on the glass behind it, as on the cypher samples: a wide, soft glow
     * round (x, y), reaching {@code r}, in {@code colour}, {@code strength} strong at its middle.
     */
    static final class Spill {
        final float x, y, r, strength;
        final int colour;

        Spill(float x, float y, float r, int colour, float strength) {
            this.x = x;
            this.y = y;
            this.r = r;
            this.colour = colour;
            this.strength = strength;
        }

        @Override
        public String toString() {
            return x + "," + y + "," + r + "," + colour + "," + strength;
        }
    }

    /** Draws the light spills, their units {@code unit} pixels, from (x0, y0). */
    static void spill(Canvas c, float x0, float y0, Spill[] spills, float unit) {
        if (spills == null) return;
        Paint p = new Paint(Paint.DITHER_FLAG);
        for (Spill s : spills) {
            float x = x0 + s.x * unit, y = y0 + s.y * unit, r = s.r * unit;
            p.setShader(new RadialGradient(x, y, r, new int[]{Scene.alpha(s.colour, s.strength),
                    Scene.alpha(s.colour, s.strength * 0.4f), s.colour & 0x00FFFFFF}, new float[]{0f, 0.45f, 1f},
                    Shader.TileMode.CLAMP));
            c.drawCircle(x, y, r, p);
        }
        p.setShader(null);
    }

    /**
     * Frosted smoky glass, {@code w} × {@code h} pixels: the wallpaper behind it, blurred and dimmed (more over a bright
     * one, so what's on the glass reads), or without it, soft lights in the wallpaper's colours; the song's lights far
     * behind, blurred out of focus; then the smoke, a milky veil warmed by the light, thicker towards the bottom left
     * where the light diffuses through it.
     */
    private static Bitmap glass(Scene scene, int w, int h, int pattern, Backdrop.Behind behind) {
        w = Math.max(2, w);
        h = Math.max(2, h);
        String key = scene.tag() + "|" + w + "x" + h + "|" + pattern + "|" + (behind == null ? "-" : behind.key);
        Bitmap hit = GLASS.get(key);
        if (hit != null) return hit;
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
        boolean wallpaper = behind != null && behind.blur != null;
        if (wallpaper) { // the wallpaper behind the glass, blurred, in its place on the screen, and dimmed
            android.graphics.Matrix m = new android.graphics.Matrix();
            m.setRectToRect(behind.region, new RectF(0, 0, w, h), android.graphics.Matrix.ScaleToFit.FILL);
            c.drawBitmap(behind.blur, m, p);
            c.drawColor(Scene.alpha(0xFF000000, Math.min(0.82f, 0.36f + 0.46f * behind.shade)));
        }
        int[] wall = behind == null ? null : behind.colours;
        float size = (float) Math.sqrt(w * (float) h) * 0.75f, strength = Look.get().pool * (wallpaper ? 0.5f : 1f);
        int pale = Scene.mix(scene.key, 0xFFFFFFFF, 0.45f);
        int warm = wall != null ? Scene.mix(wall[0], scene.key, 0.25f) : Scene.mix(scene.key, 0xFFE8D6BC, 0.6f);
        if (wall != null) pale = Scene.mix(pale, wall[1], 0.4f);
        RectF r = new RectF();
        for (float[] l : LIGHTS) {
            float x = l[0], y = l[1];
            if (pattern == 1) x = 1f - x; // mirrored
            else if (pattern == 2) x = (x + 0.37f) % 1.05f; // moved along
            int colour = l[7] == 1 ? pale : l[7] == 2 ? scene.deep : l[7] == 3 ? warm : scene.key;
            float lw = size * l[2], lh = size * l[3], corner = Math.min(lw, lh) * l[4];
            float cx = w * x, cy = h * y;
            r.set(cx - lw / 2, cy - lh / 2, cx + lw / 2, cy + lh / 2);
            p.setColor(Scene.alpha(colour, Math.min(1f, l[6] * strength)));
            p.setMaskFilter(new BlurMaskFilter(Math.max(1f, size * l[5]), BlurMaskFilter.Blur.NORMAL));
            c.drawRoundRect(r, corner, corner, p);
        }
        p.setMaskFilter(null);
        // the smoke: a milky veil over it all, warmed by the light, thicker towards the bottom left
        int smoke = Scene.mix(SMOKE, scene.key, 0.22f);
        p.setColor(Scene.alpha(smoke, wallpaper ? 0.07f : 0.1f));
        c.drawRect(0, 0, w, h, p);
        p.setShader(new android.graphics.LinearGradient(0, h, w * 0.7f, 0, Scene.alpha(smoke, 0.09f), smoke & 0x00FFFFFF,
                Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        p.setShader(null);
        GLASS.put(key, b);
        return b;
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
     * A panel picture, {@code wDp} × {@code hDp} at {@code px} pixels per dp: its frosted glass over {@code behind} (see
     * {@link #night}), the light of the bright things in front falling on it ({@code spillsDp}, in dp), and its lit
     * edge (when {@code edged}). Drawn once per light, backdrop, look and size.
     */
    static Bitmap panel(Scene scene, int wDp, int hDp, float px, float[] radiiDp, int fill, Backdrop.Behind behind,
                        Spill[] spillsDp, boolean edged) {
        StringBuilder k = new StringBuilder("p|").append(scene.tag()).append('|').append(wDp).append('x').append(hDp)
                .append('|').append(px).append('|').append(fill).append('|').append(edged).append('|')
                .append(behind == null ? "-" : behind.key);
        for (float r : radiiDp) k.append(',').append(r);
        if (spillsDp != null) for (Spill sp : spillsDp) k.append(';').append(sp);
        String key = k.toString();
        Bitmap hit = CACHE.get(key);
        if (hit != null) return hit;
        int w = Math.max(2, Math.round(wDp * px)), h = Math.max(2, Math.round(hDp * px));
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        float[] radii = new float[8];
        for (int i = 0; i < 8; i++) radii[i] = radiiDp[i] * px;
        Canvas c = new Canvas(b);
        RectF box = new RectF(0, 0, w, h);
        night(c, box, radii, scene, fill, 0f, 0, behind, null);
        spill(c, 0, 0, spillsDp, px);
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
