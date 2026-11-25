package app.currencyexchange.widget;

import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.widget.RemoteViews;

import org.json.JSONObject;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * The World clocks widget (2.26; until then the top of the Currency widget) as native views
 * (res/layout/clocks_n*.xml): its clocks, each on its city's sky right now ({@link SkyCell}), in one row of up to four
 * or two even rows of three or four, hairlines between them and the card's edge over them. On Android 12 and up each
 * clock is square, or as tall as the widget allows; the day, time and city sit centred in front, white.
 */
final class ClockView {
    private ClockView() {}

    /** How many clocks the widget can show (one row up to four, then two even rows). */
    static final int[] COUNTS = {1, 2, 3, 4, 6, 8};

    private static int layout(int n) {
        switch (n) {
            case 1: return R.layout.clocks_n1;
            case 2: return R.layout.clocks_n2;
            case 3: return R.layout.clocks_n3;
            case 6: return R.layout.clocks_n6;
            case 8: return R.layout.clocks_n8;
            default: return R.layout.clocks_n4;
        }
    }

    /** Columns for {@code n} clocks. */
    static int cols(int n) { return n <= 4 ? n : n / 2; }

    /** Rows for {@code n} clocks. */
    static int rows(int n) { return n <= 4 ? 1 : 2; }

    private static int id(Context c, String name) {
        return c.getResources().getIdentifier(name, "id", c.getPackageName());
    }

    // Clock pictures are drawn at 2 px per dp (as sharp as the Sound widget's frames, and as light to send), each at its
    // cell's real size.
    private static final float PXDP = 2f;
    private static final float OUTER_R = 28 * PXDP; // the card's own corners: the skies fill it to its edge

    /** The widget's width in dp as its pictures are drawn for (360 when the launcher doesn't say). */
    static int width(int widthDp) { return widthDp >= 120 && widthDp <= 1200 ? widthDp : 360; }

    /** A clock's width in dp for {@code n} clocks in a widget {@code widthDp} wide (the hairlines between them 1 dp). */
    static float cellW(int n, int widthDp) {
        int cols = cols(n);
        return (width(widthDp) - (cols - 1)) / (float) cols;
    }

    /**
     * A clock's height in dp: on Android 12 and up as tall as it's wide (square, between 72 and 140 dp), or less if the
     * widget isn't that tall ({@code heightDp}, 0 when unknown); before that the layout's fixed 94 dp.
     */
    static int cellH(int n, int widthDp, int heightDp) {
        if (android.os.Build.VERSION.SDK_INT < 31) return 94;
        int h = Math.max(72, Math.min(140, Math.round(cellW(n, widthDp))));
        int rows = rows(n);
        if (heightDp >= 60) h = Math.min(h, (heightDp - (rows - 1)) / rows);
        return Math.max(48, h);
    }

    /** The clock card's height in dp. */
    static int cardH(int n, int widthDp, int heightDp) {
        int rows = rows(n);
        return rows * cellH(n, widthDp, heightDp) + (rows - 1);
    }

    /** What a world clock needs to draw: its city, where it is, the sun's height there, its weather. */
    static final class Clock {
        String city;
        ZoneId zone = ZoneId.systemDefault();
        double lat = Double.NaN, lon = Double.NaN, elevation;
        boolean morning;
        int cloud = -1, code = -1, wind = -1;
    }

