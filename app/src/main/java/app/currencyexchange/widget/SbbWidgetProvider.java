package app.currencyexchange.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.widget.RemoteViews;

import java.time.ZoneId;

/** The Swiss railway clock widget. Per-minute alarm chain for the steps, {@link LiveService} for the live second hand. */
public class SbbWidgetProvider extends AppWidgetProvider {
    static final String ACTION_TICK = "app.currencyexchange.widget.SBB_TICK";

    @Override
    public void onUpdate(Context ctx, AppWidgetManager awm, int[] ids) {
        tick(ctx);
    }

    @Override
    public void onEnabled(Context ctx) {
        tick(ctx);
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
                || Intent.ACTION_TIMEZONE_CHANGED.equals(a)) {
            tick(ctx);
        }
    }

    static void tick(Context ctx) {
        try { WidgetProvider.heal(ctx); } catch (Throwable ignored) { }
        if (widgetIds(ctx).length == 0) return;
        try { // arm the next minute step first so a drawing failure can never break the chain
            Alarms.at(ctx, (System.currentTimeMillis() / 60_000L + 1) * 60_000L + 150L, tickIntent(ctx));
        } catch (Exception ignored) { }
        try { updateAll(ctx); } catch (Throwable ignored) { }
        LiveService.ensure(ctx);
    }

    static int[] widgetIds(Context ctx) {
        return AppWidgetManager.getInstance(ctx).getAppWidgetIds(new ComponentName(ctx, SbbWidgetProvider.class));
    }

    /** The clock always shows the time where you are (the device's current time zone). */
    static ZoneId zone(Context ctx) {
        return ZoneId.systemDefault();
    }

    /** Full update: picture + tap action. */
    static void updateAll(Context ctx) {
        int[] ids = widgetIds(ctx);
        if (ids.length == 0) return;
        Bitmap bmp = SbbRenderer.render(System.currentTimeMillis(), zone(ctx), SbbRenderer.SIZE, null, false);
        RemoteViews rv = new RemoteViews(ctx.getPackageName(), R.layout.widget_sbb);
        rv.setImageViewBitmap(R.id.img, bmp);
        PendingIntent open = PendingIntent.getActivity(ctx, 10, new Intent(ctx, MainActivity.class),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        rv.setOnClickPendingIntent(R.id.root, open);
        AppWidgetManager.getInstance(ctx).updateAppWidget(ids, rv);
    }

    /** Cheap picture-only update used by the live second hand. */
    static void pushFrame(Context ctx, int[] ids, Bitmap bmp) {
        RemoteViews rv = new RemoteViews(ctx.getPackageName(), R.layout.widget_sbb);
        rv.setImageViewBitmap(R.id.img, bmp);
        AppWidgetManager.getInstance(ctx).partiallyUpdateAppWidget(ids, rv);
    }

    private static PendingIntent tickIntent(Context ctx) {
        Intent i = new Intent(ctx, SbbWidgetProvider.class).setAction(ACTION_TICK);
        return PendingIntent.getBroadcast(ctx, 11, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }
}
