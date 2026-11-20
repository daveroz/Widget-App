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

    /** Widget height (dp) from which the variant with k rows fits. */
    static int heightFor(int k) { return 114 + 61 * k; }

    private static final android.util.LruCache<String, Bitmap> PICS = new android.util.LruCache<>(60);

    /** Variant with k currency rows (1..6). */
    private static void txtF(RemoteViews rv, int viewId, String s, Typeface tf, int color, float sizeDp, float fill) {
        rv.setImageViewBitmap(viewId, TextBmp.fringed(tf, sizeDp * 2.5f, fill, s, color));
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
        // The light all the widgets share: the playing song's colour, or amber.
        Scene scene = Ambience.scene();
        // The text is plain, in its own colours (no glow, no tint).
        int white = Theme.TEXT, soft = Theme.TEXT_SOFT, muted = Theme.MUTED;
        int rowW = widthDp >= 120 && widthDp <= 1200 ? widthDp : 360;
        // the clocks card's fill, behind the weather strip and the clocks (116 dp tall: its 1 dp padding, the 30 dp strip,
        // the 84 dp clocks); the flags fade into it
        rv.setImageViewBitmap(R.id.clk_glass, Stage.panel(scene, rowW, 116, 1.5f, Stage.round(28), null, false));
        // the clocks card's edge, over the card (116 dp tall: its 1 dp padding, the 30 dp strip, the 84 dp clocks)
        rv.setImageViewBitmap(R.id.clk_rim, Stage.edge(scene, Math.round(rowW * 1.5f), Math.round(116 * 1.5f),
                Stage.round(28 * 1.5f), 1.5f));
        // the strip's dividers in the song's line colour
        for (int sep : new int[]{R.id.sep0, R.id.sep1, R.id.sep2}) rv.setInt(sep, "setColorFilter", scene.line);

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
        // The header's icons say what each value is, so they're in the song's line colour, solid and a little darker.
        rv.setImageViewBitmap(R.id.hdr_wx, icon(ctx, weatherIcon(wx == null ? -1 : wx.code, day), 16, scene));
        rv.setImageViewBitmap(R.id.ic0, icon(ctx, R.drawable.ic_umbrella, 14, scene));
        rv.setImageViewBitmap(R.id.ic1, icon(ctx, R.drawable.ic_sunrise, 14, scene));
        rv.setImageViewBitmap(R.id.ic2, icon(ctx, R.drawable.ic_sunset, 14, scene));
        txt(ctx, rv, R.id.hdr_temp, (wx == null ? "--" : String.valueOf(Math.round(wx.temp))) + "\u00B0C",
                reg, white, 12);
        int rain = wx == null ? -1 : wx.rainChance(System.currentTimeMillis(), RAIN_HOURS);
        txt(ctx, rv, R.id.hdr_rain, rain < 0 ? "--%" : rain + "%", reg, white, 12);
        txt(ctx, rv, R.id.hdr_rise, rise, reg, white, 12);
        txt(ctx, rv, R.id.hdr_set, set, reg, white, 12);

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
            // the day, time and city in front of the flag stay white whatever is playing, with the lens's fringe
            txt(ctx, rv, id(ctx, "clk_day_" + i), now.format(dayF), reg, white, 11);
            rv.setImageViewBitmap(id(ctx, "clk_time_" + i), TextBmp.fringed(bold, 22 * 2.5f, 0.8f, now.format(timeF),
                    white));
            txt(ctx, rv, id(ctx, "clk_city_" + i), Util.capWords(city), reg, white, 11);
        }

        // currency rows
        String to = Store.to(ctx).trim().toUpperCase(Locale.ROOT);
        Bitmap visaLogo = visaLogo(ctx);
        Bitmap toFlag = flagOrLabel(ctx, Util.countryOfCurrency(to), to);
        JSONObject hist = Store.hist(ctx, to);
        for (int i = 0; i < k; i++) {
            String from = Store.from(ctx, i).trim().toUpperCase(Locale.ROOT);
            rv.setOnClickPendingIntent(id(ctx, "row_" + i), ConverterActivity.intentFor(ctx, i)); // tap a row: converter
            rv.setImageViewBitmap(id(ctx, "fl_a_" + i), flagOrLabel(ctx, Util.countryOfCurrency(from), from));
            txt(ctx, rv, id(ctx, "code_a_" + i), from, reg, white, 15);
            rv.setImageViewBitmap(id(ctx, "fl_b_" + i), toFlag);
            txt(ctx, rv, id(ctx, "code_b_" + i), to, reg, white, 15);
            double market = Store.rate(ctx, from, to);
            // the row's panel in the shared light, with the last month as a soft area chart over it and the row's lit
            // edge (the picture fills the row)
            double[][] trend = Trend.series(hist, from, market);
            rv.setImageViewBitmap(id(ctx, "trend_" + i), Trend.bitmap(trend, rowW, ROW_DP, scene));
            rv.setInt(id(ctx, "arrow_" + i), "setColorFilter", scene.key);
            rv.setImageViewBitmap(id(ctx, "mkt_lbl_" + i), tight(reg, CITY_SP, "Market", scene.quiet));
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

    /**
     * A small icon ({@code sizeDp} square), solid, in a darker shade of the song's line colour, with no glow, as on the
     * icon reference (its picture keeps 3 dp of room round it: its view is 6 dp bigger, with -3 dp margins).
     */
    static Bitmap icon(Context ctx, int res, int sizeDp, Scene scene) {
        String key = "i|" + res + "|" + sizeDp + "|" + scene.tag();
        Bitmap hit = PICS.get(key);
        if (hit != null) return hit;
        float px = 3f;
        int s = Math.round((sizeDp + 6) * px), in = Math.round(3 * px);
        Bitmap shape = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888);
        android.graphics.drawable.Drawable d = ctx.getDrawable(res).mutate();
        d.setBounds(in, in, s - in, s - in);
        d.setColorFilter(new android.graphics.PorterDuffColorFilter(Scene.mix(scene.line, 0xFF000000, 0.18f),
                PorterDuff.Mode.SRC_IN));
        d.draw(new Canvas(shape));
        PICS.put(key, shape);
        return shape;
    }

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

    /** The sun-phase strip's own colours, whatever is playing: day, golden hour, blue hour, night. */
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
        String key = "c|" + System.identityHashCode(flag) + "|" + phase + "|" + side + Look.get().tag();
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

    /**
     * The flag, dimmed and fading out downwards into the card's frosted glass behind it (the picture is clear where it
     * has faded), with the sun-phase band below (one exact boundary).
     */
    private static Bitmap clockFull(Bitmap flag, int phase) {
        final int w = CW, h = CH;
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        final float split = Math.round(h * 0.94f);
        RectF fr = new RectF(0, 0, w, split);
        c.saveLayer(fr, null);
        if (flag != null) {
            cover(c, flag, fr, 0);
        } else {
            Paint p = new Paint();
            p.setColor(Theme.SURFACE_HI);
            c.drawRect(fr, p);
        }
        Paint gp = new Paint();
        gp.setShader(new LinearGradient(0, 0, 0, split, 0x66000000, 0xB3000000, Shader.TileMode.CLAMP));
        c.drawRect(fr, gp); // dimmed, so the text over it reads
        gp.setShader(new LinearGradient(0, 0, 0, split, new int[]{0xF2000000, 0x99000000, 0x00000000},
                new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP));
        gp.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
        c.drawRect(fr, gp); // fading into the glass
        c.restore();
        // The sun-phase band, lit like the other lights (the look's strengths): its light rising off it into the glass
        // (a bloom that spreads further with the haze), halation's red-orange above it when the look has any, a trace
        // of the lens's fringe along its top (a line gets little: the blue copy pulled up), and a brighter core.
        int col = phaseColor(phase);
        Look look = Look.get();
        float band = h - split, mist = (float) Math.sqrt(look.haze), reach = band * 2.6f * mist;
        Paint sp = new Paint(Paint.DITHER_FLAG);
        sp.setShader(new LinearGradient(0, split, 0, split - reach, new int[]{Scene.alpha(col, Math.min(1f, 0.4f * look.bloomGain())),
                Scene.alpha(col, Math.min(1f, 0.12f * look.bloomGain() * look.haze)), col & 0x00FFFFFF},
                new float[]{0f, 0.35f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(0, split - reach, w, split, sp); // its bloom
        if (look.halationGlow() > 0f) {
            sp.setShader(new LinearGradient(0, split, 0, split - reach * 0.7f,
                    Scene.alpha(Optics.HALATION, Math.min(0.5f, 0.22f * look.halationGlow())), Optics.HALATION & 0x00FFFFFF,
                    Shader.TileMode.CLAMP));
            c.drawRect(0, split - reach * 0.7f, w, split, sp);
        }
        sp.setShader(null);
        float lift = look.fringeDp * PXDP;
        if (look.fringe > 0f) {
            sp.setColor(Scene.alpha(Lens.BLUE, look.fringe * Lens.LINES));
            c.drawRect(0, split - lift, w, h - lift, sp);
        }
        sp.setColor(col);
        c.drawRect(0, split, w, h, sp);
        sp.setColor(Scene.alpha(Scene.mix(col, 0xFFFFFFFF, 0.35f), 0.55f)); // its core, a little brighter
        c.drawRect(0, split + band * 0.3f, w, h - band * 0.3f, sp);
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
