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
    private static final long STALE_MS = 40L * 60_000L;

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
        }
    }

    /** Redraw from cache, queue a network refresh if stale, and arm the next per-minute redraw. */
    /** Called when the app opens: (re)starts the per-minute alarm chain right away. */
    static void kick(Context ctx) { tick(ctx, "app"); }

    /** If the minute chain has stopped (its next alarm is overdue), restart it. Called from other widgets' alarms and jobs. */
    static void heal(Context ctx) {
        long next = Store.sp(ctx).getLong("clockNextAt", 0);
        if (System.currentTimeMillis() - next > 90_000L) tick(ctx, "restart");
    }

    private static void tick(Context ctx) { tick(ctx, "alarm"); }

    private static void tick(Context ctx, String who) {
        if (widgetIds(ctx).length == 0) return;
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

    static void updateAll(Context ctx) {
        AppWidgetManager awm = AppWidgetManager.getInstance(ctx);
        int[] ids = widgetIds(ctx);
        if (ids.length == 0) return;
        PendingIntent open = PendingIntent.getActivity(ctx, 0, new Intent(ctx, MainActivity.class),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        int max = Math.min(CurrencyView.LAYOUTS.length, Store.count(ctx));
        for (int id : ids) {
            RemoteViews rv;
            if (Build.VERSION.SDK_INT >= 31) {
                // The launcher shows the variant whose number of rows fits the real height, and re-picks on resize.
                java.util.Map<android.util.SizeF, RemoteViews> map = new java.util.LinkedHashMap<>();
                for (int k = 1; k <= max; k++) {
                    RemoteViews v = CurrencyView.build(ctx, k);
                    v.setOnClickPendingIntent(R.id.root, open);
                    map.put(new android.util.SizeF(110f, CurrencyView.heightFor(k)), v);
                }
                rv = new RemoteViews(map);
            } else {
                android.os.Bundle o = awm.getAppWidgetOptions(id);
                boolean land = ctx.getResources().getConfiguration().orientation
                        == android.content.res.Configuration.ORIENTATION_LANDSCAPE;
                int hdp = o.getInt(land ? AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT
                        : AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 400);
                int k = 1;
                for (int i = 1; i <= max; i++) if (CurrencyView.heightFor(i) <= hdp) k = i;
                rv = CurrencyView.build(ctx, k);
                rv.setOnClickPendingIntent(R.id.root, open);
            }
            awm.updateAppWidget(id, rv);
        }
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
        if (js.getPendingJob(JOB_PERIODIC) != null) return;
        js.schedule(new JobInfo.Builder(JOB_PERIODIC, new ComponentName(ctx, RefreshJobService.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPeriodic(30L * 60_000L)
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
