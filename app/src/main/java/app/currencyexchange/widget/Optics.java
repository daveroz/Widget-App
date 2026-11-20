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
 *     2-point star or an anamorphic lens streaks it. Only on true points of light: a glint, a lamp, the progress point.</li>
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
     * of {@code colour}, {@code strength} strong at the middle. {@code px} is pixels per dp.
     */
    static void star(Canvas c, float x, float y, float reach, float px, int colour, float strength) {
        if (strength <= 0f || reach <= 0f) return;
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        int pale = Scene.mix(colour, 0xFFFFFFFF, 0.6f);
        Look look = Look.get();
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
        float thick = Math.max(0.8f, 0.9f * px);
        spike(c, p, x, y, reach * 1.5f, 0, thick, pale, STREAK, strength);
        spike(c, p, x, y, -reach * 1.5f, 0, thick, pale, STREAK, strength);
        spike(c, p, x, y, 0, reach, thick, pale, colour, strength * 0.8f);
        spike(c, p, x, y, 0, -reach, thick, pale, colour, strength * 0.8f);
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
        if (strength <= 0f) return;
        r = Math.max(r, 4 * px);
        float cx = box.left + in + r, cy = box.top + in + r; // the bend's middle
        float gx = cx - r * 0.7071f, gy = cy - r * 0.7071f; // the bend at 45°, where it faces the light
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Math.max(1f, px));
        int pale = Scene.mix(colour, 0xFFFFFFFF, 0.7f);
        p.setShader(new RadialGradient(gx, gy, r * 1.25f, new int[]{Scene.alpha(pale, 0.7f * strength),
                Scene.alpha(pale, 0.22f * strength), pale & 0x00FFFFFF}, new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP));
        c.drawPath(edge, p);
        // the strike stays inside the pane's picture: its arms no longer than the way to the edges
        float room = (gx - box.left) * 0.92f;
        star(c, gx, gy, Math.min(6 * px, room / 1.5f), px, colour, 0.55f * strength);
    }
}
