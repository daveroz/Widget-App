package app.currencyexchange.widget;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.Gravity;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * A choice of a few options (2.20), after the segmented control reference: a recessed track with the options side by
 * side, each an icon and a word, and the chosen one raised on a pill that slides across to whatever is picked. The
 * chosen option is white, the others a quiet slate. In the dark: the track a breath lighter than the card, the pill
 * lighter still, its top edge catching the light, a soft shadow under it.
 */
final class Segmented extends LinearLayout {
    interface Listener { void picked(int index); }

    static final int ON = 0xFFF4F7FA, OFF = 0xFF7D8899;
    private static final int TRACK = 0xFF10131A, PILL_TOP = 0xFF2C313B, PILL_BOTTOM = 0xFF22262E;

    private final float px, pad, radius;
    private final TextView[] labels;
    private final Glyph[] icons;
    private final Listener listener;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    private final RectF box = new RectF(), pill = new RectF();
    private int selected;
    /** Where the pill is, in options (0 = over the first; fractions while it slides). */
    private float at;
    private ValueAnimator slide;

    /**
     * Options {@code words} (with {@code glyphs}, {@link Glyph} shapes, or null for words alone), {@code selected}
     * picked; {@code listener} hears when another is picked.
     */
    Segmented(Context c, String[] words, int[] glyphs, int selected, Listener listener) {
        this(c, words, glyphs, null, selected, listener);
    }

    /** As above with a small round picture before each word ({@code images}: the converter's flags, 2.23). */
    Segmented(Context c, String[] words, android.graphics.Bitmap[] images, int selected, Listener listener) {
        this(c, words, null, images, selected, listener);
    }

    private Segmented(Context c, String[] words, int[] glyphs, android.graphics.Bitmap[] images, int selected,
                      Listener listener) {
        super(c);
        this.listener = listener;
        this.selected = selected;
        this.at = selected;
        px = c.getResources().getDisplayMetrics().density;
        pad = 4 * px;
        radius = 14 * px;
        setOrientation(HORIZONTAL);
        setWillNotDraw(false);
        setPadding(Math.round(pad), Math.round(pad), Math.round(pad), Math.round(pad));
        labels = new TextView[words.length];
        icons = new Glyph[words.length];
        for (int i = 0; i < words.length; i++) {
            final int index = i;
            LinearLayout option = new LinearLayout(c);
            option.setOrientation(HORIZONTAL);
            option.setGravity(Gravity.CENTER);
            option.setMinimumHeight(Math.round(42 * px));
            option.setPadding(Math.round(4 * px), 0, Math.round(4 * px), 0);
            if (glyphs != null) {
                ImageView icon = new ImageView(c);
                icons[i] = new Glyph(glyphs[i], i == selected ? ON : OFF, false, 1.6f);
                icon.setImageDrawable(icons[i]);
                LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(Math.round(19 * px), Math.round(19 * px));
                ip.rightMargin = Math.round(7 * px);
                option.addView(icon, ip);
            } else if (images != null && images[i] != null) {
                ImageView pic = new ImageView(c);
                pic.setImageBitmap(images[i]);
                LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(Math.round(18 * px), Math.round(18 * px));
                ip.rightMargin = Math.round(6 * px);
                option.addView(pic, ip);
            }
            TextView t = new Theme.FringeText(c);
            t.setText(words[i].toUpperCase(java.util.Locale.getDefault())); // a button: spaced capitals (2.23)
            t.setTextSize(12.5f);
            t.setTypeface(Theme.uiBold(c));
            t.setLetterSpacing(0.14f);
            t.setTextColor(i == selected ? ON : OFF);
            t.setSingleLine(true);
            labels[i] = t;
            option.addView(t);
            option.setClickable(true);
            option.setOnClickListener(v -> select(index, true));
            addView(option, new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        }
    }

    /** Picks option {@code index}, the pill sliding over to it (or jumping, without {@code animate}). */
    void select(int index, boolean animate) {
        if (index == selected && animate) return;
        int from = selected;
        selected = index;
        if (slide != null) slide.cancel();
        if (animate && isAttachedToWindow()) {
            slide = ValueAnimator.ofFloat(at, index);
            slide.setDuration(240);
            slide.setInterpolator(new DecelerateInterpolator(1.6f));
            slide.addUpdateListener(a -> {
                at = (float) a.getAnimatedValue();
                paintLabels();
                invalidate();
            });
            slide.start();
        } else {
            at = index;
            paintLabels();
            invalidate();
        }
        if (from != index && listener != null && animate) listener.picked(index);
    }

    int selected() { return selected; }

    /** The words and icons: white under the pill, slate elsewhere, blending as it passes. */
    private void paintLabels() {
        for (int i = 0; i < labels.length; i++) {
            float near = Math.max(0f, 1f - Math.abs(at - i));
            int c = Scene.mix(OFF, ON, near);
            labels[i].setTextColor(c);
            if (icons[i] != null) icons[i].setColour(c);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth(), h = getHeight(), n = labels.length;
        if (w <= 0 || h <= 0 || n == 0) return;
        // the track, recessed: a breath lighter than the card, a faint line round it
        box.set(0, 0, w, h);
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(TRACK);
        canvas.drawRoundRect(box, radius, radius, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(1f, px * 0.8f));
        paint.setColor(Scene.alpha(Stage.ICE, 0.05f));
        box.inset(px * 0.4f, px * 0.4f);
        canvas.drawRoundRect(box, radius, radius, paint);
        // the pill over the chosen option, raised: a soft shadow, a lighter fill, its top edge catching the light
        float cell = (w - 2 * pad) / n, r = radius - pad * 0.6f;
        pill.set(pad + at * cell, pad, pad + (at + 1) * cell, h - pad);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0x66000000);
        paint.setMaskFilter(new android.graphics.BlurMaskFilter(4 * px, android.graphics.BlurMaskFilter.Blur.NORMAL));
        canvas.save();
        canvas.translate(0, 1.5f * px);
        canvas.drawRoundRect(pill, r, r, paint);
        canvas.restore();
        paint.setMaskFilter(null);
        paint.setShader(new LinearGradient(0, pill.top, 0, pill.bottom, PILL_TOP, PILL_BOTTOM, Shader.TileMode.CLAMP));
        canvas.drawRoundRect(pill, r, r, paint);
        paint.setShader(new LinearGradient(0, pill.top, 0, pill.top + pill.height() * 0.5f,
                new int[]{Scene.alpha(Stage.ICE, 0.2f), Scene.alpha(Stage.ICE, 0.05f), 0x00000000}, new float[]{0f, 0.4f, 1f},
                Shader.TileMode.CLAMP));
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(1f, px));
        RectF edge = new RectF(pill);
        edge.inset(px * 0.5f, px * 0.5f);
        canvas.drawRoundRect(edge, r, r, paint);
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
    }
}
