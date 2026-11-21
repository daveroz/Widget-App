package app.currencyexchange.widget;

import android.graphics.Bitmap;
import android.graphics.BlurMaskFilter;
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
 * uses the shared light's colour (the playing song's, or amber), and dims towards the row's end, so the numbers
 * there stay clear on top of it.
 */
final class Trend {
    private Trend() {}

    /** Days of history behind each row (plus today's live rate as the last point). */
    static final int DAYS = 30;
    /** One sample every STEP days (1 = daily). For a longer DAYS, a bigger step keeps the line and the requests calm. */
    static final int STEP = 1;

    /** The fixed days the history is sampled on, so samples already fetched stay valid as time moves on. */
    static boolean sampleDay(LocalDate d) { return Math.floorMod(d.toEpochDay(), STEP) == 0; }

    // The curve keeps to the middle band of the row; the fill fades to nothing at the bottom edge.
    private static final float BAND_TOP = 0.24f, BAND_BOTTOM = 0.78f;
    private static final int LINE_ALPHA = 0x80, FILL_ALPHA = 0x3A; // 50 % line, 23 % fill at its top
    private static final float LINE_DP = 1.5f;
    /**
     * The chart dims towards the row's end, where the numbers are: full strength up to FADE_FROM of the width, down to
     * FADE_END strength by FADE_TO, and that dim for the rest, so the line never competes with the numbers over it.
     */
    private static final float FADE_FROM = 0.46f, FADE_TO = 0.66f, FADE_END = 0.15f;
    /** Moves smaller than this share of the rate are drawn flatter, so a pegged currency doesn't look volatile. */
    private static final double MIN_SPAN = 0.004;
    /** A row's corner radius (res/values/dimens.xml widget_radius). */
    private static final float RADIUS_DP = 28f;
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
            LocalDate d = today.minusDays(back);
            if (!sampleDay(d)) continue;
            JSONObject day = hist.optJSONObject(d.toString());
            double v = day == null ? 0 : day.optDouble(f, 0);
            if (v > 0) pts.add(new double[]{-back, v});
        }
        if (live > 0) pts.add(new double[]{0, live});
        return pts.size() < 3 ? null : pts.toArray(new double[0][]);
    }

    /**
     * The row's picture, {@code wDp} × {@code hDp} (the whole row): its card, as every widget's, with the chart over it
     * in the light all the widgets share ({@code pts} null: no chart yet) and the card's edge.
     */
    static Bitmap bitmap(double[][] pts, int wDp, int hDp, Scene scene) {
        return bitmap(pts, wDp, hDp, scene, false);
    }

    /** As {@link #bitmap}, or with {@code bare} just the chart on a clear picture (the rows share one card, 1.98). */
    static Bitmap bitmap(double[][] pts, int wDp, int hDp, Scene scene, boolean bare) {
        StringBuilder k = new StringBuilder().append(bare ? "b" : "").append(wDp).append('x').append(hDp).append('|').append(scene.tag());
        if (pts != null) for (double[] p : pts) k.append('|').append(p[0]).append(':').append(p[1]);
        String key = k.toString();
        Bitmap hit = CACHE.get(key);
        if (hit != null) return hit;
        Bitmap made = draw(pts, wDp, hDp, scene, bare);
        CACHE.put(key, made);
        return made;
    }

    private static Bitmap draw(double[][] pts, int wDp, int hDp, Scene scene, boolean bare) {
        int w = Math.round(wDp * PXDP), h = Math.round(hDp * PXDP);
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        // The row's panel, its round ends following the row's.
        float r = Math.min(RADIUS_DP * PXDP, h / 2f);
        RectF box = new RectF(0, 0, w, h);
        if (bare) c.save(); // just the chart: the card is the rows' shared one
        else Stage.fill(c, box, Stage.round(r), scene, PXDP, 0f, null);
        if (pts != null) {
            // The chart on a layer of its own, dimmed towards the end of the row where the numbers sit.
            c.saveLayer(box, null);
            chart(c, pts, w, h, scene);
            Paint fade = new Paint();
            fade.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
            int dim = Math.round(FADE_END * 255) << 24;
            fade.setShader(new LinearGradient(0, 0, w, 0, new int[]{0xFF000000, 0xFF000000, dim, dim},
                    new float[]{0f, FADE_FROM, FADE_TO, 1f}, Shader.TileMode.CLAMP));
            c.drawRect(box, fade);
            c.restore();
        }
        c.restore();
        if (!bare) Stage.rim(c, box, Stage.round(r), PXDP);
        return out;
    }

    /**
     * The chart: the curve as a thread of light in the shared light's colour (a warm core, a tight glow and a wide,
     * faint bloom in the haze), over a soft area of its light fading downward.
     */
    private static void chart(Canvas c, double[][] pts, int w, int h, Scene scene) {
        int n = pts.length;
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

        Path area = new Path(curve);
        area.lineTo(xs[n - 1], h);
        area.lineTo(xs[0], h);
        area.close();
        Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        fill.setShader(new LinearGradient(0, top, 0, h,
                alpha(scene.deep, FILL_ALPHA), alpha(scene.deep, 0), Shader.TileMode.CLAMP));
        c.drawPath(area, fill);

        // A hairline at the month's low, so the line reads against a level.
        Paint rule = new Paint();
        rule.setColor(Scene.alpha(scene.line, 0.22f));
        c.drawRect(0, bottom, w, bottom + Math.max(1f, PXDP * 0.5f), rule);

        // The line is a thread of light in the haze: its light falling wide and faint on the glass under it, a wide,
        // faint bloom, a tight glow, and a warm core.
        Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
        glow.setStyle(Paint.Style.STROKE);
        glow.setStrokeCap(Paint.Cap.ROUND);
        glow.setStrokeJoin(Paint.Join.ROUND);
        Look look = Look.get();
        float mist = (float) Math.sqrt(look.haze), hal = look.halationGlow();
        glow.setStrokeWidth(LINE_DP * PXDP * 14f * mist);
        glow.setColor(alpha(scene.deep, Math.min(255, Math.round(0x22 * look.bloom * look.haze))));
        glow.setMaskFilter(new BlurMaskFilter(LINE_DP * PXDP * 12f * mist, BlurMaskFilter.Blur.NORMAL));
        c.drawPath(curve, glow); // its light, scattered wide by the haze
        if (hal > 0f) { // halation: a red-orange glow along the thread, just past its tight glow
            glow.setStrokeWidth(LINE_DP * PXDP * 7f);
            glow.setColor(Scene.alpha(Optics.HALATION, Math.min(1f, 0.2f * hal)));
            glow.setMaskFilter(new BlurMaskFilter(LINE_DP * PXDP * 4f, BlurMaskFilter.Blur.NORMAL));
            c.drawPath(curve, glow);
        }
        glow.setStrokeWidth(LINE_DP * PXDP * 5f);
        glow.setColor(alpha(scene.deep, Math.min(255, Math.round(LINE_ALPHA * 0.5f * look.bloom))));
        glow.setMaskFilter(new BlurMaskFilter(LINE_DP * PXDP * 5f, BlurMaskFilter.Blur.NORMAL));
        c.drawPath(curve, glow);
        glow.setStrokeWidth(LINE_DP * PXDP * 2f);
        glow.setColor(alpha(scene.deep, Math.min(255, Math.round(LINE_ALPHA * 0.9f * look.bloom))));
        glow.setMaskFilter(new BlurMaskFilter(LINE_DP * PXDP * 1.5f, BlurMaskFilter.Blur.NORMAL));
        c.drawPath(curve, glow);

        Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeWidth(LINE_DP * PXDP);
        line.setStrokeCap(Paint.Cap.ROUND);
        line.setStrokeJoin(Paint.Join.ROUND);
        // a trace of the colour fringe of a bright light through a lens (a line gets little: Lens.LINES)
        float shift = look.fringeDp * PXDP;
        c.save();
        c.translate(-shift, 0);
        line.setColor(Scene.alpha(Lens.RED, look.fringe * Lens.LINES));
        c.drawPath(curve, line);
        c.translate(2 * shift, 0);
        line.setColor(Scene.alpha(Lens.BLUE, look.fringe * Lens.LINES));
        c.drawPath(curve, line);
        c.restore();
        line.setColor(alpha(scene.hot, Math.round(LINE_ALPHA * 1.4f)));
        c.drawPath(curve, line);
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
