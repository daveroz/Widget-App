package app.currencyexchange.widget;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;

/**
 * Visual language shared with the SAB Monitor app (daverodd/SAB-App): graphite surfaces, thin slate
 * borders and one amber accent. Type stays the original Carbon+ font from the KWGT preset.
 * Used by both the settings screen and the widget renderer so the two apps feel like one family.
 */
final class Theme {
    private Theme() {}

    // surfaces (darkest to lightest)
    static final int BG = 0xFF0B1322;        // app background
    static final int HEADER = 0xFF0E111A;
    static final int PANEL = 0xFF11141E;
    static final int SURFACE = 0xFF151922;
    static final int SURFACE_HI = 0xFF1C2230;
    static final int DEEP = 0xFF0B0E15;
    // borders
    static final int BORDER_SOFT = 0xFF1F2636;
    static final int BORDER = 0xFF252C3C;
    static final int NIGHT = 0xFF3A4560;     // grid-line slate, used for the night strip
    // text
    static final int TEXT = 0xFFF1F5F9;
    static final int TEXT_SOFT = 0xFFCBD5E1;
    static final int MUTED = 0xFF8A9BB2;
    static final int DIM = 0xFF718096;
    // accent + status
    static final int AMBER = 0xFFDF8B35;
    static final int AMBER_MID = 0xFFD6802A;
    static final int AMBER_DEEP = 0xFFC57220;
    static final int AMBER_LIGHT = 0xFFF0A04B;
    static final int GREEN = 0xFF16A34A;

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

    /** SAB's primary-button look: orange gradient. */
    static GradientDrawable amberGradient(Context c, float radiusDp, boolean pressed) {
        int[] colors = pressed
                ? new int[]{AMBER_DEEP, 0xFFB5671C}
                : new int[]{AMBER, AMBER_MID, AMBER_DEEP};
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, colors);
        g.setCornerRadius(dp(c, radiusDp));
        return g;
    }

    static StateListDrawable pressable(Drawable normal, Drawable pressed) {
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[]{android.R.attr.state_pressed}, pressed);
        s.addState(new int[]{}, normal);
        return s;
    }
}
