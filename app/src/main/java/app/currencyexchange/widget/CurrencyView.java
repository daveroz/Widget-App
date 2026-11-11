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
 * Currency widget as native views (res/layout/widget.xml). Rows, columns and text boxes are weighted, so the
 * widget follows any width and height the launcher gives it. Only the flags, the Visa logo and the clock
 * backgrounds are small bitmaps (rounded corners baked in).
 */
final class CurrencyView {
    private CurrencyView() {}

    private static int id(Context c, String name) {
        return c.getResources().getIdentifier(name, "id", c.getPackageName());
    }

    /** Text as a picture at a fixed size (sizeDp = font size); the ImageView height in the layout is sizeDp * 1.46. */
    private static void txt(Context ctx, RemoteViews rv, int viewId, String s, Typeface tf, int color, float sizeDp, boolean shadow) {
        rv.setImageViewBitmap(viewId, TextBmp.make(tf, sizeDp * 2.5f, 0.8f, false, shadow, s, color));
    }

    static final int[] LAYOUTS = {R.layout.widget_k1, R.layout.widget_k2, R.layout.widget_k3,
            R.layout.widget_k4, R.layout.widget_k5, R.layout.widget_k6};

    /** Widget height (dp) from which the variant with k rows fits. */
    static int heightFor(int k) { return 114 + 61 * k; }

    private static final android.util.LruCache<String, Bitmap> PICS = new android.util.LruCache<>(60);

    /** Variant with k currency rows (1..6). */
    private static void txtF(RemoteViews rv, int viewId, String s, Typeface tf, int color, float sizeDp, float fill) {
        rv.setImageViewBitmap(viewId, TextBmp.make(tf, sizeDp * 2.5f, fill, false, false, s, color));
    }

    /** The size the clock city names actually show at (11 drawn into a 16 dp box with Carbon+'s 0.95 em line). */
    private static final float CITY_SP = 16f * 0.8f / 0.95f * 0.985f;

    /** The size the weather-bar numbers (rain chance etc.) show at: 12 drawn into an 18 dp box. */
    private static final float HEADER_SP = 12f / 0.82f;

    /** The chance of rain shown is the highest over the current hour and this many hours ahead. */
    private static final int RAIN_HOURS = 12;

    /** Header icon for a WMO weather code (Open-Meteo); clear and partly cloudy have night versions. */
    static int weatherIcon(int code, boolean day) {
        if (code == 0) return day ? R.drawable.wx_clear_day : R.drawable.wx_clear_night;
        if (code == 1 || code == 2) return day ? R.drawable.wx_partly_day : R.drawable.wx_partly_night;
        if (code == 45 || code == 48) return R.drawable.wx_fog;
        if ((code >= 51 && code <= 67) || (code >= 80 && code <= 82)) return R.drawable.wx_rain;
        if ((code >= 71 && code <= 77) || code == 85 || code == 86) return R.drawable.wx_snow;
        if (code >= 95 && code <= 99) return R.drawable.wx_storm;
        return R.drawable.wx_cloud; // 3 = overcast, or unknown
    }

    /** Height of a currency row in the layouts (dp). */
    private static final int ROW_DP = 54;

