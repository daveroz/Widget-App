package app.currencyexchange.widget;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
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

    /**
     * A card on the settings screen, after the glowing card reference: near black on the left, warming into the song's
     * colour on the right, with a hairline edge that's light at the top left and fades round, and a faint sheen along
     * its top.
     */
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

    /** A card picture ({@link #card}), drawn once whenever its size changes, in the light it was made in. */
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
                float r = Math.min(radius, b.height() / 2f), opacity = ((fill >>> 24) & 0xFF) / 255f;
                RectF box = new RectF(0, 0, b.width(), b.height());
                Stage.fill(c, box, Stage.round(r), scene, opacity, px, 0f, null); // the same card as the widgets'
                c.restore();
                Stage.rim(c, box, Stage.round(r), scene, px);
            }
            canvas.drawBitmap(picture, b.left, b.top, null);
        }

        @Override public void setAlpha(int alpha) { }
        @Override public void setColorFilter(ColorFilter cf) { }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    /**
     * Something that's on, or the main action, after the Justice store's gold button (without its glow): solid, in a
     * deep shade of the song's colour, with a lighter edge of it, a hairline all round; white text. A small light strike
     * where its edge catches the light. The same size and corners as everything else, on or off.
     */
    static Drawable lit(Context c, float radiusDp, boolean pressed) {
        Scene s = accent();
        int fill = litFill(s);
        GradientDrawable g = new GradientDrawable();
        g.setColor(pressed ? Scene.mix(fill, 0xFFFFFFFF, 0.1f) : fill);
        g.setStroke(Math.max(1, Math.round(dp(c, 1))), Scene.alpha(Scene.mix(s.key, 0xFFD8D0C4, 0.5f), 0.6f));
        g.setCornerRadius(dp(c, radiusDp));
        if (pressed) return g;
        // where its edge catches the light, at the top left bend: a small light strike (the outline stays the button's)
        return new android.graphics.drawable.LayerDrawable(new Drawable[]{g, new Glint(dp(c, radiusDp), dp(c, 1), s.hot)});
    }

    /** A lit button's fill: the song's colour, deep (amber becomes the Justice button's dark gold). */
    static int litFill(Scene s) {
        return Scene.mix(s.key, 0xFF000000, 0.45f);
    }

    /** A small light strike ({@link Optics#star}) where a rounded button's top left bend catches the light. */
    private static final class Glint extends Drawable {
        private final float radius, px;
        private final int colour;

        Glint(float radius, float px, int colour) {
            this.radius = radius;
            this.px = px;
            this.colour = colour;
        }

        @Override
        public void draw(Canvas canvas) {
            Rect b = getBounds();
            if (b.width() <= 0 || b.height() <= 0) return;
            float r = Math.min(radius, b.height() / 2f), g = r * (1 - 0.7071f) + 2.5f * px; // just inside the bend
            float x = b.left + g, y = b.top + g;
            Optics.star(canvas, x, y, Math.min(5 * px, (x - b.left) / 1.6f), px, colour, 0.45f); // inside the button
        }

        @Override public void setAlpha(int alpha) { }
        @Override public void setColorFilter(ColorFilter cf) { }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    /** Text on a lit button ({@link #lit}): near white, as its fill is a deep shade of the colour. */
    static int onLit() {
        return 0xFFF6F1EA;
    }

    /**
     * Text or a symbol on something solid in {@code colour}: white on a deep colour (violet, crimson), near black on a
     * bright one (amber, teal, green), where white would barely read.
     */
    static int onLit(int colour) {
        return Scene.luminance(colour) > 0.3f ? 0xFF0B0A12 : 0xFFFFFFFF;
    }

    /**
     * A button that's off, or a quieter action, after the Justice store's dark button: near black (a breath of the song's
     * colour) with a grey hairline edge.
     */
    static Drawable dark(Context c, float radiusDp, boolean pressed) {
        GradientDrawable g = darkFill(c, pressed);
        g.setCornerRadius(dp(c, radiusDp));
        return g;
    }

    /** A round icon button in the app (the converter's swap): a dark button ({@link #dark}), round. */
    static Drawable iconButton(Context c, boolean pressed) {
        GradientDrawable g = darkFill(c, pressed);
        g.setShape(GradientDrawable.OVAL);
        return g;
    }

    private static GradientDrawable darkFill(Context c, boolean pressed) {
        Scene s = accent();
        int fill = Scene.mix(0xFF17191A, s.key, 0.04f);
        GradientDrawable g = new GradientDrawable();
        g.setColor(pressed ? Scene.mix(fill, 0xFFFFFFFF, 0.06f) : fill);
        g.setStroke(Math.max(1, Math.round(dp(c, 1))), Scene.mix(0xFF3B3E40, s.line, 0.12f));
        return g;
    }

    /** An icon button's dark fill: the song's colour, deep and dark. */
    static int iconFill(Scene s) {
        return Scene.mix(s.key, 0xFF0B0A12, 0.8f);
    }

    /** An icon button's glyph: the song's colour, pale. */
    static int iconGlyph(Scene s) {
        return Scene.mix(s.key, 0xFFFFFFFF, 0.72f);
    }

    /** A TextView with the subtle colour fringe all text has (see {@link Lens#text}), never a glow. */
    static class FringeText extends TextView {
        FringeText(Context c) {
            super(c);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            Lens.text(this, canvas);
            super.onDraw(canvas);
        }
    }

    /** An input with the subtle colour fringe all text has, as {@link FringeText}. */
    static class FringeEdit extends android.widget.EditText {
        FringeEdit(Context c) {
            super(c);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            Lens.text(this, canvas);
            super.onDraw(canvas);
        }
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
