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
 * Currency widget as native views (res/layout/widget_k*.xml): the world clocks, each on its city's sky right now
 * ({@link Sky}), and the currency rows on one card, thin lines between them. Rows, columns and text boxes are
 * weighted, so the widget follows any width and height the launcher gives it. Only the flags, the Visa logo, the
 * skies and the cards are small bitmaps (rounded corners baked in).
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
     * Widget height (dp) from which the variant with k rows fits: the clocks card (94), 7 dp, and the rates card (its
     * 4 dp paddings, k rows of 54 and the k - 1 lines between them).
     */
    static int heightFor(int k) { return 108 + 55 * k; }

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

    /** @param widthDp the widget's width as the launcher reports it (0 if unknown); sizes the trend charts */
    static RemoteViews build(Context ctx, int k, int widthDp) {
        Typeface reg = Theme.uiMedium(ctx);
        RemoteViews rv = new RemoteViews(ctx.getPackageName(), LAYOUTS[k - 1]);
        // The light all the widgets share: the playing song's colour, or amber.
        Scene scene = Ambience.scene();
        // The text is plain, in its own colours (no glow, no tint).
        int white = Theme.TEXT, soft = Theme.TEXT_SOFT, muted = Theme.MUTED;
        int rowW = widthDp >= 120 && widthDp <= 1200 ? widthDp : 360;
        // the clocks card's fill and edge (94 dp tall: its 1 dp padding round the 92 dp clocks)
        rv.setImageViewBitmap(R.id.clk_glass, Stage.panel(scene, rowW, 94, 1.5f, Stage.round(28), null, false));
        rv.setImageViewBitmap(R.id.clk_rim, Stage.edge(Math.round(rowW * 1.5f), Math.round(94 * 1.5f),
                Stage.round(28 * 1.5f), 1.5f));

        // world clocks, each on its city's sky right now (Sky): the light of the hour there, its clouds and weather
        DateTimeFormatter dayF = DateTimeFormatter.ofPattern("EEEE", Locale.getDefault());
        DateTimeFormatter timeF = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault());
        for (int i = 0; i < 4; i++) {
            String city = Store.city(ctx, i);
            JSONObject g = Store.geo(ctx, city);
            ZoneId z = ZoneId.systemDefault();
            double lat = Double.NaN, lon = Double.NaN;
            if (g != null) {
                try { z = ZoneId.of(g.optString("tz", z.getId())); } catch (Exception ignored) { }
                lat = g.optDouble("lat", Double.NaN);
                lon = g.optDouble("lon", Double.NaN);
            }
            ZonedDateTime now = ZonedDateTime.now(z);
            double el;
            if (!Double.isNaN(lat) && !Double.isNaN(lon)) el = SunCalc.elevation(lat, lon, now.toInstant().toEpochMilli());
            else { // no place known: a rough sun from the hour (highest at noon)
                double hr = now.getHour() + now.getMinute() / 60.0;
                el = 50 * Math.cos((hr - 12) / 12 * Math.PI) - 5;
            }
            int[] weather = Store.sky(ctx, city);
            // one picture per clock, at the cell's real width (a quarter of the card inside its 1 dp padding), so nothing
            // is stretched and there's no seam; the outer cells have the card's round corners baked in
            rv.setImageViewBitmap(id(ctx, "clk_bg_" + i), skyCell(el, weather == null ? -1 : weather[0],
                    weather == null ? -1 : weather[1], city.hashCode(), i == 0 ? 1 : (i == 3 ? 2 : 0), (rowW - 2) / 4f));
            // the day, time and city in front of the sky stay white whatever is playing
            txt(ctx, rv, id(ctx, "clk_day_" + i), now.format(dayF), reg, white, 11);
            rv.setImageViewBitmap(id(ctx, "clk_time_" + i), TextBmp.fringed(reg, 22 * 2.5f, 0.8f, now.format(timeF), // the calendar's weight, not bold
                    white));
            txt(ctx, rv, id(ctx, "clk_city_" + i), Util.capWords(city), reg, white, 11);
        }

        // currency rows
        String to = Store.to(ctx).trim().toUpperCase(Locale.ROOT);
        Bitmap visaLogo = visaLogo(ctx);
        Bitmap marketLbl = marketLabel(reg, scene.quiet);
        Bitmap toFlag = flagOrLabel(ctx, Util.countryOfCurrency(to), to);
        JSONObject hist = Store.hist(ctx, to);
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
            // the last month as a thread of light across the row, on the rows' shared card
            double[][] trend = Trend.series(hist, from, market);
            rv.setImageViewBitmap(id(ctx, "trend_" + i), Trend.bitmap(trend, rowW, ROW_DP, scene, true));
            rv.setInt(id(ctx, "arrow_" + i), "setColorFilter", scene.key);
            rv.setImageViewBitmap(id(ctx, "mkt_lbl_" + i), marketLbl);
            rv.setImageViewBitmap(id(ctx, "mkt_val_" + i), tight(reg, HEADER_SP, Util.fmt0(market), white));
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
     * "Market", its ink ending at its picture's right edge (no clear margin there), so drawn to the right of its slot it
     * ends where the Visa badge and the line between the rows end.
     */
    private static Bitmap marketLabel(Typeface tf, int colour) {
        String key = "m|" + Look.get().tag() + "|" + System.identityHashCode(tf) + "|" + colour;
        Bitmap hit = PICS.get(key);
        if (hit != null) return hit;
        Bitmap b = tight(tf, CITY_SP, "Market", colour);
        int w = b.getWidth(), h = b.getHeight(), end = w;
        int[] col = new int[h];
        while (end > 1) {
            b.getPixels(col, 0, 1, end - 1, 0, 1, h);
            boolean ink = false;
            for (int v : col) if ((v >>> 24) > 8) { ink = true; break; }
            if (ink) break;
            end--;
        }
        Bitmap made = end == w ? b : Bitmap.createBitmap(b, 0, 0, end, h);
        PICS.put(key, made);
        return made;
    }

    static Bitmap visaLogo(Context ctx) {
        String key = "v|" + (Flags.visa(ctx) != null);
        Bitmap hit = PICS.get(key);
        if (hit != null) return hit;
        Bitmap made = visaLogo0(ctx);
        PICS.put(key, made);
        return made;
    }

    private static Bitmap visaLogo0(Context ctx) {
        Bitmap vb = Flags.visa(ctx);
        Bitmap out = Bitmap.createBitmap(120, 80, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        RectF r = new RectF(0, 0, 120, 80);
        if (vb != null) {
            cover(c, vb, r, 12);
        } else {
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setColor(0xFF1A3C9C);
            c.drawRoundRect(r, 12, 12, p);
            Paint t = new Paint(Paint.ANTI_ALIAS_FLAG);
            t.setColor(Theme.TEXT);
            t.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD_ITALIC));
            t.setTextSize(30);
            t.setTextAlign(Paint.Align.CENTER);
            c.drawText("VISA", 60, 40 - (t.ascent() + t.descent()) / 2f, t);
        }
        return out;
    }

    // Clock cell pictures are drawn at 1.5 px per dp, 92 dp tall, at each cell's real width.
    private static final float PXDP = 1.5f;
    private static final int CH = Math.round(92 * PXDP);
    private static final float INNER_R = 27 * PXDP; // card radius just inside its 1 dp border

    /**
     * A clock's sky ({@link Sky}), {@code cellDp} wide: side 0, a middle cell; 1, the left cell (the card's round corners
     * on its left); 2, the right cell (on its right). The sun's height is taken to the nearest half degree, so a sky is
     * drawn again only when it has visibly changed.
     */
    private static Bitmap skyCell(double elevation, int cloud, int code, long seed, int side, float cellDp) {
        float el = Math.round(elevation * 2) / 2f;
        int w = Math.max(8, Math.round(cellDp * PXDP));
        String key = "s|" + el + "|" + (cloud < 0 ? -1 : cloud / 5) + "|" + code + "|" + seed + "|" + side + "|" + w;
        Bitmap hit = PICS.get(key);
        if (hit != null) return hit;
        Bitmap b = Sky.draw(w, CH, PXDP, el, cloud, code, seed);
        if (side != 0) b = corner(b, side == 1);
        PICS.put(key, b);
        return b;
    }

    /** Clears the outside of the card's round corners, top and bottom (on the left if left, else on the right). */
    private static Bitmap corner(Bitmap src, boolean left) {
        Bitmap b = src.copy(Bitmap.Config.ARGB_8888, true);
        Canvas c = new Canvas(b);
        int w = b.getWidth(), h = b.getHeight();
        float r = INNER_R;
        Path outside = new Path();
        outside.addRoundRect(new RectF(left ? 0 : -r, 0, left ? w + r : w, h),
                left ? new float[]{r, r, 0, 0, 0, 0, r, r} : new float[]{0, 0, r, r, r, r, 0, 0}, Path.Direction.CW);
        outside.setFillType(Path.FillType.INVERSE_WINDING);
        Paint clear = new Paint(Paint.ANTI_ALIAS_FLAG);
        clear.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
        c.drawPath(outside, clear);
        return b;
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
