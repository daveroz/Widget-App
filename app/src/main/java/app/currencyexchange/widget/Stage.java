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


/**
 * The cards everything sits on, the widgets and the app's alike, after the glowing card reference: near black, with
 * just a hint of the song's colour towards the right ({@link #fill}), a faint sheen along the top, and a hairline edge
 * that's light at the top left and fades round ({@link #rim}). On the widgets the black is mildly see-through
 * ({@link Look#opacity}), so the home screen shows faintly through it; the app's cards are nearly opaque. The light is the
 * colour of what's playing ({@link Ambience}), so everything shares it. Bright things in front can light the card
 * round them ({@link Spill}). Glass on black reads by what it reflects ({@link Optics}): a faint band of the room's
 * light slanting across it, the edge doubled near the light by the glass's thickness, and the corners catching the
 * light; since 2.05 the glass has a fine frost in it ({@link #grain}), after the crystal logo.
 */
final class Stage {
    private Stage() {}

    /** The cards' black (before its opacity): since 2.06 the deep navy of the crystal logo's space, so it isn't flat. */
    private static final int BLACK = 0xFF090B12;
    /** How bright the glass's reflection of the room is, at its brightest: barely there. */
    private static final float REFLECTION = 0.035f;
    /** How strongly the edge catches the light at its top left corner. */
    private static final float CATCH = 0.9f;

    private static final LruCache<String, Bitmap> CACHE = new LruCache<>(14);

