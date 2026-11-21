package app.currencyexchange.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.RemoteViews;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/** Week strip + next-event card. Reads the device calendar; refreshes every 15 minutes and at midnight. */
public class CalendarWidgetProvider extends AppWidgetProvider {
    static final String ACTION_TICK = "app.currencyexchange.widget.CAL_TICK";

    @Override
    public void onUpdate(Context ctx, AppWidgetManager awm, int[] ids) {
        tick(ctx);
    }

    @Override
    public void onEnabled(Context ctx) {
        tick(ctx);
    }

    @Override
    public void onAppWidgetOptionsChanged(Context ctx, AppWidgetManager awm, int id, Bundle opts) {
        updateAll(ctx);
    }

    @Override
    public void onDisabled(Context ctx) {
        Alarms.cancel(ctx, tickIntent(ctx));
        if (!LiveService.wanted(ctx)) LiveService.stop(ctx);
    }

    @Override
    public void onReceive(Context ctx, Intent intent) {
        super.onReceive(ctx, intent);
        String a = intent.getAction();
        if (ACTION_TICK.equals(a)
                || Intent.ACTION_BOOT_COMPLETED.equals(a)
                || Intent.ACTION_MY_PACKAGE_REPLACED.equals(a)
                || Intent.ACTION_TIME_CHANGED.equals(a)
                || Intent.ACTION_TIMEZONE_CHANGED.equals(a)
                || Intent.ACTION_DATE_CHANGED.equals(a)) {
            tick(ctx);
        }
    }

    static void tick(Context ctx) {
        try { WidgetProvider.heal(ctx); } catch (Throwable ignored) { }
        if (widgetIds(ctx).length == 0) return;
        try {
            long now = System.currentTimeMillis();
            long midnight = LocalDate.now().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() + 2000L;
            Alarms.at(ctx, Math.min(now + 15L * 60_000L, midnight), tickIntent(ctx));
        } catch (Exception ignored) { }
        try { updateAll(ctx); } catch (Throwable ignored) { }
        LiveService.ensure(ctx);
    }

    static int[] widgetIds(Context ctx) {
        return AppWidgetManager.getInstance(ctx).getAppWidgetIds(new ComponentName(ctx, CalendarWidgetProvider.class));
    }

    private static final int[] LAYOUTS = {R.layout.cal_k0, R.layout.cal_k1, R.layout.cal_k2, R.layout.cal_k3,
            R.layout.cal_k4, R.layout.cal_k5, R.layout.cal_k6, R.layout.cal_k7, R.layout.cal_k8};

    /** Height (dp) from which the variant with k event rows fits. */
    private static int heightFor(int k) { return k == 0 ? 114 : 136 + 45 * k; }

    /**
     * The week row's day numbers fill this share of their picture's height: the pictures show in 30 dp boxes, so the
     * numbers stay the size they were in the old 26 dp boxes.
     */
    private static final float NUMBER_FILL = 0.58f * 26f / 30f;

