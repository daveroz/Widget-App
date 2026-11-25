package app.currencyexchange.widget;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

/**
 * The settings screen's icons (2.20), drawn as fine line icons on a 24-unit grid with round ends, as in the segmented
 * control and tab bar references: outlined, or (for the tab that's open) filled, with their inner lines cut out of
 * the fill. One colour each.
 */
final class Glyph extends Drawable {
    static final int GLOBE = 0, CLOCK = 1, RATES = 2, WIDGETS = 3, STATUS = 4, REFRESH = 5, SPARKLE = 6, IMAGE = 7,
            WAVE = 8, STOPWATCH = 9, CHEVRON = 10, PLUS = 11, PIN = 12, CALENDAR = 13, BATTERY = 14, NOTE = 15,
            LEVELS = 16, CHECK = 17, PLAY = 18, PAUSE = 19, PREV = 20, NEXT = 21, SWAP = 22;

    private final int shape;
    private final boolean filled;
    private int colour;
    private final float weight;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint cut = new Paint(Paint.ANTI_ALIAS_FLAG);

    /** {@code shape} in {@code colour}, filled or outlined, its lines {@code weight} units wide (about 1.7). */
    Glyph(int shape, int colour, boolean filled, float weight) {
        this.shape = shape;
        this.colour = colour;
        this.filled = filled;
        this.weight = weight;
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        cut.setStrokeCap(Paint.Cap.ROUND);
        cut.setStrokeJoin(Paint.Join.ROUND);
        cut.setStyle(Paint.Style.STROKE);
        cut.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
    }

    Glyph(int shape, int colour, boolean filled) {
        this(shape, colour, filled, 1.7f);
    }

    void setColour(int c) {
        colour = c;
        invalidateSelf();
    }

    @Override
    public void draw(Canvas canvas) {
        Rect b = getBounds();
        if (b.width() <= 0 || b.height() <= 0) return;
        float s = Math.min(b.width(), b.height()) / 24f;
        canvas.save();
        canvas.translate(b.centerX() - 12 * s, b.centerY() - 12 * s);
        canvas.scale(s, s);
        paint.setColor(colour);
        paint.setStrokeWidth(weight);
        cut.setStrokeWidth(weight);
        if (filled) {
            int layer = canvas.saveLayer(-2, -2, 26, 26, null);
            draw(canvas, true);
            canvas.restoreToCount(layer);
        } else {
            draw(canvas, false);
        }
        canvas.restore();
    }

