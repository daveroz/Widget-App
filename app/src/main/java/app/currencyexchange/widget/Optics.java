package app.currencyexchange.widget;

import android.graphics.Canvas;
import android.graphics.ComposeShader;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;

/**
 * What a camera does to light, after the glass-on-black references, kept subtle. Each is drawn as the real optics
 * make it:
 * <ul>
 * <li><b>Light strikes</b> ({@link #star}): a cross-screen ("star") filter, a fine grid on the glass, spreads a point
 *     of light into thin spikes square to its lines; four here, the horizontal pair a little longer and bluer, as a
 *     2-point star or an anamorphic lens streaks it. Only where a card's edge catches the light, at its top left
 *     corner ({@link #catchlight}), in the edge's fixed colour; nothing inside the cards has one.</li>
 * <li><b>Glass reflections</b> ({@link #reflection}, {@link #catchlight}): glass on black reads by the light it
 *     reflects: a soft band of the room's light slanting across it, and the edge catching the light brightest where
 *     it turns (white-line lighting; glass reflects most at grazing angles).</li>
 * <li><b>Haze and bloom</b>: a Black Pro-Mist filter scatters a little of a highlight's light round it: the soft,
 *     pearly core round each strike.</li>
 * <li><b>Halation</b> ({@link #HALATION}): film without its anti-halation backing glows red-orange round bright
 *     points, light bouncing back into the red layer: a faint warm ring round the brightest things.</li>
 * <li><b>Fringe</b>: chromatic aberration, in {@link Lens}.</li>
 * </ul>
 */
final class Optics {
    private Optics() {}

    /** Halation's colour: the red-orange glow film gives round bright points. */
    static final int HALATION = 0xFFFF5A2E;
    /** The blue an anamorphic streak takes on. */
    private static final int STREAK = 0xFF8CC4FF;

    /**
     * A light strike on a point of light at (x, y), as a cross-screen filter makes it: a soft, pearly core (the mist
     * round a highlight), a faint red-orange ring of halation just outside it, and four thin spikes tapering to nothing,
     * the vertical pair {@code reach} long, the horizontal pair half as long again and fading to blue; in a pale shade
     * of {@code colour}, {@code strength} strong at the middle (times the look's {@link Look#flares}). {@code colour} is
     * the light's own: the song's for what's lit in the song's colour, fixed for a light of a fixed colour. {@code px}
     * is pixels per dp.
     */
    static void star(Canvas c, float x, float y, float reach, float px, int colour, float strength) {
        star(c, x, y, reach, px, colour, STREAK, strength);
    }

    /** As {@link #star}, its horizontal (anamorphic) streak fading to {@code streak} instead of the usual blue. */
    static void star(Canvas c, float x, float y, float reach, float px, int colour, int streak, float strength) {
        star(c, x, y, reach, px, colour, streak, strength, true);
    }

    /** As {@link #star}; without its four spikes (just its core, mist and halation) unless {@code spikes}. */
    static void star(Canvas c, float x, float y, float reach, float px, int colour, int streak, float strength,
                     boolean spikes) {
        Look look = Look.get();
        if (strength <= 0f || reach <= 0f || look.flares <= 0f) return;
        strength = Math.min(1.2f, strength * look.flares); // the look's flares: brighter, and reaching a little further
        reach *= 0.55f + 0.45f * look.flares;
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        int pale = Scene.mix(colour, 0xFFFFFFFF, 0.6f);
        float core = reach * 0.32f * (float) Math.sqrt(look.haze); // the mist round it spreads with the haze
        p.setShader(new RadialGradient(x, y, core, new int[]{Scene.alpha(pale, Math.min(1f, 0.9f * strength * look.haze)),
                Scene.alpha(colour, Math.min(1f, 0.28f * strength * look.haze)), colour & 0x00FFFFFF},
                new float[]{0f, 0.4f, 1f}, Shader.TileMode.CLAMP));
        c.drawCircle(x, y, core, p);
        // halation: a faint red-orange ring just outside the core, light bounced back through the film
        float ring = reach * 0.62f * (float) Math.sqrt(look.halation);
        p.setShader(new RadialGradient(x, y, ring, new int[]{HALATION & 0x00FFFFFF,
                Scene.alpha(HALATION, Math.min(1f, 0.16f * strength * look.halation)),
                HALATION & 0x00FFFFFF}, new float[]{0.3f, 0.55f, 1f}, Shader.TileMode.CLAMP));
        c.drawCircle(x, y, ring, p);
        if (spikes) {
            float thick = Math.max(0.8f, 0.9f * px);
            spike(c, p, x, y, reach * 1.5f, 0, thick, pale, streak, strength);
            spike(c, p, x, y, -reach * 1.5f, 0, thick, pale, streak, strength);
            spike(c, p, x, y, 0, reach, thick, pale, colour, strength * 0.8f);
            spike(c, p, x, y, 0, -reach, thick, pale, colour, strength * 0.8f);
        }
        p.setShader(null);
    }

