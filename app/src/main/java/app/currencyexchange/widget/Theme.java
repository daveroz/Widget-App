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

    private static Typeface uiMedium, uiSemi, uiBold, monoMedium, monoSemi, monoBold;

    /**
     * How large text is drawn for a given size. Every size was tuned for Carbon+, the font before Futura (1.85), whose
     * capitals are 0.58 of its size; Futura's are 0.754, so sizes are scaled by this to keep the same capital height.
     */
    static final float FONT_SCALE = 0.58f / 0.754f;

    private static synchronized void init(Context c) {
        if (uiMedium != null) return;
        Context app = c.getApplicationContext();
        // Futura 100 everywhere: Text Medium (cut for small sizes) for text and numbers, Bold for titles and the big
        // clock digits.
        Typeface regular = load(app, "fonts/futura100-text-medium.otf", Typeface.create("sans-serif-medium", Typeface.NORMAL));
        Typeface bold = load(app, "fonts/futura100-bold.otf", Typeface.create("sans-serif", Typeface.BOLD));
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
                Stage.rim(c, box, Stage.round(r), px);
            }
            canvas.drawBitmap(picture, b.left, b.top, null);
        }

        @Override public void setAlpha(int alpha) { }
        @Override public void setColorFilter(ColorFilter cf) { }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    /**
     * Something that's on, or the main action, after the Justice store's gold button (without its glow): solid, in a
     * deep shade of the song's colour, with a glass rim in a light shade of it ({@link #glassEdge}); white text. The
     * same size and corners as everything else, on or off.
     */
    static Drawable lit(Context c, float radiusDp, boolean pressed) {
        Scene s = accent();
        int fill = litFill(s);
        return new GlassButton(pressed ? Scene.mix(fill, 0xFFFFFFFF, 0.1f) : fill, dp(c, radiusDp), dp(c, 1.2f),
                litEdge(s), LIT_RIM, false);
    }

    /** A lit button's rim colour: a light shade of the song's colour. */
    static int litEdge(Scene s) {
        return Scene.mix(s.key, 0xFFFFF4E6, 0.55f);
    }

    /** A dark button's fill: near black, with a breath of the song's colour. */
    static int darkFill(Scene s) {
        return Scene.mix(0xFF17191A, s.key, 0.04f);
    }

    /** A dark button's rim colour: a warm white, a touch of the song's line colour in it. */
    static int darkEdge(Scene s) {
        return Scene.mix(0xFFF3ECE8, s.line, 0.15f);
    }

    /**
     * A round button drawn straight onto a canvas (the widgets' buttons): a solid {@code fill} at (cx, cy), radius
     * {@code r}, with a glass rim {@code stroke} wide ({@link #glassEdge}) in {@code edge}, {@code rim} strong.
     */
    static void glassCircle(Canvas c, float cx, float cy, float r, float stroke, int fill, int edge, float[] rim) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        p.setColor(fill);
        c.drawCircle(cx, cy, r, p);
        float in = stroke / 2;
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(stroke);
        p.setShader(glassEdge(new RectF(cx - r, cy - r, cx + r, cy + r), edge, rim));
        c.drawCircle(cx, cy, r - in, p);
    }

    /**
     * How strong a glass rim is along its length ({@link #glassEdge}): a dark button's, measured off the Justice store's
     * "Shop all" button (white over its near black), and a lit button's, fuller.
     */
    static final float[] DARK_RIM = {0.27f, 0.2f, 0.06f, 0.03f, 0.06f, 0.13f, 0.15f};
    static final float[] LIT_RIM = {0.8f, 0.66f, 0.36f, 0.28f, 0.34f, 0.5f, 0.56f};
    /** Where along the rim each strength is, from its top left (0) round to its bottom right (1). */
    private static final float[] RIM_AT = {0f, 0.2f, 0.35f, 0.5f, 0.65f, 0.8f, 1f};

    /**
     * A glass rim's colour along its length, after the Justice store's buttons: not a flat border but light caught by the
     * glass's edge, brightest at the top left, fading almost to nothing across the top middle and the bottom left, and
     * coming back, softer, at the bottom right where the light reflects inside the glass. A gradient at 45° across
     * {@code box} (the way CSS draws "135deg"), in {@code colour}, {@code alphas} strong at {@link #RIM_AT}.
     */
    static android.graphics.Shader glassEdge(RectF box, int colour, float[] alphas) {
        float cx = box.centerX(), cy = box.centerY(), q = (box.width() + box.height()) / 4f;
        int[] colours = new int[alphas.length];
        for (int i = 0; i < alphas.length; i++) colours[i] = Scene.alpha(colour, alphas[i]);
        return new android.graphics.LinearGradient(cx - q, cy - q, cx + q, cy + q, colours, RIM_AT,
                android.graphics.Shader.TileMode.CLAMP);
    }

    /** A button: a solid fill in its rounded shape (or round), with a glass rim ({@link #glassEdge}). */
    private static final class GlassButton extends Drawable {
        private final int fill, edge;
        private final float radius, stroke;
        private final float[] rim;
        private final boolean round;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        private final RectF box = new RectF();

        GlassButton(int fill, float radius, float stroke, int edge, float[] rim, boolean round) {
            this.fill = fill;
            this.radius = radius;
            this.stroke = Math.max(1f, stroke);
            this.edge = edge;
            this.rim = rim;
            this.round = round;
        }

        @Override
        public void draw(Canvas canvas) {
            Rect b = getBounds();
            if (b.width() <= 0 || b.height() <= 0) return;
            box.set(b);
            float r = round ? Math.min(b.width(), b.height()) / 2f : Math.min(radius, b.height() / 2f);
            paint.setStyle(Paint.Style.FILL);
            paint.setShader(null);
            paint.setColor(fill);
            canvas.drawRoundRect(box, r, r, paint);
            float in = stroke / 2;
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(stroke);
            paint.setShader(glassEdge(box, edge, rim));
            box.inset(in, in);
            canvas.drawRoundRect(box, r - in, r - in, paint);
            paint.setShader(null);
        }

        @Override
        public void getOutline(android.graphics.Outline outline) {
            Rect b = getBounds();
            float r = round ? Math.min(b.width(), b.height()) / 2f : Math.min(radius, b.height() / 2f);
            outline.setRoundRect(b, r);
        }

        @Override public void setAlpha(int alpha) { }
        @Override public void setColorFilter(ColorFilter cf) { }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    /** A lit button's fill: the song's colour, deep (amber becomes the Justice button's dark gold). */
    static int litFill(Scene s) {
        return Scene.mix(s.key, 0xFF000000, 0.45f);
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
     * A button that's off, or a quieter action, after the Justice store's dark buttons: near black (a breath of the
     * song's colour) with a glass rim ({@link #glassEdge}) in a warm white.
     */
    static Drawable dark(Context c, float radiusDp, boolean pressed) {
        return darkButton(c, radiusDp, pressed, false);
    }

    /** A round icon button in the app (the converter's swap): a dark button ({@link #dark}), round. */
    static Drawable iconButton(Context c, boolean pressed) {
        return darkButton(c, 0, pressed, true);
    }

    private static Drawable darkButton(Context c, float radiusDp, boolean pressed, boolean round) {
        Scene s = accent();
        int fill = darkFill(s);
        return new GlassButton(pressed ? Scene.mix(fill, 0xFFFFFFFF, 0.06f) : fill, dp(c, radiusDp), dp(c, 1.2f),
                darkEdge(s), DARK_RIM, round);
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
        /** Sizes as tuned for Carbon+, drawn at the same capital height in Futura ({@link #FONT_SCALE}). */
        @Override
        public void setTextSize(int unit, float size) {
            super.setTextSize(unit, size * FONT_SCALE);
        }

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
        @Override
        public void setTextSize(int unit, float size) {
            super.setTextSize(unit, size * FONT_SCALE);
        }

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
