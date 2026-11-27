package app.currencyexchange.widget;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.widget.RemoteViews;

public class WidgetProvider extends AppWidgetProvider {
    static final String ACTION_TICK = "app.currencyexchange.widget.TICK";
    private static final int JOB_PERIODIC = 1, JOB_NOW = 2;
    /** Background refresh every 15 minutes, the shortest period Android allows for a periodic job. */
    private static final long PERIOD_MS = 15L * 60_000L;
    /** Older than this and the per-minute tick asks for a refresh itself (for when the system holds the job back). */
    private static final long STALE_MS = 20L * 60_000L;
    /**
     * While the screen is on the market rates are asked for every minute (2.41): Wise's feed moves each minute, and the
     * periodic job (15 minutes at best) is often held back much longer by the system. A few seconds under a minute, so
     * the tick on the minute always finds them due.
     */
    private static final long LIVE_MS = 55_000L;
    private static final java.util.concurrent.atomic.AtomicBoolean LIVE_BUSY = new java.util.concurrent.atomic.AtomicBoolean();

    @Override
    public void onUpdate(Context ctx, AppWidgetManager awm, int[] ids) {
        tick(ctx);
    }

    @Override
    public void onAppWidgetOptionsChanged(Context ctx, AppWidgetManager awm, int id, android.os.Bundle opts) {
        updateAll(ctx);
    }

    @Override
    public void onEnabled(Context ctx) {
        requestRefresh(ctx);
    }

    @Override
    public void onDisabled(Context ctx) {
        stopIfNone(ctx);
    }

    /**
     * The last Rates or World clocks widget has gone (2.26: the two share the minute chain and the refresh): stops them
     * once neither is left on the home screen.
     */
    static void stopIfNone(Context ctx) {
        if (widgetIds(ctx).length > 0 || ClockWidgetProvider.widgetIds(ctx).length > 0) return;
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        am.cancel(tickIntent(ctx));
        JobScheduler js = (JobScheduler) ctx.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        js.cancel(JOB_PERIODIC);
        js.cancel(JOB_NOW);
        if (!LiveService.wanted(ctx)) LiveService.stop(ctx);
    }

    @Override
    public void onReceive(Context ctx, Intent intent) {
        super.onReceive(ctx, intent); // dispatches APPWIDGET_* actions
        String a = intent.getAction();
        if (ACTION_TICK.equals(a)
                || Intent.ACTION_BOOT_COMPLETED.equals(a)
                || Intent.ACTION_MY_PACKAGE_REPLACED.equals(a)
                || Intent.ACTION_TIME_CHANGED.equals(a)
                || Intent.ACTION_TIMEZONE_CHANGED.equals(a)) {
            tick(ctx);
            final PendingResult pending = goAsync(); // the process stays up while the rates come in
            ratesIfDue(ctx, pending::finish);
        }
    }

    /** Redraw from cache, queue a network refresh if stale, and arm the next per-minute redraw. */
    /** Called when the app opens: (re)starts the per-minute alarm chain right away. */
    static void kick(Context ctx) {
        tick(ctx, "app");
        ratesIfDue(ctx, null);
    }

    /**
     * The minute refresh of the market rates (2.41): with the screen on and a Rates widget placed, when the rates are a
     * minute old, the rate now for each row (a tiny request each, on a thread of its own), then the Rates widgets drawn
     * again. {@code done} (may be null) runs when it's finished or not needed.
     */
    static void ratesIfDue(Context ctx, Runnable done) {
        final Context app = ctx.getApplicationContext();
        android.os.PowerManager pm = (android.os.PowerManager) app.getSystemService(Context.POWER_SERVICE);
        boolean due = pm != null && pm.isInteractive() && widgetIds(app).length > 0
                && System.currentTimeMillis() - Store.marketAt(app) >= LIVE_MS && LIVE_BUSY.compareAndSet(false, true);
        if (!due) {
            if (done != null) done.run();
            return;
        }
        new Thread(() -> {
            try {
                if (Fetcher.refreshLive(app)) updateRates(app);
            } catch (Throwable ignored) {
            } finally {
                LIVE_BUSY.set(false);
                if (done != null) done.run();
            }
        }, "live-rates").start();
    }

    /** If the minute chain has stopped (its next alarm is overdue), restart it. Called from other widgets' alarms and jobs. */
    static void heal(Context ctx) {
        long next = Store.sp(ctx).getLong("clockNextAt", 0);
        if (System.currentTimeMillis() - next > 90_000L) tick(ctx, "restart");
    }

    private static void tick(Context ctx) { tick(ctx, "alarm"); }

    private static void tick(Context ctx, String who) {
        if (widgetIds(ctx).length == 0 && ClockWidgetProvider.widgetIds(ctx).length == 0) return;
        // Arm the next minute FIRST so a failure while drawing can never break the chain.
        try { scheduleNextTick(ctx); } catch (Exception ignored) { }
        try {
            updateAll(ctx);
            Store.diag(ctx, "clockOk", System.currentTimeMillis(), who, null);
        } catch (Throwable t) {
            Store.diag(ctx, "clockErr", System.currentTimeMillis(), who, t.getClass().getSimpleName() + ": " + t.getMessage());
        }
        LiveService.ensure(ctx);
        try {
            ensurePeriodicJob(ctx);
            if (System.currentTimeMillis() - Store.lastFetch(ctx) > STALE_MS) requestRefresh(ctx);
        } catch (Exception ignored) { }
    }