    /** @param widthDp the widget's width as the launcher reports it (0 if unknown); sizes the trend charts */
    static RemoteViews build(Context ctx, int k, int widthDp) {
        Typeface reg = Theme.uiMedium(ctx), bold = Theme.uiBold(ctx);
        RemoteViews rv = new RemoteViews(ctx.getPackageName(), LAYOUTS[k - 1]);

        // header where you are, four equal slots: weather now (icon + temperature), chance of rain in the coming
        // hours, sunrise, sunset
        Store.Weather wx = Store.weather(ctx);
        double[] ll = Store.weatherLatLon(ctx);
        ZonedDateTime here = ZonedDateTime.now();
        String rise = "--:--", set = "--:--";
        if (!Double.isNaN(ll[0]) && !Double.isNaN(ll[1])) {
            int[] t = SunCalc.times(ll[0], ll[1], here.toLocalDate(), ZoneId.systemDefault());
            rise = Util.hm(t[0]);
            set = Util.hm(t[1]);
        }
        boolean day = SunCalc.isDay(ll[0], ll[1], ZoneId.systemDefault(), here);
        rv.setImageViewResource(R.id.hdr_wx, weatherIcon(wx == null ? -1 : wx.code, day));
        txt(ctx, rv, R.id.hdr_temp, (wx == null ? "--" : String.valueOf(Math.round(wx.temp))) + "\u00B0C",
                reg, Theme.TEXT, 12, false);
        int rain = wx == null ? -1 : wx.rainChance(System.currentTimeMillis(), RAIN_HOURS);
        txt(ctx, rv, R.id.hdr_rain, rain < 0 ? "--%" : rain + "%", reg, Theme.TEXT, 12, false);
        txt(ctx, rv, R.id.hdr_rise, rise, reg, Theme.TEXT, 12, false);
        txt(ctx, rv, R.id.hdr_set, set, reg, Theme.TEXT, 12, false);

        // world clocks
        DateTimeFormatter dayF = DateTimeFormatter.ofPattern("EEEE", Locale.getDefault());
        DateTimeFormatter timeF = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault());
        for (int i = 0; i < 4; i++) {
            String city = Store.city(ctx, i);
            JSONObject g = Store.geo(ctx, city);
            ZoneId z = ZoneId.systemDefault();
            double lat = Double.NaN, lon = Double.NaN;
            String cc = "";
            if (g != null) {
                try { z = ZoneId.of(g.optString("tz", z.getId())); } catch (Exception ignored) { }
                lat = g.optDouble("lat", Double.NaN);
                lon = g.optDouble("lon", Double.NaN);
                cc = g.optString("cc", "");
            }
            ZonedDateTime now = ZonedDateTime.now(z);
            int phase = SunCalc.phase(lat, lon, z, now);
            Bitmap[] pcs = clockPieces(Flags.flag(ctx, cc), phase, i == 0 ? 1 : (i == 3 ? 2 : 0));
            if (i == 0) {
                rv.setImageViewBitmap(id(ctx, "clk_bg_0a"), pcs[0]);
                rv.setImageViewBitmap(id(ctx, "clk_bg_0"), pcs[1]);
            } else if (i == 3) {
                rv.setImageViewBitmap(id(ctx, "clk_bg_3"), pcs[0]);
                rv.setImageViewBitmap(id(ctx, "clk_bg_3a"), pcs[1]);
            } else {
                rv.setImageViewBitmap(id(ctx, "clk_bg_" + i), pcs[0]);
            }
            txt(ctx, rv, id(ctx, "clk_day_" + i), now.format(dayF), reg, Theme.TEXT_SOFT, 11, false);
            txt(ctx, rv, id(ctx, "clk_time_" + i), now.format(timeF), bold, Theme.TEXT, 22, false);
            txt(ctx, rv, id(ctx, "clk_city_" + i), Util.capWords(city), reg, Theme.TEXT, 11, false);
        }

