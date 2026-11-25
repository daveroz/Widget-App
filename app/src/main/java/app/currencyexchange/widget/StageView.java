package app.currencyexchange.widget;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.Shader;
import android.view.View;
import android.view.animation.AnimationUtils;


/**
 * The room behind the settings screen: near black, as the minimal references are, lit only by two slow, faint washes
 * of the playing song's light (amber when nothing plays), a strong one high on the right and a fainter one low on the
 * left, with a breath of fog drifting through. There's no lighting rig any more: nothing along the top, nothing that
 * chases.
 *
 * The washes are drawn at 1/8 of the screen's size and stretched, so they spread like light in fog. Since 2.06 they
 * light the crystal logo's deep space: a navy haze, out-of-focus specks and a film grain ({@link #space}).
 */
final class StageView extends View {
    private static final int SOFT = 8;
    private static final long FRAME_MS = 50;
    private static final int FOG = 0xFFB4C8E1;

    private final Paint softPaint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
    private final Paint glowPaint = new Paint(Paint.DITHER_FLAG);
    private final Matrix matrix = new Matrix();
    private final Rect screen = new Rect();
    private final Shader key = unit(Theme.accent().deep);
    private final Shader fog = unit(FOG);
    private Bitmap soft, space;
    private Canvas softCanvas;
    private final Rect full = new Rect();
    private final android.graphics.RectF fullF = new android.graphics.RectF();
    private boolean running;
    private long lastFrame;
    private float air;

    StageView(Context context) {
        super(context);
        glowPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.ADD)); // light adds up
    }

    /** Animates while the screen is in front; otherwise it holds still. */
    void setRunning(boolean run) {
        if (running == run) return;
        running = run;
        lastFrame = 0;
        if (run) invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldW, int oldH) {
        if (w <= 0 || h <= 0) return;
        soft = Bitmap.createBitmap(Math.max(1, w / SOFT), Math.max(1, h / SOFT), Bitmap.Config.ARGB_8888);
        softCanvas = new Canvas(soft);
        softCanvas.scale(soft.getWidth() / (float) w, soft.getHeight() / (float) h);
        space = space(Math.max(1, w / 4), Math.max(1, h / 4));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        long now = AnimationUtils.currentAnimationTimeMillis();
        float dt = lastFrame == 0 ? 0f : Math.min(0.1f, (now - lastFrame) / 1000f);
        lastFrame = now;
        air += dt;
        int w = getWidth(), h = getHeight();
        canvas.drawColor(Theme.BG);
        if (soft == null) return;
        full.set(0, 0, w, h);
        canvas.drawBitmap(space, null, full, softPaint); // deep space, as behind the crystal logo

        float big = Math.max(w, h);
        soft.eraseColor(0);
        glow(key, w * (0.82f + 0.03f * (float) Math.sin(air * 0.07)), h * (0.12f + 0.02f * (float) Math.cos(air * 0.05)),
                big * 0.42f, big * 0.42f, 0.085f);
        glow(key, w * (0.16f + 0.05f * (float) Math.sin(air * 0.06 + 1)), h * 0.92f, big * 0.4f, big * 0.4f, 0.04f);
        for (int i = 0; i < 2; i++) {
            float x = ((air * (7 + i * 4) / 1280f + i * 0.6f) % 1.6f - 0.3f) * w;
            glow(fog, x, h * (0.36f + i * 0.28f), w * 0.95f, h * 0.13f, 0.025f);
        }
        screen.set(0, 0, w, h);
        canvas.drawBitmap(soft, null, screen, softPaint);
        fullF.set(0, 0, w, h);
        Stage.grain(canvas, fullF); // and its film grain

        if (running) postInvalidateDelayed(FRAME_MS);
    }

    /**
     * The deep space behind the cards, as behind the crystal logo (2.06), drawn small and stretched: a navy haze high up,
     * a warm one low on the right, and a scatter of out-of-focus specks, icy and warm.
     */
    private static Bitmap space(int w, int h) {
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        android.graphics.Paint p = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG
                | android.graphics.Paint.DITHER_FLAG);
        float big = Math.max(w, h);
        p.setShader(new android.graphics.RadialGradient(w * 0.5f, h * 0.28f, big * 0.75f, 0x3A16223A, 0x0016223A,
                Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        p.setShader(new android.graphics.RadialGradient(w * 0.85f, h * 0.88f, big * 0.45f, 0x30402010, 0x00402010,
                Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        java.util.Random r = new java.util.Random(0x59ACEL);
        for (int i = 0; i < 26; i++) {
            float x = r.nextFloat() * w, y = r.nextFloat() * h, rad = (1.2f + r.nextFloat() * 4f) * big / 270f;
            int col = r.nextFloat() < 0.62f ? Stage.ICE : Stage.WARM;
            p.setShader(new android.graphics.RadialGradient(x, y, rad, Scene.alpha(col, 0.05f + r.nextFloat() * 0.12f),
                    col & 0x00FFFFFF, Shader.TileMode.CLAMP));
            c.drawCircle(x, y, rad, p);
        }
        return b;
    }

    /** A soft glow on the small canvas, in screen pixels: centre, radii and strength. */
    private void glow(Shader shader, float x, float y, float rx, float ry, float strength) {
        if (strength < 0.004f) return;
        matrix.setScale(rx, ry);
        matrix.postTranslate(x, y);
        shader.setLocalMatrix(matrix);
        glowPaint.setShader(shader);
        glowPaint.setAlpha(Math.min(255, Math.round(strength * 255)));
        softCanvas.drawRect(x - rx, y - ry, x + rx, y + ry, glowPaint);
    }

    /** A round glow of radius 1 around the origin, from the colour to nothing. */
    private static Shader unit(int colour) {
        return new RadialGradient(0, 0, 1, colour | 0xFF000000, colour & 0x00FFFFFF, Shader.TileMode.CLAMP);
    }
}
