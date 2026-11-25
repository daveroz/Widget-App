package app.currencyexchange.widget;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.util.LruCache;

/**
 * Text drawn as a small transparent picture. Launchers draw RemoteViews text with their own fonts, so this is the
 * only way to guarantee the app's own font (Futura). Every picture has the same line height for a given size, so
 * text from different strings scales consistently when an ImageView shrinks it (scaleType fit*).
 * `fill` = text line height / picture height; the rest is empty margin that keeps text from touching the box.
 */
final class TextBmp {
    private TextBmp() {}

    private static final LruCache<String, Bitmap> CACHE = new LruCache<>(300);

    /**
     * Text is laid out by its capital height, as Carbon+ (the font before Futura) laid it out: a line 1.638 capitals
     * tall with the baseline 1.207 capitals down. Every view was sized for that, so text shows at the same size in
     * Futura, whose own line is much taller for its capitals.
     */
    private static final float LINE_PER_CAP = 950f / 580f, ASC_PER_CAP = 700f / 580f;

    /** How tall {@code p}'s capitals are, in pixels. */
    private static float cap(Paint p) {
        android.graphics.Rect r = new android.graphics.Rect();
        p.getTextBounds("H", 0, 1, r);
        return -r.top;
    }

    static Bitmap make(Typeface tf, float px, float fill, boolean italic, boolean shadow, String[] parts, int[] colors) {
        StringBuilder k = new StringBuilder();
        k.append(System.identityHashCode(tf)).append('|').append(px).append('|').append(fill).append('|')
                .append(italic).append(shadow);
        for (int i = 0; i < parts.length; i++) k.append('|').append(colors[i]).append(':').append(parts[i]);
        String key = k.toString();
        Bitmap hit = CACHE.get(key);
        if (hit != null) return hit;

        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        p.setTypeface(tf);
        p.setTextSize(px);
        if (italic) p.setTextSkewX(-0.2f);
        float capH = cap(p);
        float line = capH * LINE_PER_CAP;
        int h = Math.max(2, Math.round(line / fill));
        float total = 0;
        float[] w = new float[parts.length];
        for (int i = 0; i < parts.length; i++) { w[i] = p.measureText(parts[i]); total += w[i]; }
        float pad = shadow ? px * 0.15f : px * 0.08f;
        int bw = Math.max(2, (int) Math.ceil(total + 2 * pad));
        Bitmap b = Bitmap.createBitmap(bw, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        float base = (h - line) / 2f + capH * ASC_PER_CAP;
        float x = pad;
        for (int i = 0; i < parts.length; i++) {
            p.setColor(colors[i]);
            if (shadow) p.setShadowLayer(px * 0.08f, 0, px * 0.04f, 0x99000000);
            c.drawText(parts[i], x, base, p);
            x += w[i];
        }
        CACHE.put(key, b);
        return b;
    }

    /**
     * Tight text picture: from the top of the capitals to just below the baseline (room for a comma). Height is
     * 0.82 x the font size, so an ImageView 0.82 x sizeDp tall shows it at exactly sizeDp.
     */
    static Bitmap tight(Typeface tf, float sizeDp, String s, int color) {
        String key = "T|" + System.identityHashCode(tf) + "|" + sizeDp + "|" + color + "|" + s;
        Bitmap hit = CACHE.get(key);
        if (hit != null) return hit;
        float px = sizeDp * 2.5f;
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        p.setTypeface(tf);
        p.setTextSize(px);
        p.setColor(color);
        android.graphics.Rect cap = new android.graphics.Rect();
        p.getTextBounds("H0", 0, 2, cap);
        float top = -cap.top;              // cap height above the baseline
        float below = px * 0.2f;           // comma room below the baseline
        float pad = px * 0.04f;
        int h = Math.max(2, (int) Math.ceil(top + below + pad));
        int w = Math.max(2, (int) Math.ceil(p.measureText(s == null ? "" : s) + 2 * pad));
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        new Canvas(b).drawText(s == null ? "" : s, pad, top + pad, p);
        CACHE.put(key, b);
        return b;
    }

    static Bitmap make(Typeface tf, float px, float fill, boolean italic, boolean shadow, String s, int color) {
        return make(tf, px, fill, italic, shadow, new String[]{s == null ? "" : s}, new int[]{color});
    }

    /** How much taller a fringed tight picture is than {@link #tight}'s, for room round it: its views are as much taller. */
    static final float ROOM = 1.3f;

    /**
     * As {@link #make} for one string, with the subtle colour fringe text has (a faint red edge on one side of the
     * letters, a blue one on the other; see {@link Lens#textFringe}). No glow. The picture is the same height as
     * {@link #make}'s, so the text shows at the same size; it's a little wider, for the fringe. 2.5 pixels per dp.
     */
    static Bitmap fringed(Typeface tf, float px, float fill, boolean italic, String s, int color) {
        if (s == null) s = "";
        String key = "G|" + Look.get().tag() + System.identityHashCode(tf) + "|" + px + "|" + fill + "|" + italic + "|" + color + "|" + s;
        Bitmap hit = CACHE.get(key);
        if (hit != null) return hit;
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        p.setTypeface(tf);
        p.setTextSize(px);
        if (italic) p.setTextSkewX(-0.2f);
        float capH = cap(p);
        float line = capH * LINE_PER_CAP, reach = px * 0.12f; // room for the fringe each side
        int h = Math.max(2, Math.round(line / fill));
        float pad = px * 0.08f + reach;
        int bw = Math.max(2, (int) Math.ceil(p.measureText(s) + 2 * pad));
        Bitmap b = Bitmap.createBitmap(bw, h, Bitmap.Config.ARGB_8888);
        p.setColor(color);
        new Canvas(b).drawText(s, pad, (h - line) / 2f + capH * ASC_PER_CAP, p);
        b = Lens.textFringe(b, 2.5f);
        CACHE.put(key, b);
        return b;
    }

    static Bitmap fringed(Typeface tf, float px, float fill, String s, int color) {
        return fringed(tf, px, fill, false, s, color);
    }

    /**
     * As {@link #tight}, with text's fringe and room round the letters: the picture is {@link #ROOM} times as tall as
     * {@link #tight}'s, the text in the same place in its middle, so a view that much taller shows it at the same size.
     */
    static Bitmap tightFringed(Typeface tf, float sizeDp, String s, int color) {
        return tightFixedFringed(tf, sizeDp, s, null, color, false);
    }

    /** As {@link #tightFixed} (a number that ticks, its digits all one width), fringed like {@link #tightFringed}. */
    static Bitmap tightFixedFringed(Typeface tf, float sizeDp, String s, String template, int color, boolean right) {
        if (s == null) s = "";
        String key = "H|" + Look.get().tag() + System.identityHashCode(tf) + "|" + sizeDp + "|" + color + "|" + right + "|" + template + "|" + s;
        Bitmap hit = CACHE.get(key);
        if (hit != null) return hit;
        float px = sizeDp * 2.5f;
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        p.setTypeface(tf);
        p.setTextSize(px);
        p.setColor(color);
        android.graphics.Rect cap = new android.graphics.Rect();
        p.getTextBounds("H0", 0, 2, cap);
        float top = -cap.top, below = px * 0.2f, pad = px * 0.04f; // as tight(): the same height and placement
        int h0 = Math.max(2, (int) Math.ceil(top + below + pad));
        int h = Math.round(h0 * ROOM);
        float room = (h - h0) / 2f;
        float digit = 0f;
        for (char d = '0'; d <= '9'; d++) digit = Math.max(digit, p.measureText(String.valueOf(d)));
        boolean fixed = template != null;
        float wide = fixed ? Math.max(fixedWidth(p, template, digit), fixedWidth(p, s, digit)) : p.measureText(s);
        int w = Math.max(2, (int) Math.ceil(wide + 2 * (pad + room)));
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        float base = room + top + pad;
        float x = pad + room;
        if (!fixed) {
            c.drawText(s, x, base, p);
        } else {
            x += right ? wide - fixedWidth(p, s, digit) : 0f;
            for (int i = 0; i < s.length(); i++) {
                String ch = String.valueOf(s.charAt(i));
                float cw = p.measureText(ch);
                if (Character.isDigit(s.charAt(i))) {
                    c.drawText(ch, x + (digit - cw) / 2f, base, p);
                    x += digit;
                } else {
                    c.drawText(ch, x, base, p);
                    x += cw;
                }
            }
        }
        b = Lens.textFringe(b, 2.5f);
        CACHE.put(key, b);
        return b;
    }

    /**
     * A tight text picture ({@link #tight}) for a number that ticks, whose size never changes with its digits: every
     * digit takes the width of the widest one, and the picture is as wide as {@code template} (the longest the
     * number can be, its digits as zeros, like "00:00"), the text at its right edge when {@code right}, else its left.
     * So whatever sits beside it (the Music widget's progress line) stays put as the number changes.
     */
    static Bitmap tightFixed(Typeface tf, float sizeDp, String s, String template, int color, boolean right) {
        if (s == null) s = "";
        String key = "F|" + System.identityHashCode(tf) + "|" + sizeDp + "|" + color + "|" + right + "|" + template + "|" + s;
        Bitmap hit = CACHE.get(key);
        if (hit != null) return hit;
        float px = sizeDp * 2.5f;
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        p.setTypeface(tf);
        p.setTextSize(px);
        p.setColor(color);
        android.graphics.Rect cap = new android.graphics.Rect();
        p.getTextBounds("H0", 0, 2, cap);
        float top = -cap.top, below = px * 0.2f, pad = px * 0.04f; // as tight(): the same height and placement
        float digit = 0f;
        for (char d = '0'; d <= '9'; d++) digit = Math.max(digit, p.measureText(String.valueOf(d)));
        float wide = Math.max(fixedWidth(p, template, digit), fixedWidth(p, s, digit));
        int h = Math.max(2, (int) Math.ceil(top + below + pad));
        int w = Math.max(2, (int) Math.ceil(wide + 2 * pad));
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        float x = pad + (right ? wide - fixedWidth(p, s, digit) : 0f);
        for (int i = 0; i < s.length(); i++) {
            String ch = String.valueOf(s.charAt(i));
            float cw = p.measureText(ch);
            if (Character.isDigit(s.charAt(i))) {
                c.drawText(ch, x + (digit - cw) / 2f, top + pad, p); // centred in a digit-wide cell
                x += digit;
            } else {
                c.drawText(ch, x, top + pad, p);
                x += cw;
            }
        }
        CACHE.put(key, b);
        return b;
    }

    /**
     * Text cropped exactly to its ink, top to bottom (the Calendar's big day number, sized to span the lines beside
     * it): the picture's top is the top of the letters and its bottom their bottom, so the view's height is theirs.
     */
    static Bitmap ink(Typeface tf, float px, String s, int color) {
        if (s == null || s.isEmpty()) s = " ";
        String key = "I|" + System.identityHashCode(tf) + "|" + px + "|" + color + "|" + s;
        Bitmap hit = CACHE.get(key);
        if (hit != null) return hit;
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        p.setTypeface(tf);
        p.setTextSize(px);
        p.setColor(color);
        android.graphics.Rect r = new android.graphics.Rect();
        p.getTextBounds(s, 0, s.length(), r);
        Bitmap b = Bitmap.createBitmap(Math.max(2, r.width() + 2), Math.max(2, r.height()), Bitmap.Config.ARGB_8888);
        new Canvas(b).drawText(s, 1 - r.left, -r.top, p);
        CACHE.put(key, b);
        return b;
    }

    /**
     * As {@link #ink}, in polished silver (2.23), as the app's big figures ({@link Theme.Chrome}): white at the top of
     * the figures turning to a cool silver at their foot.
     */
    static Bitmap chrome(Typeface tf, float px, String s) {
        if (s == null || s.isEmpty()) s = " ";
        String key = "C|" + System.identityHashCode(tf) + "|" + px + "|" + s;
        Bitmap hit = CACHE.get(key);
        if (hit != null) return hit;
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        p.setTypeface(tf);
        p.setTextSize(px);
        android.graphics.Rect r = new android.graphics.Rect();
        p.getTextBounds(s, 0, s.length(), r);
        int h = Math.max(2, r.height());
        p.setShader(new android.graphics.LinearGradient(0, 0, 0, h, new int[]{0xFFFFFFFF, 0xFFE6EAF0, 0xFFA3ACB8},
                new float[]{0f, 0.42f, 1f}, android.graphics.Shader.TileMode.CLAMP));
        Bitmap b = Bitmap.createBitmap(Math.max(2, r.width() + 2), h, Bitmap.Config.ARGB_8888);
        new Canvas(b).drawText(s, 1 - r.left, -r.top, p);
        CACHE.put(key, b);
        return b;
    }

    /** How wide {@code s} is with every digit {@code digit} wide. */
    private static float fixedWidth(Paint p, String s, float digit) {
        float w = 0f;
        for (int i = 0; i < s.length(); i++) {
            w += Character.isDigit(s.charAt(i)) ? digit : p.measureText(String.valueOf(s.charAt(i)));
        }
        return w;
    }
}