        // currency rows
        String to = Store.to(ctx).trim().toUpperCase(Locale.ROOT);
        Bitmap visaLogo = visaLogo(ctx);
        Bitmap toFlag = flagOrLabel(ctx, Util.countryOfCurrency(to), to);
        JSONObject hist = Store.hist(ctx, to);
        int rowW = widthDp >= 120 && widthDp <= 1200 ? widthDp : 360;
        for (int i = 0; i < k; i++) {
            String from = Store.from(ctx, i).trim().toUpperCase(Locale.ROOT);
            rv.setOnClickPendingIntent(id(ctx, "row_" + i), ConverterActivity.intentFor(ctx, i)); // tap a row: converter
            rv.setImageViewBitmap(id(ctx, "fl_a_" + i), flagOrLabel(ctx, Util.countryOfCurrency(from), from));
            txt(ctx, rv, id(ctx, "code_a_" + i), from, reg, Theme.TEXT, 15, false);
            rv.setImageViewBitmap(id(ctx, "fl_b_" + i), toFlag);
            txt(ctx, rv, id(ctx, "code_b_" + i), to, reg, Theme.TEXT, 15, false);
            double market = Store.rate(ctx, from, to);
            // the last month as a soft area chart behind the row (the picture fills the row inside its 1 dp border)
            double[][] trend = Trend.series(hist, from, market);
            if (trend != null) rv.setImageViewBitmap(id(ctx, "trend_" + i), Trend.bitmap(trend, rowW - 2, ROW_DP - 2));
            rv.setImageViewBitmap(id(ctx, "mkt_lbl_" + i), TextBmp.tight(reg, CITY_SP, "Market", Theme.MUTED));
            rv.setImageViewBitmap(id(ctx, "mkt_val_" + i), TextBmp.tight(reg, HEADER_SP, Util.fmt0(market), Theme.TEXT));
            rv.setImageViewBitmap(id(ctx, "visa_logo_" + i), visaLogo);
            double visa = Util.alignToMarket(Store.visa(ctx, from, to), market);
            int vv = id(ctx, "visa_val_" + i);
            if (!Double.isNaN(visa)) {
                rv.setImageViewBitmap(vv, TextBmp.tight(reg, HEADER_SP, Util.fmt0(visa), Theme.TEXT_SOFT));
            } else if (!Double.isNaN(market)) {
                // Visa did not answer: estimate = market + fee, marked "~" and muted
                rv.setImageViewBitmap(vv, TextBmp.tight(reg, HEADER_SP, "~" + Util.fmt0(market * (1.0 + Store.fee(ctx) / 100.0)), Theme.MUTED));
            } else {
                rv.setImageViewBitmap(vv, TextBmp.tight(reg, HEADER_SP, "", Theme.MUTED));
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

    private static int phaseColor(int phase) {
        switch (phase) {
            case SunCalc.DAY: return Theme.AMBER;
            case SunCalc.SUNSET: return 0xFFE2553A;  // golden hour / sunset / sunrise
            case SunCalc.BLUE: return 0xFF3F6FD8;    // blue hour
            default: return Theme.NIGHT;
        }
    }

    // Clock cell pictures are drawn for a typical 90 x 84 dp cell at 1.5 px per dp. The outer bottom corners are
    // separate 28 dp wide slices shown at a fixed width, so their curve always matches the card exactly.
    private static final float PXDP = 1.5f;
    private static final int CW = Math.round(90 * PXDP), CH = Math.round(84 * PXDP), SLICE = Math.round(28 * PXDP);
    private static final float INNER_R = 27 * PXDP; // card radius just inside its 1 dp border

    /** side 0: middle cell {full}; 1: left cell {corner, rest}; 2: right cell {rest, corner}. */
    private static Bitmap[] clockPieces(Bitmap flag, int phase, int side) {
        String key = "c|" + System.identityHashCode(flag) + "|" + phase + "|" + side;
        Bitmap a = PICS.get(key + "|0"), b = PICS.get(key + "|1");
        if (a != null && (side == 0 || b != null)) return side == 0 ? new Bitmap[]{a} : new Bitmap[]{a, b};
        Bitmap full = clockFull(flag, phase);
        Bitmap[] out;
        if (side == 0) {
            out = new Bitmap[]{full};
        } else if (side == 1) {
            out = new Bitmap[]{corner(Bitmap.createBitmap(full, 0, 0, SLICE, CH), true),
                    Bitmap.createBitmap(full, SLICE, 0, CW - SLICE, CH)};
        } else {
            out = new Bitmap[]{Bitmap.createBitmap(full, 0, 0, CW - SLICE, CH),
                    corner(Bitmap.createBitmap(full, CW - SLICE, 0, SLICE, CH), false)};
        }
        PICS.put(key + "|0", out[0]);
        if (out.length > 1) PICS.put(key + "|1", out[1]);
        return out;
    }

    /** Clears the outside of a round bottom corner (bottom-left if left, else bottom-right). */
    private static Bitmap corner(Bitmap src, boolean left) {
        Bitmap b = src.copy(Bitmap.Config.ARGB_8888, true);
        Canvas c = new Canvas(b);
        int w = b.getWidth(), h = b.getHeight();
        float r = INNER_R;
        Path outside = new Path();
        outside.addRoundRect(new RectF(left ? 0 : -r, 0, left ? w + r : w, h),
                new float[]{0, 0, 0, 0, left ? 0 : r, left ? 0 : r, left ? r : 0, left ? r : 0}, Path.Direction.CW);
        outside.setFillType(Path.FillType.INVERSE_WINDING);
        Paint clear = new Paint(Paint.ANTI_ALIAS_FLAG);
        clear.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
        c.drawPath(outside, clear);
        return b;
    }

    /** Flag faded into the night with the sun-phase band of light below (one exact boundary). */
    private static Bitmap clockFull(Bitmap flag, int phase) {
        final int w = CW, h = CH;
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        final float split = Math.round(h * 0.94f);
        Paint base = new Paint();
        base.setColor(Theme.DEEP);
        c.drawRect(0, 0, w, h, base);
        RectF fr = new RectF(0, 0, w, split);
        if (flag != null) {
            cover(c, flag, fr, 0);
        } else {
            Paint p = new Paint();
            p.setColor(Theme.SURFACE_HI);
            c.drawRect(fr, p);
        }
        Paint gp = new Paint();
        gp.setShader(new LinearGradient(0, 0, 0, split, 0x66000000, Theme.DEEP, Shader.TileMode.CLAMP));
        c.drawRect(fr, gp);
        // The sun-phase band is a strip of light: it glows up into the haze above it.
        int band = phaseColor(phase);
        float rise = h * 0.3f;
        Paint haze = new Paint(Paint.DITHER_FLAG);
        haze.setShader(new LinearGradient(0, split - rise, 0, split, band & 0x00FFFFFF, (band & 0x00FFFFFF) | 0x4D000000,
                Shader.TileMode.CLAMP));
        c.drawRect(0, split - rise, w, split, haze);
        Paint sp = new Paint();
        sp.setColor(band);
        c.drawRect(0, split, w, h, sp);
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
