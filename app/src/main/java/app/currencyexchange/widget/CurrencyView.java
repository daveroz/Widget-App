package app.currencyexchange.widget;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.widget.RemoteViews;

import org.json.JSONObject;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * The Rates widget as native views (res/layout/widget_k*.xml): the currency rows on one card, thin lines between them
 * (until 2.26 under the world clocks, which have a widget of their own since: {@link ClockView}). Rows, columns and
 * text boxes are weighted, so the widget follows any width and height the launcher gives it. Only the flags, the Visa
 * logo and the card are small bitmaps (rounded corners baked in).
 */
final class CurrencyView {
    private CurrencyView() {}

    private static int id(Context c, String name) {
        return c.getResources().getIdentifier(name, "id", c.getPackageName());
    }

    /**
     * Text as a picture at a fixed size (sizeDp = font size); the ImageView height in the layout is sizeDp * 1.46.
     * In its own colour, with the lens's colour fringe (no glow).
     */
    private static void txt(Context ctx, RemoteViews rv, int viewId, String s, Typeface tf, int color, float sizeDp) {
        rv.setImageViewBitmap(viewId, TextBmp.fringed(tf, sizeDp * 2.5f, 0.8f, s, color));
    }

    /** Tight text (see {@link TextBmp#tightFringed}), fringed like {@link #txt}: its view is {@link TextBmp#ROOM} times as tall. */
    private static Bitmap tight(Typeface tf, float sizeDp, String s, int color) {
        return TextBmp.tightFringed(tf, sizeDp, s, color);
    }

    static final int[] LAYOUTS = {R.layout.widget_k1, R.layout.widget_k2, R.layout.widget_k3,
            R.layout.widget_k4, R.layout.widget_k5, R.layout.widget_k6};

    /**
     * Widget height (dp) from which the variant with k rows fits: the rates card (its 4 dp paddings, k rows of 54 and
     * the k - 1 lines between them).
     */
    static int heightFor(int k) { return 7 + 55 * k; }

    private static final android.util.LruCache<String, Bitmap> PICS = new android.util.LruCache<>(60);

    /** Variant with k currency rows (1..6). */
    private static void txtF(RemoteViews rv, int viewId, String s, Typeface tf, int color, float sizeDp, float fill) {
        rv.setImageViewBitmap(viewId, TextBmp.fringed(tf, sizeDp * 2.5f, fill, s, color));
    }

    /** The size the clock city names actually show at (11 drawn into a 16 dp box with Carbon+'s 0.95 em line). */
    private static final float CITY_SP = 16f * 0.8f / 0.95f * 0.985f;

    /** The size the rates show at: 12 drawn into an 18 dp box (as the weather bar's numbers were). */
    private static final float HEADER_SP = 12f / 0.82f;

    /** Height of a currency row in the layouts (dp). */
    private static final int ROW_DP = 54;

    /** @param widthDp the widget's width as the launcher reports it (0 if unknown); sizes the cards' pictures */
    static RemoteViews build(Context ctx, int k, int widthDp) {
        Typeface reg = Theme.uiMedium(ctx);
        RemoteViews rv = new RemoteViews(ctx.getPackageName(), LAYOUTS[k - 1]);
        // The light all the widgets share: the playing song's colour, or amber.
        Scene scene = Ambience.scene();
        // The text is plain, in its own colours (no glow, no tint).
        int white = Theme.TEXT, soft = Theme.TEXT_SOFT, muted = Theme.MUTED;
        int rowW = widthDp >= 120 && widthDp <= 1200 ? widthDp : 360;

        // currency rows
        String to = Store.to(ctx).trim().toUpperCase(Locale.ROOT);
        Bitmap visaLogo = visaLogo(ctx);
        Bitmap marketLbl = marketLabel(reg, Theme.MUTED); // labels in the app's neutral grey (2.23)
        Bitmap toFlag = flagOrLabel(ctx, Util.countryOfCurrency(to), to);
        Bitmap arrow = arrow(scene);
        JSONObject hist = Store.hist(ctx, to); // each day's market rates, for each row's mood (2.33)
        // their one card (its 4 dp paddings, the rows and the lines between them), with its edge
        rv.setImageViewBitmap(R.id.rates_bg, Stage.panel(scene, rowW, 8 + ROW_DP * k + (k - 1), 1.5f, Stage.round(28), null, true));
        for (int i = 0; i < k; i++) {
            String from = Store.from(ctx, i).trim().toUpperCase(Locale.ROOT);
            rv.setOnClickPendingIntent(id(ctx, "row_" + i), ConverterActivity.intentFor(ctx, i)); // tap a row: converter
            rv.setImageViewBitmap(id(ctx, "fl_a_" + i), flagOrLabel(ctx, Util.countryOfCurrency(from), from));
            txt(ctx, rv, id(ctx, "code_a_" + i), from, reg, white, 15);
            rv.setImageViewBitmap(id(ctx, "fl_b_" + i), toFlag);
            txt(ctx, rv, id(ctx, "code_b_" + i), to, reg, white, 15);
            double market = Store.rate(ctx, from, to);
            rv.setImageViewBitmap(id(ctx, "arrow_" + i), arrow);
            rv.setImageViewBitmap(id(ctx, "mkt_lbl_" + i), marketLbl);
            rv.setImageViewBitmap(id(ctx, "mkt_val_" + i), tight(reg, HEADER_SP, Util.fmt0(market), white));
            int mv = id(ctx, "mood_" + i);
            if (mv != 0) rv.setImageViewBitmap(mv, mood(ctx, mood(hist, from, market) <= 0)); // happy when the rate fell or held (2.40)
            rv.setImageViewBitmap(id(ctx, "visa_logo_" + i), visaLogo);
            double visa = Util.alignToMarket(Store.visa(ctx, from, to), market);
            int vv = id(ctx, "visa_val_" + i);
            if (!Double.isNaN(visa)) {
                rv.setImageViewBitmap(vv, tight(reg, HEADER_SP, Util.fmt0(visa), soft));
            } else if (!Double.isNaN(market)) {
                // Visa did not answer: estimate = market + fee, marked "~" and muted
                rv.setImageViewBitmap(vv, tight(reg, HEADER_SP, "~" + Util.fmt0(market * (1.0 + Store.fee(ctx) / 100.0)), muted));
            } else {
                rv.setImageViewBitmap(vv, tight(reg, HEADER_SP, "", muted));
            }
        }
        return rv;
    }

