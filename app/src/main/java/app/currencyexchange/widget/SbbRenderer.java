package app.currencyexchange.widget;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * Draws the Swiss railway clock with the exact geometry of SBB's own digital clock (sbbUhr 1.3, the
 * "dark background" face without the grey frame): everything lives in a 112-unit square, centre (56,56).
 * Face: white disc r 52.5. 12 hour bars 3.5 x 12 and 48 minute ticks 1.4 x 3.5, all ending at r 48.5,
 * colour rgb(30,30,30). Hour hand 6.4 -> 5.2 wide, 32 long + 12 tail. Minute hand 5.2 -> 3.6 wide, 46 long
 * + 12 tail. Second hand red rgb(235,0,0): 1.4 stick from 16.5 behind the centre to 26 ahead, disc of
 * 10.5 diameter centred 31.2 from the centre. Motion comes from {@link SbbClock}.
 */
final class SbbRenderer {
    private SbbRenderer() {}

    static final int SIZE = 440;

    private static Bitmap face;
    private static int faceSize;

    private static synchronized Bitmap face(int n) {
        if (face != null && faceSize == n) return face;
        Bitmap b = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        float s = n / 112f;
        c.scale(s, s);
        c.translate(56, 56);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(0xFFFFFFFF);
        c.drawCircle(0, 0, 52.5f, p);
        p.setColor(0xFF1E1E1E);
        for (int i = 0; i < 60; i++) {
            c.save();
            c.rotate(i * 6f);
            if (i % 5 == 0) c.drawRect(-1.75f, -48.5f, 1.75f, -36.5f, p);
            else c.drawRect(-0.7f, -48.3f, 0.7f, -44.8f, p);
            c.restore();
        }
        face = b;
        faceSize = n;
        return b;
    }

    /**
     * @param elastic true = animate the minute/hour hands' swing right after the minute impulse (live frames);
     *                false = hands exactly on their marks (still pictures)
     * @param reuse   optional mutable bitmap of the right size (avoids allocating at ~10 frames a second)
     */
    static Bitmap render(long nowMs, ZoneId zone, int n, Bitmap reuse, boolean elastic) {
        Bitmap out = (reuse != null && reuse.getWidth() == n && reuse.getHeight() == n && reuse.isMutable())
                ? reuse : Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888);
        ZonedDateTime t = Instant.ofEpochMilli(nowMs).atZone(zone);
        double msIntoMinute = t.getSecond() * 1000.0 + Math.floorMod(nowMs, 1000L);
        float secDeg = (float) SbbClock.secondAngle(msIntoMinute / 1000.0);
        float minDeg = (float) SbbClock.minuteAngle(t.getMinute(), msIntoMinute, elastic);
        float hourDeg = (float) SbbClock.hourAngle(t.getHour(), t.getMinute(), msIntoMinute, elastic);

        Canvas c = new Canvas(out);
        c.drawColor(0, PorterDuff.Mode.CLEAR);
        c.drawBitmap(face(n), 0, 0, null);
        float s = n / 112f;
        c.scale(s, s);
        c.translate(56, 56);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(0xFF000000);

        hand(c, p, hourDeg, 3.2f, 2.6f, 12f, 32f);
        hand(c, p, minDeg, 2.6f, 1.8f, 12f, 46f);

        p.setColor(0xFFEB0000);
        c.save();
        c.rotate(secDeg);
        c.drawRect(-0.7f, -26.0f, 0.7f, 16.5f, p);
        c.drawCircle(0, -31.2f, 5.25f, p);
        c.restore();
        return out;
    }

    private static void hand(Canvas c, Paint p, float deg, float baseHalf, float tipHalf, float tail, float fwd) {
        Path path = new Path();
        path.moveTo(-baseHalf, tail);
        path.lineTo(baseHalf, tail);
        path.lineTo(tipHalf, -fwd);
        path.lineTo(-tipHalf, -fwd);
        path.close();
        c.save();
        c.rotate(deg);
        c.drawPath(path, p);
        c.restore();
    }
}