    static int[] widgetIds(Context ctx) {
        return AppWidgetManager.getInstance(ctx).getAppWidgetIds(new ComponentName(ctx, WidgetProvider.class));
    }

    /** Draws every Rates widget, and (2.26) every World clocks widget, afresh. */
    static void updateAll(Context ctx) {
        ClockWidgetProvider.updateAll(ctx);
        updateRates(ctx);
    }

    /** Draws every Rates widget afresh. */
    static void updateRates(Context ctx) {
        AppWidgetManager awm = AppWidgetManager.getInstance(ctx);
        int[] ids = widgetIds(ctx);
        if (ids.length == 0) return;
        PendingIntent open = PendingIntent.getActivity(ctx, 0, new Intent(ctx, MainActivity.class),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        int max = Math.min(CurrencyView.LAYOUTS.length, Store.count(ctx));
        boolean land = ctx.getResources().getConfiguration().orientation
                == android.content.res.Configuration.ORIENTATION_LANDSCAPE;
        for (int id : ids) {
            RemoteViews rv;
            android.os.Bundle o = awm.getAppWidgetOptions(id);
            int wdp = o.getInt(land ? AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH
                    : AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0);
            // Android 12 and up: a size map, so the launcher shows the variant whose rows fit (no live frames go to the
            // Rates widget since 2.26, so it can always have one)
            if (Build.VERSION.SDK_INT >= 31) {
                // The launcher shows the variant whose number of rows fits the real height, and re-picks on resize.
                java.util.Map<android.util.SizeF, RemoteViews> map = new java.util.LinkedHashMap<>();
                for (int k = 1; k <= max; k++) {
                    RemoteViews v = CurrencyView.build(ctx, k, wdp);
                    v.setOnClickPendingIntent(R.id.root, open);
                    map.put(new android.util.SizeF(110f, CurrencyView.heightFor(k)), v);
                }
                rv = new RemoteViews(map);
            } else {
                rv = CurrencyView.build(ctx, variant(ctx, awm, id), wdp);
                rv.setOnClickPendingIntent(R.id.root, open);
            }
            awm.updateAppWidget(id, rv);
        }
    }

    /** How many currency rows widget {@code id} shows: as many as its height fits (and as are set up). */
    static int variant(Context ctx, AppWidgetManager awm, int id) {
        boolean land = ctx.getResources().getConfiguration().orientation
                == android.content.res.Configuration.ORIENTATION_LANDSCAPE;
        android.os.Bundle o = awm.getAppWidgetOptions(id);
        int hdp = o == null ? 400 : o.getInt(land ? AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT
                : AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 400);
        int max = Math.min(CurrencyView.LAYOUTS.length, Store.count(ctx)), k = 1;
        for (int i = 1; i <= max; i++) if (CurrencyView.heightFor(i) <= hdp) k = i;
        return k;
    }

    /** Widget {@code id}'s width in dp, as the cards' pictures are drawn for it (360 when the launcher doesn't say). */
    static int widthDp(Context ctx, AppWidgetManager awm, int id) {
        boolean land = ctx.getResources().getConfiguration().orientation
                == android.content.res.Configuration.ORIENTATION_LANDSCAPE;
        android.os.Bundle o = awm.getAppWidgetOptions(id);
        int w = o == null ? 0 : o.getInt(land ? AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH
                : AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0);
        return w >= 120 && w <= 1200 ? w : 360;
    }

    private static PendingIntent tickIntent(Context ctx) {
        Intent i = new Intent(ctx, WidgetProvider.class).setAction(ACTION_TICK);
        return PendingIntent.getBroadcast(ctx, 0, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private static void scheduleNextTick(Context ctx) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        long next = (System.currentTimeMillis() / 60_000L + 1) * 60_000L + 100L; // right as the minute turns
        PendingIntent pi = tickIntent(ctx);
        String how;
        try {
            if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC, next, pi); // on the dot, even in battery saver
                how = "exact";
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC, next, pi);
                how = "inexact (exact alarms not allowed)";
            }
        } catch (SecurityException e) {
            am.setAndAllowWhileIdle(AlarmManager.RTC, next, pi);
            how = "inexact (" + e.getMessage() + ")";
        }
        Store.diag(ctx, "clockNext", next, how, null);
    }

    private static void ensurePeriodicJob(Context ctx) {
        JobScheduler js = (JobScheduler) ctx.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        JobInfo pending = js.getPendingJob(JOB_PERIODIC);
        if (pending != null && pending.getIntervalMillis() == PERIOD_MS) return;
        // none yet, or one from an older version with another period: (re)schedule, which replaces it
        js.schedule(new JobInfo.Builder(JOB_PERIODIC, new ComponentName(ctx, RefreshJobService.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPeriodic(PERIOD_MS)
                .setPersisted(true)
                .build());
    }

    /** One-off refresh as soon as the network is available. */
    static void requestRefresh(Context ctx) {
        JobScheduler js = (JobScheduler) ctx.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        js.schedule(new JobInfo.Builder(JOB_NOW, new ComponentName(ctx, RefreshJobService.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .build());
    }
}
