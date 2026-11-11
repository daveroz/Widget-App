package app.currencyexchange.widget;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.widget.TextView;

/**
 * The Deco "light in fog" visual language, shared with the Deco launcher and SAB Monitor: a night stage in haze,
 * glass panels with hairline edges, and one amber key light. Something that's on is "lit" (a touch of the key on
 * the glass with a glowing key edge) rather than a solid block, and small labels are spaced bold capitals.
 * Type stays the original Carbon+ font from the KWGT preset. Used by the settings screen, the converter and the
 * widget renderer so they all feel like one family.
 */
final class Theme {
    private Theme() {}

    // surfaces (darkest to lightest)
    static final int BG = 0xFF05070B;        // the night behind everything
    static final int HEADER = 0xFF070A10;
    static final int PANEL = 0xFF0B1220;
    static final int SURFACE = 0xFF0D1422;
    static final int SURFACE_HI = 0xFF131C2C;
    static final int DEEP = 0xFF070A10;
    /** Glass: the night at 72%, over the light. */
    static final int GLASS = 0xB80A0F16;
    /** A well inside glass (inputs, pickers): darker, still see-through. */
    static final int WELL = 0x8C05070B;
    // hairlines: a cool white at low strength, so they read as an edge of light, not a line of paint
    static final int BORDER_SOFT = 0x14AADCFF;
    static final int BORDER = 0x1FAADCFF;
    static final int BORDER_STRONG = 0x38AADCFF;
    static final int NIGHT = 0xFF33405A;     // the night strip on the clocks
    // text
    static final int TEXT = 0xFFEEF4F8;
    static final int TEXT_SOFT = 0xFFC9D4DD;
    static final int MUTED = 0xFF8FA0B3;
    static final int DIM = 0xFF6F8091;
    // the key light
    static final int AMBER = 0xFFEB9438;
    static final int AMBER_MID = 0xFFE08A33;
    static final int AMBER_DEEP = 0xFFC97624;
    /** The key a quarter of the way to white: labels and lines drawn in the light. */
    static final int AMBER_LIGHT = 0xFFF0AE6A;
    /** The key almost white: the hot core of a lamp. */
    static final int LED = 0xFFFDF2E7;
    static final int KEY_GLASS = 0x24EB9438;
    static final int KEY_GLASS_HI = 0x3DEB9438;
    static final int KEY_EDGE = 0x99EB9438;
    static final int KEY_HALO = 0x1FEB9438;
    static final int GREEN = 0xFF34D399;

    private static Typeface uiMedium, uiSemi, uiBold, monoMedium, monoSemi, monoBold, futura;

    private static synchronized void init(Context c) {
        if (uiMedium != null) return;
        Context app = c.getApplicationContext();
        // The original Carbon+ font from the KWGT preset is used everywhere: regular for text and numbers,
        // Bold for titles and the big clock digits.
        Typeface regular = load(app, "fonts/carbonplus.otf", Typeface.create("sans-serif", Typeface.NORMAL));
        Typeface bold = load(app, "fonts/carbonplus-Bold.otf", Typeface.create("sans-serif", Typeface.BOLD));
        uiMedium = regular;
        uiSemi = regular;
        uiBold = bold;
        monoMedium = regular;
        monoSemi = regular;
        monoBold = bold;
    }

    private static Typeface load(Context c, String asset, Typeface fallback) {
        try {
            return Typeface.createFromAsset(c.getAssets(), asset);
        } catch (Exception e) {
            return fallback;
        }
    }

    /** The Calendar preset's own font (Futura Medium BT), bundled from the KWGT file. */
    static synchronized Typeface futura(Context c) {
        if (futura == null) futura = load(c.getApplicationContext(), "fonts/futura-medium-bt.ttf", Typeface.create("sans-serif-medium", Typeface.NORMAL));
        return futura;
    }

