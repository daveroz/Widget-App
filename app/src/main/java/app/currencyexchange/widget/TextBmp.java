package app.currencyexchange.widget;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Shader;
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
        return make(tf, px, fill, italic, shadow, parts, colors, 0, 0f);
    }

    /**
     * Text lit like a light source (see {@link Glow}): {@code color} letters glowing in {@code light}, {@code strength}
     * strong (0: no glow). The glow needs a little room, so the picture is a touch wider than an unlit one; where it
     * still reaches the picture's edge it fades out softly rather than being cut.
     */
    static Bitmap lit(Typeface tf, float px, float fill, boolean italic, String s, int color, int light, float strength) {
        return make(tf, px, fill, italic, false, new String[]{s == null ? "" : s}, new int[]{color}, light, strength);
    }

    /** The glow's reach (its wide bloom), as a share of the font size, and the room left for it beside the text. */
    private static final float GLOW_REACH = 0.3f, GLOW_PAD = 0.14f;

    private static Bitmap make(Typeface tf, float px, float fill, boolean italic, boolean shadow, String[] parts,
                               int[] colors, int light, float strength) {
        boolean glow = strength > 0f;
        StringBuilder k = new StringBuilder();
        k.append(System.identityHashCode(tf)).append('|').append(px).append('|').append(fill).append('|')
                .append(italic).append(shadow);
        if (glow) k.append("|g").append(light).append(':').append(strength);
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
        float pad = glow ? px * GLOW_PAD : shadow ? px * 0.15f : px * 0.08f;
        int bw = Math.max(2, (int) Math.ceil(total + 2 * pad));
        Bitmap b = Bitmap.createBitmap(bw, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        float base = (h - line) / 2f - fm.ascent;
        if (glow) {
            // The glow on its own layer, faded out towards the picture's edges so it never ends in a hard line.
            Bitmap halo = Bitmap.createBitmap(bw, h, Bitmap.Config.ARGB_8888);
            Canvas hc = new Canvas(halo);
            float x = pad;
            for (int i = 0; i < parts.length; i++) {
                Glow.text(hc, p, parts[i], x, base, colors[i], light, strength, px * GLOW_REACH);
                x += w[i];
            }
            fadeEdges(hc, bw, h, pad);
            c.drawBitmap(halo, 0, 0, null);
            halo.recycle();
        }
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

    /** Fades what's on {@code c} out to nothing over {@code edge} pixels at the left and right, and a little at the top and bottom. */
    private static void fadeEdges(Canvas c, int w, int h, float edge) {
        Paint m = new Paint();
        m.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
        float e = Math.max(1f, Math.min(edge, w / 2f)) / w;
        m.setShader(new LinearGradient(0, 0, w, 0, new int[]{0, 0xFF000000, 0xFF000000, 0},
                new float[]{0f, e, 1f - e, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, m);
        m.setShader(new LinearGradient(0, 0, 0, h, new int[]{0, 0xFF000000, 0xFF000000, 0},
                new float[]{0f, 0.12f, 0.88f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, m);
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
}