    // ------------------------------------------------------------------ the market's mood (2.33)

    /**
     * How the market rate now compares with the last day before today in the history: 1 up, -1 down, 0 the same (as
     * shown, to the whole unit, or within a hundred-thousandth) or unknown (no rate, or no earlier day kept yet).
     */
    static int mood(JSONObject hist, String from, double live) {
        if (!(live > 0) || hist == null) return 0;
        String f = from.trim().toLowerCase(Locale.ROOT), today = java.time.LocalDate.now(java.time.ZoneOffset.UTC).toString();
        String best = null;
        double prev = 0;
        for (java.util.Iterator<String> it = hist.keys(); it.hasNext(); ) {
            String day = it.next();
            if (day.compareTo(today) >= 0 || (best != null && day.compareTo(best) <= 0)) continue;
            JSONObject o = hist.optJSONObject(day);
            double v = o == null ? 0 : o.optDouble(f, 0);
            if (v > 0) { best = day; prev = v; }
        }
        if (best == null) return 0;
        if ((live >= 100 && Math.round(live) == Math.round(prev)) || Math.abs(live - prev) <= prev * 1e-5) return 0;
        return live > prev ? 1 : -1;
    }

    /**
     * The mood as a picture: laughing and crowned with two sparkles (up, or the same), or sad in a chipped ring (down),
     * the supplied drawing (res/drawable/mood_up and mood_down, art/mood) as it is, with no glow.
     */
    static Bitmap mood(Context ctx, boolean up) {
        String key = "mood|" + up;
        Bitmap hit = PICS.get(key);
        if (hit != null) return hit;
        int s = 120;
        Bitmap out = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888);
        android.graphics.drawable.Drawable d = ctx.getDrawable(up ? R.drawable.mood_up : R.drawable.mood_down);
        if (d != null) {
            d.setBounds(0, 0, s, s);
            d.draw(new Canvas(out));
        }
        PICS.put(key, out);
        return out;
    }

    // ------------------------------------------------------------------ bitmaps

    /** Flag with rounded corners, or a grey tile with the code when the flag isn't downloaded yet. */
    static Bitmap flagOrLabel(Context ctx, String cc, String code) {
        String key = "f|" + cc + "|" + code + "|" + (Flags.flag(ctx, cc) != null);
        Bitmap hit = PICS.get(key);
        if (hit != null) return hit;
        Bitmap made = flagOrLabel0(ctx, cc, code);
        PICS.put(key, made);
        return made;
    }

    private static Bitmap flagOrLabel0(Context ctx, String cc, String code) {
        Bitmap fl = Flags.flag(ctx, cc);
        Bitmap out = Bitmap.createBitmap(120, 120, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        RectF r = new RectF(0, 0, 120, 120);
        if (fl != null) {
            cover(c, fl, r, 60);
        } else {
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setColor(Theme.SURFACE_HI);
            c.drawOval(r, p);
            Paint t = new Paint(Paint.ANTI_ALIAS_FLAG);
            t.setColor(Theme.MUTED);
            t.setTextSize(40);
            t.setTextAlign(Paint.Align.CENTER);
            c.drawText(cc == null ? "" : cc.toUpperCase(Locale.ROOT), 60, 60 - (t.ascent() + t.descent()) / 2f, t);
        }
        return out;
    }

    /**
     * The arrow between a row's two currencies: since 2.27 in the text's own white, as the codes beside it, whatever is
     * playing, with no haze (2.21 had it in the light with a faint haze); on a picture 4 dp bigger all round than the
     * 8 x 11 dp arrow, as the layout expects.
     */
    static Bitmap arrow(Scene scene) {
        String key = "a|white";
        Bitmap hit = PICS.get(key);
        if (hit != null) return hit;
        float px = 3f, u = 0.4f * px; // the old 20 x 26 unit arrow at 0.4 dp a unit
        int w = Math.round(16 * px), h = Math.round(19 * px);
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        float x0 = (w - 20 * u) / 2f, y0 = (h - 26 * u) / 2f;
        android.graphics.Path tri = new android.graphics.Path();
        tri.moveTo(x0 + u, y0);
        tri.lineTo(x0 + u, y0 + 26 * u);
        tri.lineTo(x0 + 19 * u, y0 + 13 * u);
        tri.close();
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStyle(Paint.Style.FILL);
        p.setColor(Theme.TEXT);
        c.drawPath(tri, p);
        PICS.put(key, b);
        return b;
    }

    /** "Market", without the clear margins round its ink, so centred in the labels' column it sits exactly over the badge. */
    private static Bitmap marketLabel(Typeface tf, int colour) {
        String key = "m|" + Look.get().tag() + "|" + System.identityHashCode(tf) + "|" + colour;
        Bitmap hit = PICS.get(key);
        if (hit != null) return hit;
        Bitmap b = tight(tf, CITY_SP, "Market", colour);
        int w = b.getWidth(), h = b.getHeight(), start = 0, end = w;
        int[] col = new int[h];
        while (end > 1 && clear(b, col, end - 1)) end--;
        while (start < end - 1 && clear(b, col, start)) start++;
        Bitmap made = start == 0 && end == w ? b : Bitmap.createBitmap(b, start, 0, end - start, h);
        PICS.put(key, made);
        return made;
    }

    /** Whether column {@code x} of {@code b} has no ink ({@code col} holds one column's pixels). */
    private static boolean clear(Bitmap b, int[] col, int x) {
        b.getPixels(col, 0, 1, x, 0, 1, col.length);
        for (int v : col) if ((v >>> 24) > 8) return false;
        return true;
    }

    /**
     * The Visa badge, monochrome as in the mockup (2.05): a small dark grey tile with the mark in white, so nothing but
     * the flags brings colour into the rows. Since 2.25 the mark is Visa's own wordmark ({@code R.drawable.visa_mark})
     * rather than "VISA" typed in italics.
     */
    static Bitmap visaLogo(Context ctx) {
        Bitmap hit = PICS.get("v|mark");
        if (hit != null) return hit;
        Bitmap out = Bitmap.createBitmap(130, 64, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(0xFF3A3A40);
        c.drawRoundRect(new RectF(0, 0, 130, 64), 9, 9, p);
        android.graphics.drawable.Drawable mark = ctx.getDrawable(R.drawable.visa_mark);
        if (mark != null) {
            mark = mark.mutate();
            mark.setTint(0xFFF2F2F4);
            int mw = 92, mh = Math.round(mw * 8.2f / 24f); // the mark's own proportions
            int x = (130 - mw) / 2, y = (64 - mh) / 2;
            mark.setBounds(x, y, x + mw, y + mh);
            mark.draw(c);
        }
        PICS.put("v|mark", out);
        return out;
    }

    /** Draws bmp scaled to cover dst (centre-crop) with anti-aliased rounded corners. */
    private static void cover(Canvas c, Bitmap bmp, RectF dst, float rad) {
        float bw = bmp.getWidth(), bh = bmp.getHeight();
        float sc = Math.max(dst.width() / bw, dst.height() / bh);
        Matrix m = new Matrix();
        m.setScale(sc, sc);
        m.postTranslate(dst.left + (dst.width() - bw * sc) / 2f, dst.top + (dst.height() - bh * sc) / 2f);
        BitmapShader sh = new BitmapShader(bmp, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        sh.setLocalMatrix(m);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        p.setShader(sh);
        if (rad > 0) c.drawRoundRect(dst, rad, rad, p);
        else c.drawRect(dst, p);
    }
}