    /** Draws the shape on the 24-unit grid: its outline, or its body filled and its inner lines cut out. */
    private void draw(Canvas c, boolean fill) {
        Paint line = paint;
        Path p = new Path();
        switch (shape) {
            case GLOBE: {
                body(c, fill, circle(12, 12, 9));
                Path inner = new Path();
                inner.addOval(new RectF(8, 3, 16, 21), Path.Direction.CW);
                inner.moveTo(3, 12);
                inner.lineTo(21, 12);
                inner(c, fill, inner);
                break;
            }
            case CLOCK: {
                body(c, fill, circle(12, 12, 9));
                p.moveTo(12, 7);
                p.lineTo(12, 12);
                p.lineTo(15.5f, 14);
                inner(c, fill, p);
                break;
            }
            case RATES: {
                body(c, fill, circle(12, 12, 9));
                p.moveTo(7.5f, 9.5f);
                p.lineTo(16.5f, 9.5f);
                p.moveTo(14, 7);
                p.lineTo(16.5f, 9.5f);
                p.lineTo(14, 12);
                p.moveTo(16.5f, 14.5f);
                p.lineTo(7.5f, 14.5f);
                p.moveTo(10, 12);
                p.lineTo(7.5f, 14.5f);
                p.lineTo(10, 17);
                inner(c, fill, p);
                break;
            }
            case WIDGETS: {
                Path sq = new Path();
                sq.addRoundRect(new RectF(3.5f, 3.5f, 10.5f, 10.5f), 2, 2, Path.Direction.CW);
                sq.addRoundRect(new RectF(13.5f, 3.5f, 20.5f, 10.5f), 2, 2, Path.Direction.CW);
                sq.addRoundRect(new RectF(3.5f, 13.5f, 10.5f, 20.5f), 2, 2, Path.Direction.CW);
                sq.addRoundRect(new RectF(13.5f, 13.5f, 20.5f, 20.5f), 2, 2, Path.Direction.CW);
                body(c, fill, sq);
                break;
            }
            case STATUS: {
                Path shield = new Path();
                shield.moveTo(12, 3);
                shield.lineTo(19, 6);
                shield.lineTo(19, 11.5f);
                shield.cubicTo(19, 16, 16, 19.2f, 12, 21);
                shield.cubicTo(8, 19.2f, 5, 16, 5, 11.5f);
                shield.lineTo(5, 6);
                shield.close();
                body(c, fill, shield);
                p.moveTo(8.8f, 12);
                p.lineTo(11, 14.2f);
                p.lineTo(15.4f, 9.8f);
                inner(c, fill, p);
                break;
            }
            case REFRESH: {
                line.setStyle(Paint.Style.STROKE);
                p.addArc(new RectF(4.5f, 4.5f, 19.5f, 19.5f), -60, 290);
                c.drawPath(p, line);
                Path head = new Path();
                head.moveTo(15.2f, 2.6f);
                head.lineTo(16.5f, 6.1f);
                head.lineTo(12.9f, 7.2f);
                c.drawPath(head, line);
                break;
            }
            case SPARKLE: {
                p.moveTo(12, 3);
                p.cubicTo(12.8f, 8.4f, 15.6f, 11.2f, 21, 12);
                p.cubicTo(15.6f, 12.8f, 12.8f, 15.6f, 12, 21);
                p.cubicTo(11.2f, 15.6f, 8.4f, 12.8f, 3, 12);
                p.cubicTo(8.4f, 11.2f, 11.2f, 8.4f, 12, 3);
                p.close();
                body(c, fill, p);
                break;
            }
            case IMAGE: {
                Path frame = new Path();
                frame.addRoundRect(new RectF(3.5f, 5, 20.5f, 19), 2.5f, 2.5f, Path.Direction.CW);
                body(c, fill, frame);
                p.moveTo(6.5f, 16);
                p.lineTo(10, 12);
                p.lineTo(13, 15);
                p.lineTo(15, 13);
                p.lineTo(17.5f, 16);
                p.addCircle(15.5f, 9, 1.3f, Path.Direction.CW);
                inner(c, fill, p);
                break;
            }
            case WAVE: {
                line.setStyle(Paint.Style.STROKE);
                p.moveTo(3, 12);
                p.cubicTo(5.5f, 5.5f, 9.5f, 5.5f, 12, 12);
                p.cubicTo(14.5f, 18.5f, 18.5f, 18.5f, 21, 12);
                c.drawPath(p, line);
                break;
            }
            case STOPWATCH: {
                body(c, fill, circle(12, 13.5f, 7.5f));
                Path top = new Path();
                top.moveTo(10, 3);
                top.lineTo(14, 3);
                top.moveTo(12, 3);
                top.lineTo(12, 6);
                line.setStyle(Paint.Style.STROKE);
                c.drawPath(top, line);
                p.moveTo(12, 13.5f);
                p.lineTo(12, 9.5f);
                inner(c, fill, p);
                break;
            }
            case CHEVRON: {
                line.setStyle(Paint.Style.STROKE);
                p.moveTo(10, 6.5f);
                p.lineTo(15.5f, 12);
                p.lineTo(10, 17.5f);
                c.drawPath(p, line);
                break;
            }
            case PLUS: {
                line.setStyle(Paint.Style.STROKE);
                p.moveTo(12, 5);
                p.lineTo(12, 19);
                p.moveTo(5, 12);
                p.lineTo(19, 12);
                c.drawPath(p, line);
                break;
            }
            case PIN: {
                Path pin = new Path();
                pin.moveTo(12, 21);
                pin.cubicTo(12, 21, 5.5f, 15, 5.5f, 9.8f);
                pin.arcTo(new RectF(5.5f, 3.3f, 18.5f, 16.3f), 180, 180);
                pin.cubicTo(18.5f, 15, 12, 21, 12, 21);
                pin.close();
                body(c, fill, pin);
                p.addCircle(12, 9.8f, 2.2f, Path.Direction.CW);
                inner(c, fill, p);
                break;
            }
            case CALENDAR: {
                Path cal = new Path();
                cal.addRoundRect(new RectF(4, 5.5f, 20, 20), 2.5f, 2.5f, Path.Direction.CW);
                body(c, fill, cal);
                Path pegs = new Path();
                pegs.moveTo(8.5f, 3.5f);
                pegs.lineTo(8.5f, 7);
                pegs.moveTo(15.5f, 3.5f);
                pegs.lineTo(15.5f, 7);
                line.setStyle(Paint.Style.STROKE);
                c.drawPath(pegs, line);
                p.moveTo(4, 10);
                p.lineTo(20, 10);
                inner(c, fill, p);
                break;
            }
            case BATTERY: {
                Path bat = new Path();
                bat.addRoundRect(new RectF(3, 8, 18.5f, 16), 2.2f, 2.2f, Path.Direction.CW);
                body(c, fill, bat);
                Path nub = new Path();
                nub.moveTo(21, 10.8f);
                nub.lineTo(21, 13.2f);
                line.setStyle(Paint.Style.STROKE);
                c.drawPath(nub, line);
                if (!fill) {
                    Paint solid = new Paint(line);
                    solid.setStyle(Paint.Style.FILL);
                    c.drawRoundRect(new RectF(5.6f, 10.6f, 12.5f, 13.4f), 0.8f, 0.8f, solid);
                }
                break;
            }
            case NOTE: {
                line.setStyle(Paint.Style.STROKE);
                p.moveTo(9, 17.5f);
                p.lineTo(9, 6);
                p.lineTo(19, 4);
                p.lineTo(19, 15.5f);
                p.moveTo(9, 9.5f);
                p.lineTo(19, 7.5f);
                c.drawPath(p, line);
                Path heads = new Path();
                heads.addCircle(6.8f, 17.6f, 2.2f, Path.Direction.CW);
                heads.addCircle(16.8f, 15.6f, 2.2f, Path.Direction.CW);
                body(c, fill, heads);
                break;
            }
            case LEVELS: {
                line.setStyle(Paint.Style.STROKE);
                float[][] bars = {{4.5f, 10, 14}, {8.25f, 7, 17}, {12, 4, 20}, {15.75f, 8, 16}, {19.5f, 10.5f, 13.5f}};
                for (float[] bar : bars) {
                    p.moveTo(bar[0], bar[1]);
                    p.lineTo(bar[0], bar[2]);
                }
                c.drawPath(p, line);
                break;
            }
            case PLAY: { // filled when it's on a lit button: a triangle with softened corners
                p.moveTo(8, 5.5f);
                p.lineTo(18.5f, 12);
                p.lineTo(8, 18.5f);
                p.close();
                body(c, fill, p);
                break;
            }
            case PAUSE: {
                line.setStyle(Paint.Style.STROKE);
                float was = line.getStrokeWidth();
                if (fill) line.setStrokeWidth(was * 1.9f);
                p.moveTo(8.5f, 6.5f);
                p.lineTo(8.5f, 17.5f);
                p.moveTo(15.5f, 6.5f);
                p.lineTo(15.5f, 17.5f);
                c.drawPath(p, line);
                line.setStrokeWidth(was);
                break;
            }
            case PREV:
            case NEXT: {
                boolean next = shape == NEXT;
                float d = next ? 1 : -1;
                p.moveTo(12 - d * 5, 6.5f);
                p.lineTo(12 + d * 3.5f, 12);
                p.lineTo(12 - d * 5, 17.5f);
                p.close();
                body(c, fill, p);
                Path bar = new Path();
                bar.moveTo(12 + d * 6.5f, 6.5f);
                bar.lineTo(12 + d * 6.5f, 17.5f);
                line.setStyle(Paint.Style.STROKE);
                c.drawPath(bar, line);
                break;
            }
            case SWAP: { // up and down arrows side by side
                line.setStyle(Paint.Style.STROKE);
                p.moveTo(8.5f, 19);
                p.lineTo(8.5f, 5);
                p.moveTo(5, 8.5f);
                p.lineTo(8.5f, 5);
                p.lineTo(12, 8.5f);
                p.moveTo(15.5f, 5);
                p.lineTo(15.5f, 19);
                p.moveTo(12, 15.5f);
                p.lineTo(15.5f, 19);
                p.lineTo(19, 15.5f);
                c.drawPath(p, line);
                break;
            }
            case CHECK: {
                line.setStyle(Paint.Style.STROKE);
                p.moveTo(6, 12.5f);
                p.lineTo(10, 16.5f);
                p.lineTo(18, 8);
                c.drawPath(p, line);
                break;
            }
            default:
                break;
        }
    }

    private static Path circle(float x, float y, float r) {
        Path p = new Path();
        p.addCircle(x, y, r, Path.Direction.CW);
        return p;
    }

    /** The shape's body: outlined, or filled. */
    private void body(Canvas c, boolean fill, Path p) {
        paint.setStyle(fill ? Paint.Style.FILL_AND_STROKE : Paint.Style.STROKE);
        c.drawPath(p, paint);
    }

    /** Lines inside the body: drawn, or cut out of the fill. */
    private void inner(Canvas c, boolean fill, Path p) {
        if (fill) {
            c.drawPath(p, cut);
        } else {
            paint.setStyle(Paint.Style.STROKE);
            c.drawPath(p, paint);
        }
    }

    @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); }
    @Override public void setColorFilter(ColorFilter cf) { paint.setColorFilter(cf); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
