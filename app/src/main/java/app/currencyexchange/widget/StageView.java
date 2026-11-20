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
 * The washes are drawn at 1/8 of the screen's size and stretched, so they spread like light in fog. No grain.
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
    private Bitmap soft;
    private Canvas softCanvas;
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

        if (running) postInvalidateDelayed(FRAME_MS);
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
