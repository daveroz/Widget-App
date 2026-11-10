package app.currencyexchange.widget;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.Context;

/** Background network refresh (system-scheduled, battery-friendly). */
public class RefreshJobService extends JobService {
    @Override
    public boolean onStartJob(final JobParameters params) {
        final Context ctx = getApplicationContext();
        new Thread(() -> {
            try {
                Fetcher.refreshAll(ctx);
                WidgetProvider.updateAll(ctx);
                WidgetProvider.heal(ctx);
                if (Fetcher.refreshHistory(ctx)) WidgetProvider.updateAll(ctx); // trend charts, after the rates
            } catch (Throwable ignored) {
            } finally {
                jobFinished(params, false);
            }
        }, "currency-widget-refresh").start();
        return true; // work continues on the thread
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return true; // retry later if the system interrupts us
    }
}
