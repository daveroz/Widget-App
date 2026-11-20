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
 * Worldly's visual language: OLED-black panels lit in the playing song's colour, with edges lit like light catching
 * them rather than solid borders, in the chosen {@link Look}. Structure (edges, rules, ticks, small labels) is in the
 * song's line colour ({@link Scene#line}); the key light (the song's colour, amber when nothing plays) is kept for what
 * matters right now: live values, today, and what's on, which is "lit" (a touch of the key on the black with a glowing
 * key edge). Small labels are spaced bold capitals. Type stays the original Carbon+ font from the KWGT preset. Used by
 * the settings screen, the converter and the widget renderer so they all feel like one family. Amber stays fixed
 * only where it means something: "needs attention" on the settings screen.
 */
final class Theme {
    private Theme() {}

    // surfaces (darkest to lightest)
    static final int BG = 0xFF020304;        // the night behind everything
    static final int HEADER = 0xFF070A10;
    static final int PANEL = 0xFF0B1220;
    static final int SURFACE = 0xFF0D1422;
    static final int SURFACE_HI = 0xFF131C2C;
    static final int DEEP = 0xFF020306;
    /** A card on the settings screen: nearly the panels' black, so the room behind barely shows. */
    static final int CARD = 0xF5010203;
    /** A well inside a card (inputs, pickers): a breath lighter than the card. */
    static final int WELL = 0xFF06080C;
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
    static final int GREEN = 0xFF34D399;

    /** The light the screens are drawn in: the playing song's colour, as on the widgets (amber when nothing plays). */
    static Scene accent() {
        return Ambience.scene();
    }

    /** A hairline in the song's line colour, {@code strength} strong (0 to 1). */
    static int line(float strength) {
        return Scene.alpha(accent().line, strength);
    }

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

    /** A card, as the widgets' panels are drawn: the black, the look's pool of the light, and its lit edge. */
    static Drawable card(Context c, float radiusDp) {
        return new Card(dp(c, radiusDp), dp(c, 1), CARD);
    }

    /** A card floating over anything (the converter): nearly opaque, so the numbers read over any wallpaper. */
    static Drawable card(Context c, float radiusDp, int fill) {
        return new Card(dp(c, radiusDp), dp(c, 1), fill);
    }

    /** A well inside a card: inputs and pickers. The edge turns to the key when it's focused or pressed. */
    static GradientDrawable well(Context c, float radiusDp, boolean active) {
        Scene s = accent();
        return box(c, active ? Scene.alpha(s.key, 0.14f) : WELL, active ? Scene.alpha(s.key, 0.6f) : line(0.22f), radiusDp);
    }

    /** A card picture, drawn once whenever its size changes, in the light and look it was made in. */
    static final class Card extends Drawable {
        private final float radius, px;
        private final int fill;
        private final Scene scene = accent();
        private Bitmap picture;

        Card(float radius, float px, int fill) {
            this.radius = radius;
            this.px = px;
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
                RectF box = new RectF(0, 0, b.width(), b.height());
                float[] radii = Stage.round(Math.min(radius, b.height() / 2f));
                Stage.night(c, box, radii, scene, fill, 0f);
                c.restore();
                Stage.rim(c, box, radii, scene, px);
            }
            canvas.drawBitmap(picture, b.left, b.top, null);
        }

        @Override public void setAlpha(int alpha) { }
        @Override public void setColorFilter(ColorFilter cf) { }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    /**
     * Something that's on, lit like a lamp: a touch of the key light on the glass, the light bleeding into the glass
     * along its edge, a bright key edge, and outside it a wide, soft bloom with a tight halo hugging the edge. The
     * glow needs room, so the panel is drawn {@link Lit#INSET_DP} in from the view's edges.
     */
    static Drawable lit(Context c, float radiusDp, boolean pressed) {
        return new Lit(dp(c, radiusDp), dp(c, Lit.INSET_DP), dp(c, 1), accent(), pressed ? 0.24f : 0.14f);
    }

    /** The lit look, drawn once into a picture whenever its size changes (a blur can't be drawn live everywhere). */
    static final class Lit extends Drawable {
        static final float INSET_DP = 3;
        private final float radius, inset, line;
        private final Scene scene;
        private final int fill;
        private Bitmap picture;

        Lit(float radius, float inset, float line, Scene scene, float fill) {
            this.radius = radius;
            this.inset = inset;
            this.line = line;
            this.scene = scene;
            this.fill = Scene.alpha(scene.key, fill);
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
                glow.setStrokeWidth(inset);
                float bloom = Look.get().bloom;
                glow.setColor(Scene.alpha(scene.deep, 0.55f * bloom));
                glow.setMaskFilter(new BlurMaskFilter(inset * 0.9f, BlurMaskFilter.Blur.NORMAL));
                c.drawRoundRect(r, radius, radius, glow);
                glow.setStrokeWidth(line * 1.5f);
                glow.setColor(Scene.alpha(scene.label, 0.6f * bloom));
                glow.setMaskFilter(new BlurMaskFilter(line * 1.5f, BlurMaskFilter.Blur.NORMAL));
                c.drawRoundRect(r, radius, radius, glow);
                Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
                p.setColor(BG);
                p.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
                c.drawRoundRect(r, radius, radius, p); // the glow is only outside; inside is the black
                p.setXfermode(null);
                p.setColor(Stage.NIGHT);
                c.drawRoundRect(r, radius, radius, p);
                p.setColor(fill);
                c.drawRoundRect(r, radius, radius, p);
                // the light bleeding into the glass along its edge
                p.setColor(Scene.alpha(scene.key, 0.22f));
                p.setMaskFilter(new BlurMaskFilter(inset * 1.1f, BlurMaskFilter.Blur.INNER));
                c.drawRoundRect(r, radius, radius, p);
                p.setMaskFilter(null);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(line);
                p.setColor(Scene.alpha(scene.label, 0.8f));
                float h = line / 2;
                c.drawRoundRect(new RectF(r.left + h, r.top + h, r.right - h, r.bottom - h), radius, radius, p);
                picture = Lens.fringe(picture, line, 0.8f); // the lens's colour fringe on a lit thing
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
