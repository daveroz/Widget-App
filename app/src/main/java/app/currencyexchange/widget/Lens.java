package app.currencyexchange.widget;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.PorterDuffXfermode;

/**
 * The faint colour fringe a real lens gives a bright light (chromatic aberration), as in the glass references: kept
 * for the brightest things only (what's lit, a glowing line), never for text, and subtle, a red edge on one side and
 * a blue one on the other, showing only where the light spills past itself.
 */
final class Lens {
    private Lens() {}

    private static final int RED = 0xFFFF4A3A, BLUE = 0xFF3AA8FF;

    /** A copy of {@code src} with its fringe: a red copy shifted {@code shift} px left and a blue one right, behind it. */
    static Bitmap fringe(Bitmap src, float shift, float strength) {
        Bitmap out = Bitmap.createBitmap(src.getWidth(), src.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        Paint p = new Paint(Paint.FILTER_BITMAP_FLAG);
        p.setColorFilter(new PorterDuffColorFilter(Scene.alpha(RED, strength), PorterDuff.Mode.SRC_IN));
        c.drawBitmap(src, -shift, 0, p);
        p.setColorFilter(new PorterDuffColorFilter(Scene.alpha(BLUE, strength), PorterDuff.Mode.SRC_IN));
        c.drawBitmap(src, shift, 0, p);
        p.setColorFilter(null);
        p.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_OVER));
        c.drawBitmap(src, 0, 0, p);
        return out;
    }
}