    static Typeface uiMedium(Context c) { init(c); return uiMedium; }
    static Typeface uiSemi(Context c) { init(c); return uiSemi; }
    static Typeface uiBold(Context c) { init(c); return uiBold; }
    static Typeface monoMedium(Context c) { init(c); return monoMedium; }
    static Typeface monoSemi(Context c) { init(c); return monoSemi; }
    static Typeface monoBold(Context c) { init(c); return monoBold; }

    // ---- drawable helpers for the settings screen ----

    static float dp(Context c, float v) { return v * c.getResources().getDisplayMetrics().density; }

    /** Rounded box with an optional 1dp border (stroke = 0 for none). */
    static GradientDrawable box(Context c, int fill, int stroke, float radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(c, radiusDp));
        if (stroke != 0) g.setStroke(Math.max(1, Math.round(dp(c, 1))), stroke);
        return g;
    }

    /** A glass panel: the night at 72% with a hairline edge. */
    static GradientDrawable glass(Context c, float radiusDp) {
        return box(c, GLASS, BORDER, radiusDp);
    }

    /** A well inside glass: inputs and pickers. The edge turns to the key when it's focused or pressed. */
    static GradientDrawable well(Context c, float radiusDp, boolean active) {
        return box(c, active ? KEY_GLASS : WELL, active ? KEY_EDGE : BORDER, radiusDp);
    }

    /**
     * Something that's on: a touch of the key light on the glass, a key edge, and a soft glow of the key just
     * outside it. The glow needs room, so the panel is drawn {@link Lit#INSET_DP} in from the view's edges.
     */
    static Drawable lit(Context c, float radiusDp, boolean pressed) {
        return new Lit(dp(c, radiusDp), dp(c, Lit.INSET_DP), dp(c, 1), pressed ? KEY_GLASS_HI : KEY_GLASS);
    }

    /** The lit look, drawn once into a picture whenever its size changes (a blur can't be drawn live everywhere). */
    static final class Lit extends Drawable {
        static final float INSET_DP = 3;
        private final float radius, inset, line;
        private final int fill;
        private Bitmap picture;

        Lit(float radius, float inset, float line, int fill) {
            this.radius = radius;
            this.inset = inset;
            this.line = line;
            this.fill = fill;
        }

        @Override
        protected void onBoundsChange(Rect b) {
            picture = null;
        }

        @Override
        public void draw(Canvas canvas) {
            Rect b = getBounds();
            if (b.width() <= 0 || b.height() <= 0) return;
            if (picture == null) {
                picture = Bitmap.createBitmap(b.width(), b.height(), Bitmap.Config.ARGB_8888);
                Canvas c = new Canvas(picture);
                RectF r = new RectF(inset, inset, b.width() - inset, b.height() - inset);
                Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
                glow.setStyle(Paint.Style.STROKE);
                glow.setStrokeWidth(inset * 0.9f);
                glow.setColor(0x73EB9438);
                glow.setMaskFilter(new BlurMaskFilter(inset * 0.9f, BlurMaskFilter.Blur.NORMAL));
                c.drawRoundRect(r, radius, radius, glow);
                Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
                p.setColor(BG);
                p.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
                c.drawRoundRect(r, radius, radius, p); // the glow is only outside; inside is glass
                p.setXfermode(null);
                p.setColor(fill);
                c.drawRoundRect(r, radius, radius, p);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(line);
                p.setColor(KEY_EDGE);
                float h = line / 2;
                c.drawRoundRect(new RectF(r.left + h, r.top + h, r.right - h, r.bottom - h), radius, radius, p);
            }
            canvas.drawBitmap(picture, b.left, b.top, null);
        }

        @Override public void setAlpha(int alpha) { }
        @Override public void setColorFilter(ColorFilter cf) { }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    /** Small label: bold capitals, spaced out, like markings on a piece of equipment. */
    static void label(Context c, TextView t) {
        t.setAllCaps(true);
        t.setTypeface(uiBold(c));
        t.setTextSize(11);
        t.setLetterSpacing(0.22f);
    }

    static StateListDrawable pressable(Drawable normal, Drawable pressed) {
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[]{android.R.attr.state_pressed}, pressed);
        s.addState(new int[]{}, normal);
        return s;
    }
}
