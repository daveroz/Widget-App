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
     * How much of the fringe a straight line gets (a progress line and its ticks, a trend line, the line of light at the
     * sleeve's mouth): pulled sideways, a line gets a whole red and blue copy beside it, which looks odd, so lines get
     * only a trace of it. Points of light (the progress point, the lamps, the record's rim) keep it all.
     */
    static final float LINES = 0.3f;

    /**
     * A copy of {@code src} with the look's fringe behind it: a red copy pulled left and a blue one right, by the
     * look's distance at {@code pxPerDp} pixels per dp, {@code weight} times the look's strength. For bright things.
     */
    static Bitmap fringe(Bitmap src, float pxPerDp, float weight) {
        Look look = Look.get();
        return fringeBy(src, look.fringeDp * pxPerDp, Math.min(1f, look.fringe * weight));
    }

    /** A copy of a picture of text ({@code pxPerDp} pixels per dp) with the subtler fringe text has. */
    static Bitmap textFringe(Bitmap src, float pxPerDp) {
        Look look = Look.get();
        return fringeBy(src, look.textFringeDp * pxPerDp, look.textFringe);
    }

    /** {@code src} with red and blue copies {@code shift} pixels left and right behind it, {@code strength} strong. */
    private static Bitmap fringeBy(Bitmap src, float shift, float strength) {
        if (strength <= 0f) return src;
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
     * copy of {@code s} pulled left and a blue one right, at {@code pxPerDp} pixels per dp, with {@code paint}'s
     * typeface, size and alignment, as subtle as text's fringe is.
     */
    static void text(Canvas c, Paint paint, String s, float x, float y, float pxPerDp) {
        Look look = Look.get();
        if (look.textFringe <= 0f || s == null || s.isEmpty()) return;
        float shift = look.textFringeDp * pxPerDp;
        int was = paint.getColor();
        paint.setColor(Scene.alpha(RED, look.textFringe));
        c.drawText(s, x - shift, y, paint);
        paint.setColor(Scene.alpha(BLUE, look.textFringe));
        c.drawText(s, x + shift, y, paint);
        paint.setColor(was);
    }

    /**
     * The fringe under a TextView's (or an EditText's) letters, in its own onDraw before the letters: its layout drawn
     * twice, in red pulled left and in blue pulled right, where the view puts its text.
     */
    static void text(android.widget.TextView v, Canvas canvas) {
        android.text.Layout layout = v.getLayout();
        Look look = Look.get();
        if (layout == null || look.textFringe <= 0f) return;
        float shift = look.textFringeDp * v.getResources().getDisplayMetrics().density;
        // where TextView puts its text: inside its padding, moved down for a centred or bottom gravity
        int top = v.getExtendedPaddingTop(), bottom = v.getExtendedPaddingBottom();
        int box = v.getHeight() - top - bottom, gap = box - layout.getHeight();
        int vertical = v.getGravity() & android.view.Gravity.VERTICAL_GRAVITY_MASK;
        float down = gap <= 0 ? 0 : vertical == android.view.Gravity.BOTTOM ? gap
                : vertical == android.view.Gravity.CENTER_VERTICAL ? gap / 2f : 0;
        android.text.TextPaint p = v.getPaint();
        int was = p.getColor();
        canvas.save();
        int left = v.getCompoundPaddingLeft(), right = v.getCompoundPaddingRight();
        canvas.clipRect(left + v.getScrollX(), top + v.getScrollY(), v.getWidth() - right + v.getScrollX(),
                v.getHeight() - bottom + v.getScrollY());
        canvas.translate(left - shift, top + down);
        p.setColor(Scene.alpha(RED, look.textFringe));
        layout.draw(canvas);
        canvas.translate(2 * shift, 0);
        p.setColor(Scene.alpha(BLUE, look.textFringe));
        layout.draw(canvas);
        canvas.restore();
        p.setColor(was);
    }
}