    /** One spike: a thin sliver from (x, y), {@code width} wide there, out to a point at (x + dx, y + dy). */
    private static void spike(Canvas c, Paint p, float x, float y, float dx, float dy, float width, int pale, int tip,
                              float strength) {
        float len = (float) Math.hypot(dx, dy);
        float nx = -dy / len * width / 2, ny = dx / len * width / 2;
        Path path = new Path();
        path.moveTo(x + nx, y + ny);
        path.lineTo(x + dx, y + dy);
        path.lineTo(x - nx, y - ny);
        path.close();
        p.setShader(new LinearGradient(x, y, x + dx, y + dy, new int[]{Scene.alpha(pale, strength),
                Scene.alpha(Scene.mix(pale, tip, 0.6f), 0.35f * strength), tip & 0x00FFFFFF}, new float[]{0f, 0.45f, 1f},
                Shader.TileMode.CLAMP));
        c.drawPath(path, p);
    }

    /**
     * The room's light reflected in a pane of glass ({@code box}): a soft band slanting down across its top right,
     * fading out towards the middle, {@code strength} strong at its brightest. Draw it inside the pane's clip.
     */
    static void reflection(Canvas c, RectF box, float strength) {
        if (strength <= 0f) return;
        float w = box.width(), h = box.height();
        float drop = h * 0.62f, slant = drop * 0.55f, band = Math.min(w * 0.18f, Math.max(h * 0.5f, w * 0.08f));
        float x0 = box.left + w * 0.6f, top = box.top;
        Path path = new Path();
        path.moveTo(x0, top);
        path.lineTo(x0 + band, top);
        path.lineTo(x0 + band - slant, top + drop);
        path.lineTo(x0 - slant, top + drop);
        path.close();
        Shader across = new LinearGradient(x0, top, x0 + band, top, new int[]{0x00FFFFFF, Scene.alpha(0xFFFFFFFF, strength),
                Scene.alpha(0xFFFFFFFF, strength * 0.55f), 0x00FFFFFF}, new float[]{0f, 0.3f, 0.7f, 1f}, Shader.TileMode.CLAMP);
        Shader down = new LinearGradient(0, top, 0, top + drop, 0xFFFFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        p.setShader(new ComposeShader(across, down, PorterDuff.Mode.DST_IN));
        c.drawPath(path, p);
    }

    /**
     * Where a rounded pane's edge catches the light, at its top left corner (radius {@code r}, the edge {@code in}
     * pixels inside {@code box}): the edge brighter round the bend, and a small strike where it's brightest, in a pale
     * shade of {@code colour}. {@code edge} is the edge's path, {@code px} pixels per dp.
     */
    static void catchlight(Canvas c, RectF box, Path edge, float r, float in, float px, int colour, float strength) {
        catchlight(c, box, edge, r, in, px, colour, STREAK, strength, false);
    }

    /**
     * The crystal video's glint (2.18), on the rim of the top left bend ({@code r} its radius, the rim {@code in} pixels
     * inside {@code box}), a little nearer the top than its middle, as in the video: a small, intense core drawn out
     * along the rim, a soft icy halo round it, a short streak along the rim (the diagonal, still the longest) and faint
     * short rays square to the card; and the rim splitting like a prism round it, cyan on its outer side and amber on
     * its inner, fading along the edges. Kept small and quiet.
     */
    static void glint(Canvas c, RectF box, float r, float in, float px, float strength) {
        Look look = Look.get();
        if (strength <= 0f || look.flares <= 0f) return;
        float s = Math.min(1.2f, strength * (0.55f + 0.45f * look.flares) * 0.85f);
        r = Math.max(r, 4 * px);
        double th = Math.toRadians(54); // from the left, round towards the top
        float cx = box.left + in + r, cy = box.top + in + r;
        float gx = cx - r * (float) Math.cos(th), gy = cy - r * (float) Math.sin(th);
        float tx = (float) Math.sin(th), ty = -(float) Math.cos(th); // the rim's direction there (up and to the right)
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        // the prism round it: cyan just outside the rim's line, amber just inside, fading over two radii
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Math.max(0.8f, 0.7f * px));
        float reach = r * 2.2f;
        android.graphics.Path outer = new android.graphics.Path(), inner = new android.graphics.Path();
        float o = Math.max(0.35f, in - 0.6f * px), i2 = in + 1.15f * px;
        outer.addRoundRect(new RectF(box.left + o, box.top + o, box.right - o, box.bottom - o), r + in - o, r + in - o,
                android.graphics.Path.Direction.CW);
        inner.addRoundRect(new RectF(box.left + i2, box.top + i2, box.right - i2, box.bottom - i2), Math.max(0, r + in - i2),
                Math.max(0, r + in - i2), android.graphics.Path.Direction.CW);
        p.setShader(new RadialGradient(gx, gy, reach, new int[]{Scene.alpha(0xFF7CD6FF, 0.8f * s), Scene.alpha(0xFF7CD6FF, 0.24f * s),
                0x007CD6FF}, new float[]{0f, 0.4f, 1f}, Shader.TileMode.CLAMP));
        c.drawPath(outer, p);
        p.setShader(new RadialGradient(gx, gy, reach, new int[]{Scene.alpha(0xFFFFA860, 0.55f * s), Scene.alpha(0xFFFFA860, 0.15f * s),
                0x00FFA860}, new float[]{0f, 0.4f, 1f}, Shader.TileMode.CLAMP));
        c.drawPath(inner, p);
        // the rim glowing white round it, softly
        android.graphics.Path rim = new android.graphics.Path();
        rim.addRoundRect(new RectF(box.left + in, box.top + in, box.right - in, box.bottom - in), r, r,
                android.graphics.Path.Direction.CW);
        p.setStrokeWidth(Math.max(1f, 0.9f * px));
        p.setShader(new RadialGradient(gx, gy, r * 1.4f, new int[]{Scene.alpha(0xFFF6FAFF, 0.85f * s), Scene.alpha(0xFFDCEBFF, 0.3f * s),
                0x00DCEBFF}, new float[]{0f, 0.4f, 1f}, Shader.TileMode.CLAMP));
        c.drawPath(rim, p); // the rim itself bright and sharp near the glint, as in the video
        p.setStyle(Paint.Style.FILL);
        // its soft icy halo
        float halo = r * 0.4f; // small: in the video it spreads outside the glass, which a card's picture can't
        p.setShader(new RadialGradient(gx, gy, halo, new int[]{Scene.alpha(0xFFDCEBFF, 0.45f * s), Scene.alpha(0xFFDCEBFF, 0.12f * s),
                0x00DCEBFF}, new float[]{0f, 0.3f, 1f}, Shader.TileMode.CLAMP));
        c.drawCircle(gx, gy, halo, p);
        // a short streak along the rim and faint short rays square to the card
        float thick = Math.max(0.8f, 0.8f * px);
        int pale = 0xFFF4F8FF;
        spike(c, p, gx, gy, tx * r * 0.62f, ty * r * 0.62f, thick * 1.2f, pale, 0xFF9CCBFF, 0.5f * s);
        spike(c, p, gx, gy, -tx * r * 0.62f, -ty * r * 0.62f, thick * 1.2f, pale, 0xFF9CCBFF, 0.5f * s);
        float ray = r * 0.34f;
        spike(c, p, gx, gy, ray, 0, thick * 0.8f, pale, 0xFF9CCBFF, 0.22f * s);
        spike(c, p, gx, gy, -ray * 0.8f, 0, thick * 0.8f, pale, 0xFF9CCBFF, 0.22f * s);
        spike(c, p, gx, gy, 0, ray, thick * 0.8f, pale, 0xFF9CCBFF, 0.2f * s);
        spike(c, p, gx, gy, 0, -ray * 0.8f, thick * 0.8f, pale, 0xFF9CCBFF, 0.2f * s);
        // the core: small and intense, drawn out along the rim
        float core = r * 0.09f;
        c.save();
        c.translate(gx, gy);
        c.rotate((float) Math.toDegrees(Math.atan2(ty, tx)));
        c.scale(3.2f, 1f);
        p.setShader(new RadialGradient(0, 0, core, new int[]{0xFFFFFFFF, Scene.alpha(0xFFF4F8FF, Math.min(1f, 0.85f * s + 0.15f)),
                0x00F0F6FF}, new float[]{0f, 0.38f, 1f}, Shader.TileMode.CLAMP));
        c.drawCircle(0, 0, core, p);
        c.restore();
        p.setShader(null);
    }

