package app.currencyexchange.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;

/**
 * The World clocks widget (2.26): its clocks on their cities' skies ({@link ClockView}), as many as are set in the
 * app (one to four in a row, or six or eight in two rows). The minute ticks, the background refresh and the skies'
 * motion are shared with the Rates widget ({@link WidgetProvider}, {@link LiveService}): whichever of the two is on
 * the home screen keeps them going.
 */
public class ClockWidgetProvider extends AppWidgetProvider {
    @Override
    public void onUpdate(Context ctx, AppWidgetManager awm, int[] ids) {
        WidgetProvider.kick(ctx); // draws both widgets and keeps the minute chain going
    }

    @Override
    public void onAppWidgetOptionsChanged(Context ctx, AppWidgetManager awm, int id, android.os.Bundle opts) {
        updateAll(ctx);
    }

    @Override
    public void onEnabled(Context ctx) {
        WidgetProvider.requestRefresh(ctx);
        WidgetProvider.kick(ctx);
        LiveService.ensure(ctx);
    }

    @Override
    public void onDisabled(Context ctx) {
        WidgetProvider.stopIfNone(ctx);
    }

    static int[] widgetIds(Context ctx) {
        return AppWidgetManager.getInstance(ctx).getAppWidgetIds(new ComponentName(ctx, ClockWidgetProvider.class));
    }

    /** Draws every World clocks widget afresh (once a minute, and when anything it shows changes). */
    static void updateAll(Context ctx) {
        AppWidgetManager awm = AppWidgetManager.getInstance(ctx);
        int[] ids = widgetIds(ctx);
        if (ids.length == 0) return;
        PendingIntent open = PendingIntent.getActivity(ctx, 0, new Intent(ctx, MainActivity.class),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        int n = Store.clockCount(ctx);
        for (int id : ids) {
            android.widget.RemoteViews rv = ClockView.build(ctx, n, widthDp(ctx, awm, id), heightDp(ctx, awm, id));
            rv.setOnClickPendingIntent(R.id.root, open);
            awm.updateAppWidget(id, rv);
        }
    }

    private static boolean land(Context ctx) {
        return ctx.getResources().getConfiguration().orientation
                == android.content.res.Configuration.ORIENTATION_LANDSCAPE;
    }

    /** Widget {@code id}'s width in dp, as the launcher reports it (0 when it doesn't). */
    static int widthDp(Context ctx, AppWidgetManager awm, int id) {
        android.os.Bundle o = awm.getAppWidgetOptions(id);
        return o == null ? 0 : o.getInt(land(ctx) ? AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH
                : AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0);
    }

    /** Widget {@code id}'s height in dp, as the launcher reports it (0 when it doesn't). */
    static int heightDp(Context ctx, AppWidgetManager awm, int id) {
        android.os.Bundle o = awm.getAppWidgetOptions(id);
        return o == null ? 0 : o.getInt(land(ctx) ? AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT
                : AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0);
    }
}
