package app.currencyexchange.widget;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;

/**
 * The colour fringe a real lens gives a bright light (chromatic aberration), as in the chromatic references: on the
 * brightest things (what's lit, a glowing line, the lamps) and on text, in place of any glow: a red edge on one side
 * and a blue one on the other, showing only where it spills past the letters or the light. How far apart and how
 * strong is set in {@link Look}.
 */
final class Lens {
    private Lens() {}

    static final int RED = 0xFFFF4A3A, BLUE = 0xFF3AA8FF;

    /**
     * A copy of {@code src} with the look's fringe behind it: a red copy pulled left and a blue one right, by the
     * look's distance at {@code pxPerDp} pixels per dp, {@code weight} times the look's strength.
     */
    static Bitmap fringe(Bitmap src, float pxPerDp, float weight) {
        Look look = Look.get();
        float strength = Math.min(1f, look.fringe * weight);
        if (strength <= 0f) return src;
        float shift = look.fringeDp * pxPerDp;
        Bitmap out = Bitmap.createBitmap(src.getWidth(), src.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        Paint p = new Paint(Paint.FILTER_BITMAP_FLAG);
        p.setColorFilter(new PorterDuffColorFilter(Scene.alpha(RED, strength), PorterDuff.Mode.SRC_IN));
        c.drawBitmap(src, -shift, 0, p);
        p.setColorFilter(new PorterDuffColorFilter(Scene.alpha(BLUE, strength), PorterDuff.Mode.SRC_IN));
        c.drawBitmap(src, shift, 0, p);
        p.setColorFilter(null);
        c.drawBitmap(src, 0, 0, p);
        return out;
    }

    /**
     * The fringe of text drawn straight onto a canvas (not the letters themselves; draw them after, over it): a red
     * copy of {@code s} pulled left and a blue one right, at {@code pxPerDp} pixels per dp, {@code weight} times the
     * look's strength, with {@code paint}'s typeface, size and alignment.
     */
    static void text(Canvas c, Paint paint, String s, float x, float y, float pxPerDp, float weight) {
        Look look = Look.get();
        float strength = Math.min(1f, look.fringe * weight);
        if (strength <= 0f || s == null || s.isEmpty()) return;
        float shift = look.fringeDp * pxPerDp;
        int was = paint.getColor();
        paint.setColor(Scene.alpha(RED, strength));
        c.drawText(s, x - shift, y, paint);
        paint.setColor(Scene.alpha(BLUE, strength));
        c.drawText(s, x + shift, y, paint);
        paint.setColor(was);
    }
}