    /** The first {@code n} world clocks at {@code ms}. */
    static Clock[] clocks(Context ctx, long ms, int n) {
        Clock[] out = new Clock[n];
        for (int i = 0; i < n; i++) {
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

    /** The card's round corners each clock carries ({@link SkyCell#TL} ...): only the ones at the card's corners. */
    static int corners(int i, int n) {
        int cols = cols(n), rows = rows(n), r = i / cols, c = i % cols, m = 0;
        if (r == 0 && c == 0) m |= SkyCell.TL;
        if (r == 0 && c == cols - 1) m |= SkyCell.TR;
        if (r == rows - 1 && c == cols - 1) m |= SkyCell.BR;
        if (r == rows - 1 && c == 0) m |= SkyCell.BL;
        return m;
    }

    /** Clock {@code i}'s picture-maker, of {@code n}, in a widget {@code widthDp} by {@code heightDp}. */
    static SkyCell cell(Clock k, int i, int n, int widthDp, int heightDp) {
        int w = Math.max(8, Math.round(cellW(n, widthDp) * PXDP)), h = Math.round(cellH(n, widthDp, heightDp) * PXDP);
        return SkyCell.get(k.city, k.lat, k.lon, k.zone, k.elevation, k.morning, k.cloud, k.code, k.wind, w, h, PXDP,
                corners(i, n), OUTER_R);
    }

    /** The widget with {@code n} clocks, {@code widthDp} by {@code heightDp} (0 when unknown). */
    static RemoteViews build(Context ctx, int n, int widthDp, int heightDp) {
        Typeface reg = Theme.uiMedium(ctx);
        RemoteViews rv = new RemoteViews(ctx.getPackageName(), layout(n));
        int rowW = width(widthDp), cardH = cardH(n, widthDp, heightDp);
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            rv.setViewLayoutHeight(R.id.clk_grid, cardH, android.util.TypedValue.COMPLEX_UNIT_DIP);
        }
        rv.setImageViewBitmap(R.id.clk_rim, Stage.edge(Math.round(rowW * 1.5f), Math.round(cardH * 1.5f),
                Stage.round(28 * 1.5f), 1.5f));
        DateTimeFormatter dayF = DateTimeFormatter.ofPattern("EEEE", Locale.getDefault());
        DateTimeFormatter timeF = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault());
        long nowMs = System.currentTimeMillis();
        Clock[] clocks = clocks(ctx, nowMs, n);
        int white = Theme.TEXT;
        for (int i = 0; i < n; i++) {
            Clock k = clocks[i];
            ZonedDateTime now = ZonedDateTime.now(k.zone);
            // one picture per clock, at the cell's real size, its city's sky and corner (SkyCell); a copy, since the
            // live service draws the next frame into the same picture while this update is on its way
            rv.setImageViewBitmap(id(ctx, "clk_bg_" + i),
                    cell(k, i, n, widthDp, heightDp).frame(nowMs).copy(Bitmap.Config.ARGB_8888, false));
            // the day, time and city in front of the sky stay white whatever is playing
            rv.setImageViewBitmap(id(ctx, "clk_day_" + i), TextBmp.fringed(reg, 11 * 2.5f, 0.8f, now.format(dayF), white));
            rv.setImageViewBitmap(id(ctx, "clk_time_" + i), TextBmp.fringed(reg, 22 * 2.5f, 0.8f, now.format(timeF), white));
            rv.setImageViewBitmap(id(ctx, "clk_city_" + i), TextBmp.fringed(reg, 11 * 2.5f, 0.8f, Util.capWords(k.city), white));
        }
        return rv;
    }

    private static Clock[] liveClocks;
    private static long liveClocksAt;
    private static int liveN;
    private static int[] bgIds;

    /**
     * Sends each World clocks widget its clocks' next frames (the live service's sky loop, while skies are animated):
     * a partial update of just those pictures. The clocks' places and weather are looked up again every 15 s.
     */
    static void pushSkies(Context ctx, int[] ids, long ms) {
        int n = Store.clockCount(ctx);
        if (liveClocks == null || liveN != n || ms - liveClocksAt > 15_000L || ms < liveClocksAt) {
            liveClocks = clocks(ctx, ms, n);
            liveClocksAt = ms;
            liveN = n;
        }
        if (bgIds == null) {
            bgIds = new int[8];
            for (int i = 0; i < 8; i++) bgIds[i] = id(ctx, "clk_bg_" + i);
        }
        AppWidgetManager awm = AppWidgetManager.getInstance(ctx);
        for (int id : ids) {
            int w = ClockWidgetProvider.widthDp(ctx, awm, id), h = ClockWidgetProvider.heightDp(ctx, awm, id);
            RemoteViews rv = new RemoteViews(ctx.getPackageName(), layout(n));
            for (int i = 0; i < n; i++) rv.setImageViewBitmap(bgIds[i], cell(liveClocks[i], i, n, w, h).frame(ms));
            awm.partiallyUpdateAppWidget(id, rv);
        }
    }
}
