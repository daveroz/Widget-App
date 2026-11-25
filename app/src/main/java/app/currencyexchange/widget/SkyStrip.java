package app.currencyexchange.widget;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.Handler;
import android.os.HandlerThread;
import android.view.View;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * The home page's hero (2.20): the four world clocks as the Currency widget shows them, each on its city's sky and
 * corner right now ({@link SkyCell}), side by side in one glass card, standing where the car stands in the reference,
 * and reflected under it on the dark as on a polished floor, fading away. The skies move while it's on screen (about
 * eight frames a second when skies are animated, otherwise a picture a minute); the frames are drawn on a thread of
 * their own.
 */
final class SkyStrip extends View {
    /** A clock's height for its width: a little taller than the widget's square clocks, so more sky shows. */
    private static final float TALL = 1.45f;
    /** How tall the reflection is, for the strip's height, and how bright at its top. */
    private static final float REFLECT = 0.4f, REFLECT_ALPHA = 0.26f;
    private static final long FRAME_MS = 125, STILL_MS = 30_000;

    private final float px, radius, seam, gap;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    /** For drawing on screen (the frames are drawn with {@link #paint}, on their own thread). */
    private final Paint shown = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint mask = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint day = new Paint(Paint.ANTI_ALIAS_FLAG), time = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Object lock = new Object();
    private Bitmap front, back;
    private HandlerThread thread;
    private Handler worker;
    private boolean running;
    private CurrencyView.Clock[] clocks;
    private long clocksAt;
    private final DateTimeFormatter dayF = DateTimeFormatter.ofPattern("EEEE", Locale.getDefault());
    private final DateTimeFormatter timeF = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault());

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            long ms = System.currentTimeMillis();
            compose(ms);
            postInvalidate();
            if (running && worker != null) {
                boolean animated = Store.animateSkies(getContext());
                long wait = animated ? FRAME_MS - ms % FRAME_MS : STILL_MS - ms % STILL_MS;
                worker.postDelayed(this, Math.max(16, wait));
            }
        }
    };

    SkyStrip(Context c) {
        super(c);
        px = c.getResources().getDisplayMetrics().density;
        radius = 20 * px;
        seam = Math.max(1f, px);
        gap = 7 * px;
        mask.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
        day.setTypeface(Theme.uiMedium(c));
        day.setColor(Theme.TEXT);
        day.setTextAlign(Paint.Align.CENTER);
        day.setTextSize(10.5f * px);
        time.setTypeface(Theme.uiMedium(c));
        time.setColor(Theme.TEXT);
        time.setTextAlign(Paint.Align.CENTER);
        time.setTextSize(22.5f * px);
        setClickable(true);
    }

    /** The strip's own height (the clocks), for a width of {@code w} pixels. */
    private int stripHeight(int w) {
        return Math.round((w - 3 * seam) / 4f * TALL);
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int w = MeasureSpec.getSize(widthSpec), strip = stripHeight(w);
        setMeasuredDimension(w, strip + Math.round(gap + strip * REFLECT));
    }

    /** Moves while the screen (and this page) is in front; otherwise it holds still. */
    void setRunning(boolean run) {
        if (run == running) return;
        running = run;
        if (run) {
            if (thread == null) {
                thread = new HandlerThread("hero-frames", android.os.Process.THREAD_PRIORITY_DISPLAY);
                thread.start();
                worker = new Handler(thread.getLooper());
            }
            worker.removeCallbacks(tick);
            worker.post(tick);
        } else if (worker != null) {
            worker.removeCallbacks(tick);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        setRunning(false);
        if (thread != null) {
            thread.quitSafely();
            thread = null;
            worker = null;
        }
        super.onDetachedFromWindow();
    }

    /** Draws the four clocks at {@code ms} into the spare picture, then makes it the one shown. */
    private void compose(long ms) {
        int w = getWidth();
        if (w <= 0) return;
        int h = stripHeight(w);
        Bitmap b;
        synchronized (lock) {
            b = back;
        }
        if (b == null || b.getWidth() != w || b.getHeight() != h) b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Context ctx = getContext();
        if (clocks == null || ms - clocksAt > 15_000L || ms < clocksAt) {
            clocks = CurrencyView.clocks(ctx, ms);
            clocksAt = ms;
        }
        Canvas c = new Canvas(b);
        c.drawColor(0, PorterDuff.Mode.CLEAR);
        float cellW = (w - 3 * seam) / 4f;
        Paint.FontMetrics dm = day.getFontMetrics(), tm = time.getFontMetrics();
        float dayH = dm.descent - dm.ascent, timeH = tm.descent - tm.ascent, lead = -0.18f * timeH;
        float block = dayH * 2 + timeH + 2 * lead;
        for (int i = 0; i < 4; i++) {
            CurrencyView.Clock k = clocks[i];
            int x0 = Math.round(i * (cellW + seam)), x1 = i == 3 ? w : Math.round(i * (cellW + seam) + cellW);
            SkyCell cell = SkyCell.get(k.city, k.lat, k.lon, k.zone, k.elevation, k.morning, k.cloud, k.code, k.wind,
                    x1 - x0, h, px, i == 0 ? 1 : (i == 3 ? 2 : 0), radius);
            c.drawBitmap(cell.frame(ms), x0, 0, paint);
            // the day, time and city in front, white and centred, as on the widget
            ZonedDateTime now = Instant.ofEpochMilli(ms).atZone(k.zone);
            float cx = (x0 + x1) / 2f, top = (h - block) / 2f;
            text(c, day, now.format(dayF), cx, top - dm.ascent);
            text(c, time, now.format(timeF), cx, top + dayH + lead - tm.ascent);
            text(c, day, Util.capWords(k.city), cx, top + dayH + timeH + 2 * lead - dm.ascent);
            if (i < 3) { // the hairline between two clocks, as on the widget
                paint.setColor(Scene.alpha(Stage.ICE, 0.24f));
                c.drawRect(x1, 0, x1 + seam, h, paint);
                paint.setColor(0xFF000000);
            }
        }
        // the card's glass edge over the skies, with its glint, as every card's
        Stage.rim(c, new RectF(0, 0, w, h), Stage.round(radius), px);
        synchronized (lock) {
            back = front;
            front = b;
        }
    }

    private void text(Canvas c, Paint p, String s, float x, float y) {
        Lens.text(c, p, s, x, y, px); // the fringe all text has
        c.drawText(s, x, y, p);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth();
        if (w <= 0) return;
        int h = stripHeight(w);
        Bitmap f;
        synchronized (lock) {
            f = front;
        }
        if ((f == null || f.getWidth() != w) && !canvas.isHardwareAccelerated()) { // a picture of the screen: draw it now
            compose(System.currentTimeMillis());
            synchronized (lock) {
                f = front;
            }
        }
        if (f == null || f.getWidth() != w) {
            if (worker != null) worker.post(tick);
            return;
        }
        canvas.drawBitmap(f, 0, 0, shown);
        // its reflection on the dark, as on a polished floor: upside down under it, fading away
        float top = h + gap, bottom = top + h * REFLECT;
        int layer = canvas.saveLayer(0, top, w, bottom, null);
        canvas.save();
        canvas.translate(0, 2 * h + gap);
        canvas.scale(1, -1);
        canvas.drawBitmap(f, 0, 0, shown);
        canvas.restore();
        mask.setShader(new LinearGradient(0, top, 0, bottom, new int[]{Scene.alpha(0xFF000000, REFLECT_ALPHA),
                Scene.alpha(0xFF000000, REFLECT_ALPHA * 0.35f), 0x00000000}, new float[]{0f, 0.4f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawRect(0, top, w, bottom, mask);
        canvas.restoreToCount(layer);
    }
}
