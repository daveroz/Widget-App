package app.currencyexchange.widget;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;
import android.widget.LinearLayout;

/**
 * A bar of frosted glass floating at the top of the settings screen, after the Justice store's header: the cards
 * scroll under it and show through, heavily blurred, under a translucent dark grey, with a glass rim (light caught by
 * its edge, brightest at the top left) and a faint sheen along its top. Its corner catches the light like every card's ({@link Optics#catchlight}).
 * <p>
 * What's behind it is drawn small ({@link #DOWN} times smaller), blurred, and stretched back up: drawn again after
 * every scroll, and now and then for the room's slowly drifting light.
 */
final class GlassBar extends LinearLayout {
    /** What's behind is drawn this many times smaller than the screen... */
    private static final int DOWN = 8;
    /** ...then blurred this many of its pixels each way, three times over (close to a gaussian blur). */
    private static final int BLUR = 3;
    /** The room's light drifts slowly: what shows through catches up this often even when nothing scrolls. */
    private static final long DRIFT_MS = 700;
    /** How strong its rim is along its length (see {@link Theme#glassEdge}). */
    private static final float[] RIM = {0.55f, 0.46f, 0.3f, 0.24f, 0.28f, 0.36f, 0.4f};

    private final View room;
    private final float radius, px;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
    private final Path shape = new Path(), edge = new Path();
    private final RectF box = new RectF();
    private Bitmap backdrop;
    private Canvas backdropCanvas;
    private int[] pixels, spare;
    private boolean capturing, stale = true;
    /** Takes in what's behind just before a frame is drawn, when it's changed (never in the middle of drawing one). */
    private final android.view.ViewTreeObserver.OnPreDrawListener before = () -> {
        if (stale) capture();
        return true;
    };
    private final Runnable drift = new Runnable() {
        @Override
        public void run() {
            if (hasWindowFocus()) refresh(); // only while the screen is in front
            postDelayed(this, DRIFT_MS);
        }
    };

    /** A glass bar over {@code room} (the view it floats in, whose other children show through it). */
    GlassBar(Context c, View room, float radiusDp) {
        super(c);
        this.room = room;
        float density = c.getResources().getDisplayMetrics().density;
        this.radius = radiusDp * density;
        this.px = density;
        setWillNotDraw(false);
    }

    /** What's behind has changed (the cards scrolled, say): it's taken in again before the next frame. */
    void refresh() {
        stale = true;
        invalidate();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        getViewTreeObserver().addOnPreDrawListener(before);
        postDelayed(drift, DRIFT_MS);
    }

    @Override
    protected void onDetachedFromWindow() {
        removeCallbacks(drift);
        getViewTreeObserver().removeOnPreDrawListener(before);
        super.onDetachedFromWindow();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldW, int oldH) {
        super.onSizeChanged(w, h, oldW, oldH);
        stale = true;
        float in = Math.max(1f, px) / 2;
        box.set(0, 0, w, h);
        shape.rewind();
        shape.addRoundRect(box, radius, radius, Path.Direction.CW);
        edge.rewind();
        edge.addRoundRect(new RectF(in, in, w - in, h - in), radius - in, radius - in, Path.Direction.CW);
    }