    static void updateAll(Context ctx) {
        AppWidgetManager awm = AppWidgetManager.getInstance(ctx);
        int[] ids = widgetIds(ctx);
        if (ids.length == 0) return;
        boolean allowed = CalendarData.allowed(ctx);
        List<CalendarData.Event> events = CalendarData.upcoming(ctx);

        PendingIntent settings = PendingIntent.getActivity(ctx, 20, new Intent(ctx, MainActivity.class),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent calendar = settings;
        PendingIntent add = settings;
        if (allowed) {
            calendar = settings;
            Intent ci = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALENDAR);
            if (ci.resolveActivity(ctx.getPackageManager()) != null) {
                calendar = PendingIntent.getActivity(ctx, 21, ci, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            }
            add = calendar;
            Intent ai = new Intent(Intent.ACTION_INSERT).setData(android.provider.CalendarContract.Events.CONTENT_URI);
            if (ai.resolveActivity(ctx.getPackageManager()) != null) {
                add = PendingIntent.getActivity(ctx, 25, ai, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            }
        }
        PendingIntent open = allowed ? calendar : settings;
        PendingIntent addPi = allowed ? add : settings;

        for (int id : ids) {
            RemoteViews rv;
            android.graphics.Bitmap bg = background(ctx, awm, id, heightFor(2)); // one picture shared by every size variant
            if (android.os.Build.VERSION.SDK_INT >= 31) {
                // The launcher picks the variant that fits the real widget size and re-picks when it is resized.
                java.util.Map<android.util.SizeF, RemoteViews> map = new java.util.LinkedHashMap<>();
                for (int k = 0; k < LAYOUTS.length; k++) {
                    map.put(new android.util.SizeF(110f, heightFor(k)), build(ctx, k, events, allowed, open, addPi, bg));
                }
                rv = new RemoteViews(map);
            } else {
                Bundle o = awm.getAppWidgetOptions(id);
                boolean land = ctx.getResources().getConfiguration().orientation
                        == android.content.res.Configuration.ORIENTATION_LANDSCAPE;
                int hdp = o.getInt(land ? AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT
                        : AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 208);
                int k = 0;
                for (int i = 0; i < LAYOUTS.length; i++) if (heightFor(i) <= hdp) k = i;
                rv = build(ctx, k, events, allowed, open, addPi, bg);
            }
            awm.updateAppWidget(id, rv);
        }
    }

    /**
     * Text as a picture in the original Carbon+ font; sizeDp is the intended font size. In its own colour, with the
     * lens's colour fringe (no glow).
     */
    private static void t(RemoteViews rv, android.graphics.Typeface tf, int viewId, String s, int color,
                          float sizeDp, float fill, boolean italic) {
        rv.setImageViewBitmap(viewId, TextBmp.fringed(tf, sizeDp * 2.5f, fill, italic, s, color));
    }

    private static int rid(Context ctx, String name) {
        return ctx.getResources().getIdentifier(name, "id", ctx.getPackageName());
    }

    private static RemoteViews build(Context ctx, int k, List<CalendarData.Event> events, boolean allowed,
                                     PendingIntent open, PendingIntent add, android.graphics.Bitmap bg) {
        RemoteViews rv = new RemoteViews(ctx.getPackageName(), LAYOUTS[k]);
        // The light all the widgets share (the playing song's colour, or amber): the panel's washes, today, the accents.
        Scene scene = Ambience.scene();
        if (bg != null) rv.setImageViewBitmap(R.id.cal_bg, bg);
        android.graphics.Typeface tf = Theme.uiMedium(ctx);
        LocalDate today = LocalDate.now();
        long now = System.currentTimeMillis();
        java.util.Locale loc = java.util.Locale.getDefault();

        // The text is in its own colours, with the lens's colour fringe; only today and "in 5 minutes" are in the light. Labels (what something is, when it is) are in the quiet colour,
        // tinted by the song; the divider and the "+" are in its line colour.
        int white = Theme.TEXT, soft = Theme.TEXT_SOFT, muted = scene.quiet, label = scene.label;
        // the day's number, cropped to its digits, its view as tall as from the top of the month's capitals to the
        // bottom of the line under it (the layouts place it there)
        rv.setImageViewBitmap(R.id.cal_day, TextBmp.ink(tf, 48 * 2.5f, String.valueOf(today.getDayOfMonth()), white));
        rv.setInt(R.id.cal_div, "setColorFilter", scene.line);
        rv.setImageViewBitmap(R.id.btn_add, addButton(scene));
        t(rv, tf, R.id.cal_month, today.format(java.time.format.DateTimeFormatter.ofPattern("MMMM", loc)).toUpperCase(loc), white, 16, 0.82f, false);
        int week = today.get(java.time.temporal.WeekFields.of(java.time.DayOfWeek.SUNDAY, 1).weekOfYear());
        t(rv, tf, R.id.cal_sub, "Today, " + today.format(java.time.format.DateTimeFormatter.ofPattern("EEEE", loc))
                + "  \u00B7  Week " + week, muted, 11, 0.8f, false);

        // week row, Sunday first; today sits on a solid tile in the song's colour, covering its day name and number
        LocalDate sunday = today.minusDays(today.getDayOfWeek().getValue() % 7);
        int onTile = Theme.onLit(scene.key);
        for (int i = 0; i < 7; i++) {
            LocalDate d = sunday.plusDays(i);
            boolean isToday = d.equals(today);
            int col = rid(ctx, "wk_col_" + i), lab = rid(ctx, "wk_label_" + i), num = rid(ctx, "wk_num_" + i);
            String name = d.format(java.time.format.DateTimeFormatter.ofPattern("EEE", loc)).toUpperCase(loc);
            String day = String.valueOf(d.getDayOfMonth());
            if (isToday) { // solid, like a button; its text has the fringe all text has
                rv.setInt(col, "setBackgroundResource", R.drawable.cal_today);
                if (android.os.Build.VERSION.SDK_INT >= 31) {
                    rv.setColorStateList(col, "setBackgroundTintList", android.content.res.ColorStateList.valueOf(scene.key));
                }
                rv.setImageViewBitmap(lab, TextBmp.fringed(Theme.uiBold(ctx), 9.5f * 2.5f, 0.8f, name, onTile));
                rv.setImageViewBitmap(num, TextBmp.fringed(tf, 13 * 2.5f, NUMBER_FILL, day, onTile));
            } else {
                rv.setInt(col, "setBackgroundResource", 0);
                t(rv, tf, lab, name, muted, 9.5f, 0.8f, false);
                t(rv, tf, num, day, soft, 13, NUMBER_FILL, false);
            }
        }

        // events, laid out as on the reference: each day's events on tiles, the day (its weekday over its number)
        // beside its first one; and when nothing is on today, a tile of its own saying so, the day on it too
        java.time.format.DateTimeFormatter tfmt = java.time.format.DateTimeFormatter.ofLocalizedTime(java.time.format.FormatStyle.SHORT);
        java.util.List<Object> lines = new java.util.ArrayList<>(); // a CalendarData.Event, or a String[] {title, detail}
        if (!allowed) {
            lines.add(new String[]{"Calendar access needed", "Tap to open settings"});
        } else {
            boolean todayHasEvents = !events.isEmpty() && events.get(0).date.equals(today);
            if (!todayHasEvents) lines.add(new String[]{"Nothing scheduled today", events.isEmpty() ? "Nothing in the next 120 days" : ""});
            lines.addAll(events);
        }
        LocalDate shownDay = null;
        for (int i = 0; i < k; i++) {
            int row = rid(ctx, "cal_row_" + i), line = rid(ctx, "cal_line_" + i), dayCol = rid(ctx, "cal_day_" + i),
                    dname = rid(ctx, "cal_dname_" + i), dnum = rid(ctx, "cal_dnum_" + i), tile = rid(ctx, "cal_tile_" + i),
                    bar = rid(ctx, "cal_bar_" + i), title = rid(ctx, "cal_title_" + i), time = rid(ctx, "cal_time_" + i),
                    hint = rid(ctx, "cal_hint_" + i);
            if (i >= lines.size()) { rv.setViewVisibility(row, android.view.View.GONE); continue; }
            rv.setViewVisibility(row, android.view.View.VISIBLE);
            Object item = lines.get(i);
            LocalDate day = item instanceof CalendarData.Event ? ((CalendarData.Event) item).date : today;
            // the day, beside its first line only; today's weekday in the light
            if (!day.equals(shownDay)) {
                shownDay = day;
                rv.setViewVisibility(dayCol, android.view.View.VISIBLE);
                boolean isToday = day.equals(today);
                t(rv, tf, dname, day.format(java.time.format.DateTimeFormatter.ofPattern("EEE", loc)).toUpperCase(loc),
                        isToday ? label : muted, 9.5f, 0.8f, false);
                t(rv, tf, dnum, String.valueOf(day.getDayOfMonth()), isToday ? white : soft, 15, 0.8f, false);
            } else {
                rv.setViewVisibility(dayCol, android.view.View.INVISIBLE);
            }
            if (item instanceof String[]) { // a day with nothing on (or no access): one tile, the day on it too
                String[] say = (String[]) item;
                rv.setInt(line, "setBackgroundResource", R.drawable.cal_event);
                rv.setInt(tile, "setBackgroundResource", 0);
                rv.setViewVisibility(bar, android.view.View.GONE);
                t(rv, tf, title, say[0], white, 15, 0.8f, false);
                t(rv, tf, time, say[1], muted, 11, 0.8f, false);
                t(rv, tf, hint, "", muted, 11, 0.8f, false);
                int under = say[1].isEmpty() ? android.view.View.GONE : android.view.View.VISIBLE; // one line: centred
                rv.setViewVisibility(time, under);
                rv.setViewVisibility(hint, under);
                continue;
            }
            CalendarData.Event ev = (CalendarData.Event) item;
            rv.setViewVisibility(bar, android.view.View.VISIBLE);
            rv.setInt(bar, "setColorFilter", ev.color != 0 ? (0xFF000000 | ev.color) : scene.key);
            String tm;
            if (ev.allDay) tm = "All day";
            else {
                java.time.ZoneId z = java.time.ZoneId.systemDefault();
                tm = java.time.Instant.ofEpochMilli(ev.begin).atZone(z).format(tfmt) + " - "
                        + java.time.Instant.ofEpochMilli(ev.end).atZone(z).format(tfmt);
            }
            t(rv, tf, title, Util.ellipsize(Util.capWords(ev.title), 30), white, 15, 0.8f, false);
            t(rv, tf, time, tm, muted, 11, 0.8f, false);
            String h = "";
            if (!ev.allDay && ev.date.equals(today)) {
                if (ev.begin <= now) h = "now";
                else {
                    long mins = (ev.begin - now + 59_999L) / 60_000L;
                    if (mins <= 90) h = "in " + mins + (mins == 1 ? " minute" : " minutes");
                }
            }
            t(rv, tf, hint, h, label, 11, 0.8f, true);
        }
        int hidden = allowed ? lines.size() - k : 0; // events that didn't fit
        if (k > 0 && hidden > 0) {
            rv.setViewVisibility(R.id.cal_more, android.view.View.VISIBLE);
            t(rv, tf, R.id.cal_more, "+" + hidden + " more", muted, 9.5f, 0.8f, false);
        } else {
            rv.setViewVisibility(R.id.cal_more, android.view.View.GONE);
        }
        rv.setOnClickPendingIntent(R.id.panel, open);
        rv.setOnClickPendingIntent(R.id.btn_add, add);
        return rv;
    }

    /** The "+": a round icon button, as on the icon reference: a dark solid circle with a pale plus. */
    private static android.graphics.Bitmap addButton(Scene scene) {
        float px = 3f;
        int s = Math.round(44 * px);
        android.graphics.Bitmap b = android.graphics.Bitmap.createBitmap(s, s, android.graphics.Bitmap.Config.ARGB_8888);
        android.graphics.Canvas c = new android.graphics.Canvas(b);
        // as the app's dark buttons: near black with a glass rim
        Theme.glassCircle(c, s / 2f, s / 2f, 19 * px, 1.2f * px, Theme.darkFill(scene), Theme.darkEdge(scene), Theme.DARK_RIM);
        android.graphics.Paint p = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        p.setStyle(android.graphics.Paint.Style.STROKE);
        p.setStrokeWidth(2.2f * px);
        p.setStrokeCap(android.graphics.Paint.Cap.ROUND);
        p.setColor(Theme.iconGlyph(scene));
        c.drawLine(15 * px, 22 * px, 29 * px, 22 * px, p);
        c.drawLine(22 * px, 15 * px, 22 * px, 29 * px, p);
        return b;
    }

    /** The widget's card picture (see {@link Stage#panel}), at its real size on the home screen. */
    private static android.graphics.Bitmap background(Context ctx, AppWidgetManager awm, int id, int fallbackHeightDp) {
        Bundle o = awm.getAppWidgetOptions(id);
        boolean land = ctx.getResources().getConfiguration().orientation
                == android.content.res.Configuration.ORIENTATION_LANDSCAPE;
        int w = o == null ? 0 : o.getInt(land ? AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH : AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH);
        int h = o == null ? 0 : o.getInt(land ? AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT : AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT);
        if (w <= 0) w = 320;
        if (h <= 0) h = fallbackHeightDp;
        // today's tile lights the glass round it: in its column of the week row (20 dp side padding, seven columns),
        // its middle 87 dp down (13 dp padding, the 48 dp header, 2 dp, half the 48 dp week row)
        Scene scene = Ambience.scene();
        int col = LocalDate.now().getDayOfWeek().getValue() % 7;
        Stage.Spill today = new Stage.Spill(20 + (col + 0.5f) * (w - 40) / 7f, 87, 40, scene.key, 0.26f * Look.get().bloom);
        return Stage.panel(scene, w, h, 1.25f, Stage.round(28), new Stage.Spill[]{today}, true);
    }

    private static PendingIntent broadcast(Context ctx, String action, int code) {
        Intent i = new Intent(ctx, CalendarWidgetProvider.class).setAction(action);
        return PendingIntent.getBroadcast(ctx, code, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private static PendingIntent tickIntent(Context ctx) {
        return broadcast(ctx, ACTION_TICK, 24);
    }
}