    /**
     * Just the edge catching the light round a corner's bend, brighter where it faces the light, with no glint (2.10:
     * what the bottom right corner keeps): the top left corner, or ({@code far}) the bottom right one.
     */
    static void bendLight(Canvas c, RectF box, Path edge, float r, float in, float px, int colour, float strength,
                          boolean far) {
        if (strength <= 0f) return;
        r = Math.max(r, 4 * px);
        float cx = far ? box.right - in - r : box.left + in + r, cy = far ? box.bottom - in - r : box.top + in + r;
        float k = far ? 0.7071f : -0.7071f;
        float gx = cx + r * k, gy = cy + r * k;
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Math.max(1f, px));
        int pale = Scene.mix(colour, 0xFFFFFFFF, 0.7f);
        p.setShader(new RadialGradient(gx, gy, r * 1.25f, new int[]{Scene.alpha(pale, 0.7f * strength),
                Scene.alpha(pale, 0.22f * strength), pale & 0x00FFFFFF}, new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP));
        c.drawPath(edge, p);
    }

    /**
     * As above, at the top left corner or ({@code far}) the bottom right one, as a crystal pane lit from two sides
     * shows it (2.05): the strike's horizontal streak fading to {@code streak}.
     */
    static void catchlight(Canvas c, RectF box, Path edge, float r, float in, float px, int colour, int streak,
                           float strength, boolean far) {
        if (strength <= 0f) return;
        r = Math.max(r, 4 * px);
        float cx = far ? box.right - in - r : box.left + in + r, cy = far ? box.bottom - in - r : box.top + in + r;
        float k = far ? 0.7071f : -0.7071f;
        float gx = cx + r * k, gy = cy + r * k; // the bend at 45°, where it faces the light
        bendLight(c, box, edge, r, in, px, colour, strength, far);
        if (!far) glint(c, box, r, in, px, strength);
    }
}