    /** While it takes in what's behind, the bar itself (drawn as part of the room) is left out. */
    @Override
    public void draw(Canvas canvas) {
        if (capturing) return;
        super.draw(canvas);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        if (stale) capture(); // not taken in before this frame (drawn straight to a picture, say)
        Scene s = Theme.accent();
        canvas.save();
        canvas.clipPath(shape);
        if (backdrop != null) { // what's behind, blurred, stretched back up to where it is
            int m = margin();
            canvas.save();
            canvas.translate(-m, -m);
            canvas.scale(DOWN, DOWN);
            paint.setShader(null);
            canvas.drawBitmap(backdrop, 0, 0, paint);
            canvas.restore();
        }
        // the glass: a translucent dark grey with a breath of the song's colour, and a faint sheen along its top
        paint.setShader(null);
        paint.setColor(Scene.alpha(Scene.mix(0xFF4A4644, s.deep, 0.08f), 0.58f));
        canvas.drawRect(box, paint);
        paint.setShader(new LinearGradient(0, 0, 0, h * 0.6f, 0x14FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
        canvas.drawRect(box, paint);
        canvas.restore();
        // its rim: light caught by the glass's edge, brightest at the top left and back, softer, at the bottom right
        // (as the buttons'; see Theme.glassEdge), never fading as far as theirs
        int line = Stage.EDGE; // a fixed warm white, as every card's edge
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(1f, 1.2f * px));
        paint.setShader(Theme.glassEdge(box, line, RIM));
        canvas.drawPath(edge, paint);
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        Optics.catchlight(canvas, box, edge, radius - Math.max(1f, px) / 2, Math.max(1f, px) / 2, px, Stage.EDGE, 0.5f);
    }

    /** The room round the bar taken in too, so the blur at its edges has something to draw from. */
    private int margin() {
        return BLUR * 3 * DOWN;
    }

    /** Draws the room behind the bar, small, into the backdrop, and blurs it. */
    private void capture() {
        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0 || room.getWidth() <= 0) return;
        stale = false;
        int m = margin();
        int bw = (w + 2 * m) / DOWN + 1, bh = (h + 2 * m) / DOWN + 1;
        if (backdrop == null || backdrop.getWidth() != bw || backdrop.getHeight() != bh) {
            backdrop = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888);
            backdropCanvas = new Canvas(backdrop);
            pixels = new int[bw * bh];
            spare = new int[bw * bh];
        }
        backdrop.eraseColor(Theme.BG);
        // where the bar is in the room
        float x = 0, y = 0;
        for (View v = this; v != room && v != null; v = v.getParent() instanceof View ? (View) v.getParent() : null) {
            x += v.getLeft() + v.getTranslationX();
            y += v.getTop() + v.getTranslationY();
        }
        backdropCanvas.save();
        backdropCanvas.scale(1f / DOWN, 1f / DOWN);
        backdropCanvas.translate(m - x, m - y);
        capturing = true;
        try {
            room.draw(backdropCanvas);
        } finally {
            capturing = false;
            backdropCanvas.restore();
        }
        backdrop.getPixels(pixels, 0, bw, 0, 0, bw, bh);
        for (int i = 0; i < 3; i++) {
            boxBlur(pixels, spare, bw, bh, BLUR, true);
            boxBlur(spare, pixels, bw, bh, BLUR, false);
        }
        backdrop.setPixels(pixels, 0, bw, 0, 0, bw, bh);
    }

    /** One pass of a box blur {@code 2r + 1} wide, along the rows ({@code across}) or the columns, from src into dst. */
    private static void boxBlur(int[] src, int[] dst, int w, int h, int r, boolean across) {
        int lines = across ? h : w, length = across ? w : h;
        int step = across ? 1 : w, next = across ? w : 1;
        float n = 2 * r + 1;
        for (int l = 0; l < lines; l++) {
            int base = l * next;
            int a = 0, rr = 0, g = 0, b = 0;
            for (int k = -r; k <= r; k++) {
                int c = src[base + Math.max(0, Math.min(length - 1, k)) * step];
                a += c >>> 24;
                rr += (c >> 16) & 0xFF;
                g += (c >> 8) & 0xFF;
                b += c & 0xFF;
            }
            for (int i = 0; i < length; i++) {
                dst[base + i * step] = (Math.round(a / n) << 24) | (Math.round(rr / n) << 16) | (Math.round(g / n) << 8)
                        | Math.round(b / n);
                int out = src[base + Math.max(0, i - r) * step], in = src[base + Math.min(length - 1, i + r + 1) * step];
                a += (in >>> 24) - (out >>> 24);
                rr += ((in >> 16) & 0xFF) - ((out >> 16) & 0xFF);
                g += ((in >> 8) & 0xFF) - ((out >> 8) & 0xFF);
                b += (in & 0xFF) - (out & 0xFF);
            }
        }
    }
}
