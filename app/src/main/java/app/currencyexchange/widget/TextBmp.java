package app.currencyexchange.widget;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.util.LruCache;

/**
 * Text drawn as a small transparent picture. Launchers draw RemoteViews text with their own fonts, so this is the
 * only way to guarantee the original fonts (Carbon+). Every picture has the same line height for a given size, so
 * text from different strings scales consistently when an ImageView shrinks it (scaleType fit*).
 * `fill` = text line height / picture height; the rest is empty margin that keeps text from touching the box.
 */
final class TextBmp {
    private TextBmp() {}

    private static final LruCache<String, Bitmap> CACHE = new LruCache<>(300);

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
        Paint.FontMetrics fm = p.getFontMetrics();
        float line = fm.descent - fm.ascent;
        int h = Math.max(2, Math.round(line / fill));
        float total = 0;
        float[] w = new float[parts.length];
        for (int i = 0; i < parts.length; i++) { w[i] = p.measureText(parts[i]); total += w[i]; }
        float pad = shadow ? px * 0.15f : px * 0.08f;
        int bw = Math.max(2, (int) Math.ceil(total + 2 * pad));
        Bitmap b = Bitmap.createBitmap(bw, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        float base = (h - line) / 2f - fm.ascent;
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

    /**
     * As {@link #make} for one string, the letters blooming softly into the haze around them ({@code glow} strong, 0
     * for plain) in {@code light}, as in the haze references: a short reach, so the letters themselves stay sharp. The
     * picture is the same height as {@link #make}'s, so the text shows at the same size.
     */
    static Bitmap glowing(Typeface tf, float px, float fill, String s, int color, int light, float glow) {
        if (glow <= 0f) return make(tf, px, fill, false, false, s, color);
        if (s == null) s = "";
        String key = "G|" + System.identityHashCode(tf) + "|" + px + "|" + fill + "|" + color + "|" + light + "|" + glow + "|" + s;
        Bitmap hit = CACHE.get(key);
        if (hit != null) return hit;
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        p.setTypeface(tf);
        p.setTextSize(px);
        Paint.FontMetrics fm = p.getFontMetrics();
        float line = fm.descent - fm.ascent;
        int h = Math.max(2, Math.round(line / fill));
        float reach = px * 0.1f, pad = px * 0.08f + reach * 0.5f;
        int bw = Math.max(2, (int) Math.ceil(p.measureText(s) + 2 * pad));
        Bitmap b = Bitmap.createBitmap(bw, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        float base = (h - line) / 2f - fm.ascent;
        Glow.text(c, p, s, pad, base, color, light, glow, reach);
        p.setColor(color);
        c.drawText(s, pad, base, p);
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

    /** How wide {@code s} is with every digit {@code digit} wide. */
    private static float fixedWidth(Paint p, String s, float digit) {
        float w = 0f;
        for (int i = 0; i < s.length(); i++) {
            w += Character.isDigit(s.charAt(i)) ? digit : p.measureText(String.valueOf(s.charAt(i)));
        }
        return w;
    }
}
