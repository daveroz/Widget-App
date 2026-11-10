package app.currencyexchange.widget;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.LruCache;

import org.json.JSONObject;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Locale;

/**
 * The trend behind each conversion row: the last month of market rates as a soft area chart, a curve through
 * every day's rate with a fill that fades out downwards. Only the shape: no axis, no numbers, no markers. It
 * uses the one amber accent at low opacity so the row's flags and numbers stay on top.
 */
final class Trend {
    private Trend() {}

    /** Days of history behind each row (plus today's live rate as the last point). */
    static final int DAYS = 30;

    // The curve keeps to the middle band of the row; the fill fades to nothing at the bottom edge.
    private static final float BAND_TOP = 0.24f, BAND_BOTTOM = 0.78f;
    private static final int LINE_ALPHA = 0x80, FILL_ALPHA = 0x3A; // 50 % line, 23 % fill at its top
    private static final float LINE_DP = 1.5f;
    /** Moves smaller than this share of the rate are drawn flatter, so a pegged currency doesn't look volatile. */
    private static final double MIN_SPAN = 0.004;
    /** Radius just inside a row's 1 dp border (res/values/dimens.xml widget_radius_inner). */
    private static final float INNER_RADIUS_DP = 27f;
    private static final float PXDP = 2f;

    private static final LruCache<String, Bitmap> CACHE = new LruCache<String, Bitmap>(4 * 1024 * 1024) {
        @Override
        protected int sizeOf(String key, Bitmap b) { return b.getByteCount(); }
    };

    /**
     * {daysAgo (0 = today), rate} for from -> to, oldest first, ending with the live rate.
     * hist is {@link Store#hist}. Null when there are fewer than 3 points to draw.
     */
    static double[][] series(JSONObject hist, String from, double live) {
        String f = from.trim().toLowerCase(Locale.ROOT);
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        ArrayList<double[]> pts = new ArrayList<>();
        for (int back = DAYS; back >= 1; back--) {
            JSONObject day = hist.optJSONObject(today.minusDays(back).toString());
            double v = day == null ? 0 : day.optDouble(f, 0);
            if (v > 0) pts.add(new double[]{-back, v});
        }
        if (live > 0) pts.add(new double[]{0, live});
        return pts.size() < 3 ? null : pts.toArray(new double[0][]);
    }

    /** The chart for an area of wDp x hDp (a row inside its 1 dp border), with the row's round ends cut out. */
    static Bitmap bitmap(double[][] pts, int wDp, int hDp) {
        StringBuilder k = new StringBuilder().append(wDp).append('x').append(hDp);
        for (double[] p : pts) k.append('|').append(p[0]).append(':').append(p[1]);
        String key = k.toString();
        Bitmap hit = CACHE.get(key);
        if (hit != null) return hit;
        Bitmap made = draw(pts, wDp, hDp);
        CACHE.put(key, made);
        return made;
    }

    private static Bitmap draw(double[][] pts, int wDp, int hDp) {
        int w = Math.round(wDp * PXDP), h = Math.round(hDp * PXDP), n = pts.length;
        double lo = Double.MAX_VALUE, hi = -Double.MAX_VALUE;
        for (double[] p : pts) {
            lo = Math.min(lo, p[1]);
            hi = Math.max(hi, p[1]);
        }
        double mid = (lo + hi) / 2, span = Math.max(hi - lo, mid * MIN_SPAN), base = mid - span / 2;
        float top = h * BAND_TOP, bottom = h * BAND_BOTTOM;
        double x0 = pts[0][0], dx = pts[n - 1][0] - x0;
        float[] xs = new float[n], ys = new float[n];
        for (int i = 0; i < n; i++) {
            xs[i] = (float) ((pts[i][0] - x0) / dx * w);
            ys[i] = (float) (bottom - (pts[i][1] - base) / span * (bottom - top));
        }
        Path curve = curve(xs, ys);

        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);

        Path area = new Path(curve);
        area.lineTo(xs[n - 1], h);
        area.lineTo(xs[0], h);
        area.close();
        Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        fill.setShader(new LinearGradient(0, top, 0, h,
                alpha(Theme.AMBER, FILL_ALPHA), alpha(Theme.AMBER, 0), Shader.TileMode.CLAMP));
        c.drawPath(area, fill);

        Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeWidth(LINE_DP * PXDP);
        line.setStrokeCap(Paint.Cap.ROUND);
        line.setStrokeJoin(Paint.Join.ROUND);
        line.setColor(alpha(Theme.AMBER_LIGHT, LINE_ALPHA));
        c.drawPath(curve, line);

        // Cut away what falls outside the row's rounded ends (clipToOutline does this too, but only on Android 12+).
        float r = Math.min(INNER_RADIUS_DP * PXDP, h / 2f);
        Path outside = new Path();
        outside.addRoundRect(new RectF(0, 0, w, h), r, r, Path.Direction.CW);
        outside.setFillType(Path.FillType.INVERSE_WINDING);
        Paint clear = new Paint(Paint.ANTI_ALIAS_FLAG);
        clear.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
        c.drawPath(outside, clear);
        return out;
    }

    /** Smooth curve through the points that never overshoots them (Steffen's monotone cubic, as Bezier segments). */
    private static Path curve(float[] x, float[] y) {
        int n = x.length;
        float[] m = new float[n];
        for (int i = 0; i < n; i++) {
            float dl = i > 0 ? (y[i] - y[i - 1]) / (x[i] - x[i - 1]) : 0;
            float dr = i < n - 1 ? (y[i + 1] - y[i]) / (x[i + 1] - x[i]) : 0;
            if (i == 0) m[i] = dr;
            else if (i == n - 1) m[i] = dl;
            else if (dl * dr <= 0) m[i] = 0; // a peak or a valley: flat tangent
            else {
                float hl = x[i] - x[i - 1], hr = x[i + 1] - x[i];
                float p = (dl * hr + dr * hl) / (hl + hr);
                m[i] = Math.copySign(Math.min(Math.min(2 * Math.abs(dl), 2 * Math.abs(dr)), Math.abs(p)), dl);
            }
        }
        Path path = new Path();
        path.moveTo(x[0], y[0]);
        for (int i = 0; i < n - 1; i++) {
            float t = (x[i + 1] - x[i]) / 3f;
            path.cubicTo(x[i] + t, y[i] + m[i] * t, x[i + 1] - t, y[i + 1] - m[i + 1] * t, x[i + 1], y[i + 1]);
        }
        return path;
    }

    private static int alpha(int color, int a) { return (color & 0x00FFFFFF) | (a << 24); }
}
