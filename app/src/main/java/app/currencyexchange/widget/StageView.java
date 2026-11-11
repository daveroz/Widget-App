package app.currencyexchange.widget;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
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

import java.util.Random;

/**
 * The room behind the settings screen, as on the Deco launcher: a dark stage in haze, lit in amber. Two washes
 * of the key light drift slowly, a strong one high on the right and a fainter one low on the left; fog banks
 * drift through, and a row of small lamps along the top runs a slow chase, like a lighting rig seen through haze.
 *
 * Every light has two parts: a soft glow, drawn at 1/8 of the screen's size and stretched so it spreads like
 * light in fog, and a sharp core drawn at full detail. A fine grain over the haze keeps its gradients smooth.
 */
final class StageView extends View {
    private static final int SOFT = 8;
    private static final long FRAME_MS = 33;
    private static final int FOG = 0xFFB4C8E1;

    private final Paint softPaint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
    private final Paint glowPaint = new Paint(Paint.DITHER_FLAG);
    private final Paint corePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint grainPaint = new Paint();
    private final Matrix matrix = new Matrix();
    private final Rect screen = new Rect();
    private final Shader key = unit(Theme.AMBER);
    private final Shader led = unit(Theme.LED);
    private final Shader fog = unit(FOG);
    private Bitmap soft;
    private Canvas softCanvas;
    private boolean running;
    private long lastFrame;
    private float air;

    StageView(Context context) {
        super(context);
        glowPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.ADD)); // light adds up
        grainPaint.setShader(new BitmapShader(grain(), Shader.TileMode.REPEAT, Shader.TileMode.REPEAT));
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
        int rig = w > h ? 9 : 7;
        float dp = getResources().getDisplayMetrics().density;
        soft.eraseColor(0);
        glow(key, w * (0.82f + 0.03f * (float) Math.sin(air * 0.07)), h * (0.15f + 0.02f * (float) Math.cos(air * 0.05)),
                big * 0.42f, big * 0.42f, 0.16f);
        glow(key, w * (0.16f + 0.05f * (float) Math.sin(air * 0.06 + 1)), h * 0.9f, big * 0.4f, big * 0.4f, 0.075f);
        for (int i = 0; i < rig; i++) {
            glow(i % 2 == 1 ? led : key, w * (i + 0.5f) / rig, 12 * dp, 44 * dp, 44 * dp, brightness(i, rig) * 0.3f);
        }
        for (int i = 0; i < 2; i++) {
            float x = ((air * (7 + i * 4) / 1280f + i * 0.6f) % 1.6f - 0.3f) * w;
            glow(fog, x, h * (0.36f + i * 0.28f), w * 0.95f, h * 0.13f, 0.05f);
        }

        screen.set(0, 0, w, h);
        canvas.drawBitmap(soft, null, screen, softPaint);
        for (int i = 0; i < rig; i++) {
            int tone = i % 2 == 1 ? Theme.LED : mix(Theme.AMBER, Theme.LED, 0.6f);
            corePaint.setColor(alpha(tone, Math.min(1f, brightness(i, rig) + 0.2f)));
            float x = w * (i + 0.5f) / rig;
            canvas.drawRect(x - 3 * dp, 4 * dp, x + 3 * dp, 5.5f * dp, corePaint);
        }
        canvas.drawRect(screen, grainPaint);

        if (running) postInvalidateDelayed(FRAME_MS);
    }

    /** A rig lamp's brightness: a slow chase runs along the row. */
    private float brightness(int i, int count) {
        float chase = (float) Math.pow(Math.max(0, Math.cos(Math.PI * 2 * (i / (float) count - air / 7))), 4);
        return 0.22f + 0.5f * chase;
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

    private static int alpha(int colour, float a) {
        return (Math.round(Math.max(0, Math.min(1, a)) * 255) << 24) | (colour & 0x00FFFFFF);
    }

    private static int mix(int a, int b, float t) {
        int r = Math.round(((a >> 16) & 0xFF) + (((b >> 16) & 0xFF) - ((a >> 16) & 0xFF)) * t);
        int g = Math.round(((a >> 8) & 0xFF) + (((b >> 8) & 0xFF) - ((a >> 8) & 0xFF)) * t);
        int bl = Math.round((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

    /** A tile of faint white specks, like film grain. */
    private static Bitmap grain() {
        int size = 128;
        int[] pixels = new int[size * size];
        Random random = new Random(7);
        for (int i = 0; i < pixels.length; i++) {
            float v = random.nextFloat();
            int a = v > 0.5f ? Math.round((v - 0.5f) * 2 * 14) : 0;
            pixels[i] = (a << 24) | 0x00FFFFFF;
        }
        return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888);
    }
}
