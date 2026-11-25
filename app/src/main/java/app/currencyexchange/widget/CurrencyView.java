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
     * Widget height (dp) from which the variant with k rows fits: the clocks card (94), the 16 dp gap (2.21; 12 before), and
     * the rates card (its 4 dp paddings, k rows of 54 and the k - 1 lines between them).
     */
    static int heightFor(int k) { return 117 + 55 * k; }

    /** As above for a widget {@code widthDp} wide: its clocks card as tall as a clock is wide (2.15). */
    static int heightFor(int k, int widthDp) { return heightFor(k) + clocksDp(widthDp) - 94; }

    /**
     * The clocks card's height (dp) in a widget {@code widthDp} wide: on Android 12 and up as tall as each of its four
     * clocks is wide, so they're square (2.15); before that its layout's fixed 94 dp.
     */
    static int clocksDp(int widthDp) {
        if (android.os.Build.VERSION.SDK_INT < 31) return 94;
        int w = widthDp >= 120 && widthDp <= 1200 ? widthDp : 360;
        return Math.max(72, Math.min(140, Math.round((w - 3) / 4f)));
    }

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
        // the clocks card: square clocks (2.15), its edge over the skies, which fill it to its edge
        int clocksH = clocksDp(rowW);
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            rv.setViewLayoutHeight(R.id.clk_row, clocksH, android.util.TypedValue.COMPLEX_UNIT_DIP);
        }
        rv.setImageViewBitmap(R.id.clk_rim, Stage.edge(Math.round(rowW * 1.5f), Math.round(clocksH * 1.5f),
                Stage.round(28 * 1.5f), 1.5f));

        // world clocks, each on its city's sky right now (Sky): the light of the hour there, its clouds and weather
        DateTimeFormatter dayF = DateTimeFormatter.ofPattern("EEEE", Locale.getDefault());
        DateTimeFormatter timeF = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault());
        long nowMs = System.currentTimeMillis();
        Clock[] clocks = clocks(ctx, nowMs);
        for (int i = 0; i < 4; i++) {
            Clock k0 = clocks[i];
            String city = k0.city;
            ZonedDateTime now = ZonedDateTime.now(k0.zone);
            // one picture per clock, at the cell's real width, its city's sky and corner (SkyCell); a copy, since the
            // live service draws the next frame into the same picture while this update is on its way
            rv.setImageViewBitmap(id(ctx, "clk_bg_" + i), cell(k0, i, rowW).frame(nowMs).copy(Bitmap.Config.ARGB_8888, false));
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
        Bitmap arrow = arrow(scene);
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
     * The arrow between a row's two currencies (2.21): solid in the light, with the same very faint haze round it as the
     * music player's progress line has (its bloom, in the light's deep shade), on a picture 4 dp bigger all round than
     * the 8 x 11 dp arrow so the haze has room.
     */
    static Bitmap arrow(Scene scene) {
        String key = "a|" + scene.tag();
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
        Look look = Look.get();
        float mist = Math.min(1.25f, (float) Math.sqrt(look.haze));
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStyle(Paint.Style.FILL_AND_STROKE);
        p.setStrokeJoin(Paint.Join.ROUND);
        p.setStrokeWidth(1.2f * px * mist);
        p.setColor(Scene.alpha(scene.deep, 0.5f * look.bloom));
        p.setMaskFilter(new android.graphics.BlurMaskFilter(1.75f * px * mist, android.graphics.BlurMaskFilter.Blur.NORMAL));
        c.drawPath(tri, p); // its haze, as the progress line's bloom
        p.setMaskFilter(null);
        p.setStyle(Paint.Style.FILL);
        p.setColor(scene.key);
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
     * The Visa badge, monochrome as in the mockup (2.05): a small dark grey tile with "VISA" in white bold italics,
     * so nothing but the flags brings colour into the rows.
     */
    static Bitmap visaLogo(Context ctx) {
        Bitmap hit = PICS.get("v|mono");
        if (hit != null) return hit;
        Bitmap out = Bitmap.createBitmap(130, 64, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(0xFF3A3A40);
        c.drawRoundRect(new RectF(0, 0, 130, 64), 9, 9, p);
        Paint t = new Paint(Paint.ANTI_ALIAS_FLAG);
        t.setColor(0xFFF2F2F4);
        t.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD_ITALIC));
        t.setTextSize(34);
        t.setTextAlign(Paint.Align.CENTER);
        t.setLetterSpacing(0.02f);
        c.drawText("VISA", 64, 32 - (t.ascent() + t.descent()) / 2f, t);
        PICS.put("v|mono", out);
        return out;
    }

    // Clock pictures are drawn at 2 px per dp (as sharp as the Sound widget's frames, and as light to send), the
    // card's full height (square since 2.15), at each cell's real width.
    private static final float PXDP = 2f;
    private static final float OUTER_R = 28 * PXDP; // the card's own corners: the skies fill it to its edge

    /** What a world clock needs to draw: its city, where it is, the sun's height there, its weather. */
    static final class Clock {
        String city;
        ZoneId zone = ZoneId.systemDefault();
        double lat = Double.NaN, lon = Double.NaN, elevation;
        boolean morning;
        int cloud = -1, code = -1, wind = -1;
    }

    /** The four world clocks at {@code ms}. */
    static Clock[] clocks(Context ctx, long ms) {
        Clock[] out = new Clock[4];
        for (int i = 0; i < 4; i++) {
            Clock k = new Clock();
            k.city = Store.city(ctx, i);
            JSONObject g = Store.geo(ctx, k.city);
            if (g != null) {
                try { k.zone = ZoneId.of(g.optString("tz", k.zone.getId())); } catch (Exception ignored) { }
                k.lat = g.optDouble("lat", Double.NaN);
                k.lon = g.optDouble("lon", Double.NaN);
            }
            ZonedDateTime now = java.time.Instant.ofEpochMilli(ms).atZone(k.zone);
            double hr = now.getHour() + now.getMinute() / 60.0;
            k.morning = hr < 12.5;
            if (!Double.isNaN(k.lat) && !Double.isNaN(k.lon)) k.elevation = SunCalc.elevation(k.lat, k.lon, ms);
            else k.elevation = 50 * Math.cos((hr - 12) / 12 * Math.PI) - 5; // no place known: a rough sun from the hour
            int[] wx = Store.sky(ctx, k.city);
            if (wx != null) {
                k.cloud = wx[0];
                k.code = wx[1];
                k.wind = wx.length > 2 ? wx[2] : -1;
            }
            out[i] = k;
        }
        return out;
    }

    /** Clock {@code i}'s picture-maker in a card {@code rowW} dp wide (four cells and the three 1 dp seams between). */
    static SkyCell cell(Clock k, int i, int rowW) {
        int w = Math.max(8, Math.round((rowW - 3) / 4f * PXDP)), h = Math.round(clocksDp(rowW) * PXDP);
        return SkyCell.get(k.city, k.lat, k.lon, k.zone, k.elevation, k.morning, k.cloud, k.code, k.wind, w, h, PXDP,
                i == 0 ? 1 : (i == 3 ? 2 : 0), OUTER_R);
    }

    private static Clock[] liveClocks;
    private static long liveClocksAt;
    private static int[] bgIds;

    /**
     * Sends each Currency widget its four clocks' next frames (the live service's sky loop, while skies are animated):
     * a partial update of just those four pictures. The clocks' places and weather are looked up again every 15 s.
     */
    static void pushSkies(Context ctx, int[] ids, long ms) {
        if (liveClocks == null || ms - liveClocksAt > 15_000L || ms < liveClocksAt) {
            liveClocks = clocks(ctx, ms);
            liveClocksAt = ms;
        }
        if (bgIds == null) {
            bgIds = new int[4];
            for (int i = 0; i < 4; i++) bgIds[i] = id(ctx, "clk_bg_" + i);
        }
        android.appwidget.AppWidgetManager awm = android.appwidget.AppWidgetManager.getInstance(ctx);
        for (int id : ids) {
            int k = WidgetProvider.variant(ctx, awm, id), rowW = WidgetProvider.widthDp(ctx, awm, id);
            RemoteViews rv = new RemoteViews(ctx.getPackageName(), LAYOUTS[k - 1]);
            for (int i = 0; i < 4; i++) rv.setImageViewBitmap(bgIds[i], cell(liveClocks[i], i, rowW).frame(ms));
            awm.partiallyUpdateAppWidget(id, rv);
        }
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