    /**
     * A card's fill, in its rounded shape ({@code radii}, 8 values in pixels): the black at {@code opacity}, warming
     * towards the right into a hint of the song's colour, a faint glow of the light at the right, a faint sheen along
     * the top ({@code px} pixels per dp), the light swelling with {@code bass} (0 to 1, the Sound widget's), and the
     * light of bright things in front falling on it ({@code spills}, in pixels inside the box). The canvas is left
     * clipped to the shape for drawing more inside it (call {@code c.restore()} after).
     */
    static void fill(Canvas c, RectF box, float[] radii, Scene scene, float opacity, float px, float bass, Spill[] spills) {
        Path shape = new Path();
        shape.addRoundRect(box, radii, Path.Direction.CW);
        c.save();
        c.clipPath(shape);
        float w = box.width(), h = box.height();
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        int warm = Scene.mix(BLACK, scene.deep, 0.16f); // just a hint of the colour
        p.setShader(new LinearGradient(box.left, 0, box.right, 0, new int[]{Scene.alpha(BLACK, opacity),
                Scene.alpha(Scene.mix(BLACK, warm, 0.18f), opacity), Scene.alpha(warm, opacity)},
                new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(box, p);
        float glow = (0.05f + 0.12f * Math.min(1f, bass)) * Look.get().haze; // a faint glow of the light at the right, swelling with the bass
        p.setShader(new RadialGradient(box.left + w * 0.96f, box.top + h * 0.22f, Math.max(w, h) * 0.6f,
                Scene.alpha(scene.key, glow), scene.key & 0x00FFFFFF, Shader.TileMode.CLAMP));
        c.drawRect(box, p);
        p.setShader(new LinearGradient(0, box.top, 0, box.top + Math.min(h, 44 * px), 0x0FFFFFFF, 0x00FFFFFF,
                Shader.TileMode.CLAMP));
        c.drawRect(box, p); // the sheen
        // the crystal's own light, as in the logo (2.06): an icy sheen in its top left (no glow at the bottom right, 2.15)
        float big = Math.max(w, h);
        p.setShader(new RadialGradient(box.left + w * 0.1f, box.top + h * 0.08f, big * 0.55f,
                Scene.alpha(ICE, 0.055f * opacity), ICE & 0x00FFFFFF, Shader.TileMode.CLAMP));
        c.drawRect(box, p);
        p.setShader(null);
        grain(c, box); // the frost in the glass
        Optics.reflection(c, box, REFLECTION); // the room's light, reflected in the glass
        spill(c, box.left, box.top, spills, 1f);
    }

    /** A widget card's fill ({@link #fill} at the widgets' mild see-through black, {@link Look#opacity}). */
    static void fill(Canvas c, RectF box, float[] radii, Scene scene, float px, float bass, Spill[] spills) {
        fill(c, box, radii, scene, Look.get().opacity, px, bass, spills);
    }

    /**
     * The light of a bright thing in front falling on the card behind it, as on the cypher samples: a wide, soft glow
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
        float haze = Look.get().haze; // the more haze, the more of the light scatters onto the card
        for (Spill s : spills) {
            float x = x0 + s.x * unit, y = y0 + s.y * unit, r = s.r * unit * (float) Math.sqrt(haze);
            float a = Math.min(1f, s.strength * haze);
            p.setShader(new RadialGradient(x, y, r, new int[]{Scene.alpha(s.colour, a),
                    Scene.alpha(s.colour, a * 0.4f), s.colour & 0x00FFFFFF}, new float[]{0f, 0.45f, 1f},
                    Shader.TileMode.CLAMP));
            c.drawCircle(x, y, r, p);
        }
        p.setShader(null);
    }

    /**
     * The cards' edge colour, a warm white: the same whatever is playing, so the edge and the flare at its corner never
     * change colour.
     */
    static final int EDGE = 0xFFF0EBE4;

    /** The icy white the glass's edge takes where it catches the light, as the crystal logo's (2.05): fixed. */
    static final int ICE = 0xFFE2EEFF;
    /** The warm light the far corner catches, as the logo's bottom right: fixed. */
    static final int WARM = 0xFFFFA24E;

    /**
     * A card's edge, just inside {@code box}, as a pane of crystal lit from two sides shows it (2.05, after the crystal
     * logo): a hairline ({@code px} pixels per dp) that's almost nothing along the straight sides (clear glass, never
     * a line in the song's colour), lit icy white round the top left corner (since 2.15 nothing glows at the bottom
     * right); a fainter line a little inside there, the glass's other face seen through its bevel; and a glint at the
     * top left ({@link Optics#catchlight}): the only flare there is. All in fixed colours, whatever is playing.
     */
    static void rim(Canvas c, RectF box, float[] radii, float px) {
        float line = Math.max(1f, px), in = line / 2;
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(line);
        float reach = Math.max(box.width(), box.height()), r = Math.max(radii[0], 8 * px);
        Path path = inset(box, radii, in);
        // all round, almost nothing: the straight sides stay clear glass
        p.setColor(Scene.alpha(ICE, 0.035f));
        c.drawPath(path, p);
        // the top left catches the light, icy, along both edges away from the corner
        float near = Math.min(reach * 0.6f, 7 * r);
        p.setShader(new RadialGradient(box.left, box.top, near, new int[]{Scene.alpha(ICE, 0.5f), Scene.alpha(ICE, 0.16f),
                ICE & 0x00FFFFFF}, new float[]{0f, 0.35f, 1f}, Shader.TileMode.CLAMP));
        c.drawPath(path, p);
        // the bevel's polished slope (2.06, as the logo's): a soft band of light just inside the edge near each lit corner
        Paint slope = new Paint(Paint.ANTI_ALIAS_FLAG);
        slope.setStyle(Paint.Style.STROKE);
        slope.setStrokeWidth(2.6f * px);
        slope.setMaskFilter(new android.graphics.BlurMaskFilter(1.3f * px, android.graphics.BlurMaskFilter.Blur.NORMAL));
        Path band = inset(box, radii, in + 1.6f * px);
        slope.setShader(new RadialGradient(box.left, box.top, near * 0.8f, new int[]{Scene.alpha(ICE, 0.13f),
                Scene.alpha(ICE, 0.04f), ICE & 0x00FFFFFF}, new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP));
        c.drawPath(band, slope);
        // the bevel: the glass's other face, a fainter line inside near each lit corner
        Path bevel = inset(box, radii, in + 2.5f * px);
        p.setStrokeWidth(Math.max(1f, 0.7f * px));
        p.setShader(new RadialGradient(box.left, box.top, near * 0.6f, new int[]{Scene.alpha(ICE, 0.16f),
                Scene.alpha(ICE, 0.05f), ICE & 0x00FFFFFF}, new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
        c.drawPath(bevel, p);
        // the glint at the top left; since 2.15 the bottom right has no glow at all, just the faint edge all round
        Optics.catchlight(c, box, path, Math.max(0, radii[0] - in), in, px, ICE, 0xFF8CC4FF, CATCH, false);
    }

    private static Bitmap grainTile;

    /**
     * The frost in the glass, as in the crystal logo (2.05): a fine, faint grain of light and dark specks (about two
     * levels either way on the black) with a few brighter specks of dust, the same tile repeated over {@code box}.
     */
    static void grain(Canvas c, RectF box) {
        if (grainTile == null) {
            int n = 128;
            int[] px = new int[n * n];
            java.util.Random r = new java.util.Random(0x6A1A55L);
            for (int i = 0; i < px.length; i++) {
                float g = (float) r.nextGaussian();
                int a = Math.min(255, Math.round(Math.abs(g) * 3.5f));
                px[i] = (a << 24) | (g > 0 ? 0xFFFFFF : 0x000000);
            }
            for (int k = 0; k < 24; k++) px[r.nextInt(px.length)] = ((18 + r.nextInt(22)) << 24) | 0xF4F8FF;
            grainTile = Bitmap.createBitmap(px, n, n, Bitmap.Config.ARGB_8888);
        }
        Paint p = new Paint();
        p.setShader(grainShader());
        c.drawRect(box, p);
    }

    /** The frost's grain as a repeating shader (for drawing it onto other things too). */
    static Shader grainShader() {
        if (grainTile == null) grain(new Canvas(Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)), new RectF(0, 0, 1, 1));
        return new BitmapShader(grainTile, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT);
    }

    /** {@code box}'s rounded shape, {@code in} pixels inside it. */
    private static Path inset(RectF box, float[] radii, float in) {
        float[] r = new float[8];
        for (int i = 0; i < 8; i++) r[i] = Math.max(0, radii[i] - in);
        Path path = new Path();
        path.addRoundRect(new RectF(box.left + in, box.top + in, box.right - in, box.bottom - in), r, Path.Direction.CW);
        return path;
    }

    /** Eight equal corner radii. */
    static float[] round(float r) {
        return new float[]{r, r, r, r, r, r, r, r};
    }

    /**
     * A widget card's picture, {@code wDp} × {@code hDp} at {@code px} pixels per dp: its fill ({@link #fill}), the light
     * of the bright things in front falling on it ({@code spillsDp}, in dp), and its edge (when {@code edged}). Drawn
     * once per light and size.
     */
    static Bitmap panel(Scene scene, int wDp, int hDp, float px, float[] radiiDp, Spill[] spillsDp, boolean edged) {
        StringBuilder k = new StringBuilder("p|").append(scene.tag()).append('|').append(wDp).append('x').append(hDp)
                .append('|').append(px).append('|').append(edged);
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
        fill(c, box, radii, scene, px, 0f, null);
        spill(c, 0, 0, spillsDp, px);
        c.restore();
        if (edged) rim(c, box, radii, px);
        CACHE.put(key, b);
        return b;
    }

    /**
     * Just the edge, on a clear picture {@code w} × {@code h} pixels: for laying over a card drawn by views (the
     * Currency widget's clocks) or over a picture redrawn every frame (the Sound widget). Drawn once per light and size.
     */
    static Bitmap edge(int w, int h, float[] radii, float px) {
        StringBuilder k = new StringBuilder("e|").append(Look.get().tag()).append('|').append(w).append('x').append(h)
                .append('|').append(px);
        for (float r : radii) k.append(',').append(r);
        String key = k.toString();
        Bitmap hit = CACHE.get(key);
        if (hit != null) return hit;
        Bitmap b = Bitmap.createBitmap(Math.max(2, w), Math.max(2, h), Bitmap.Config.ARGB_8888);
        rim(new Canvas(b), new RectF(0, 0, b.getWidth(), b.getHeight()), radii, px);
        CACHE.put(key, b);
        return b;
    }
}
