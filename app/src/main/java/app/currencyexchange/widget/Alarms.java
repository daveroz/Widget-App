package app.currencyexchange.widget;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.os.Build;

/** One place for "wake me at this time" so every widget arms alarms the same way. */
final class Alarms {
    private Alarms() {}

    /** Exact (works in battery saver) when allowed, otherwise the closest the system lets us get. */
    static void at(Context ctx, long when, PendingIntent pi) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        try {
            if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC, when, pi);
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC, when, pi);
            }
        } catch (SecurityException e) {
            am.setAndAllowWhileIdle(AlarmManager.RTC, when, pi);
        }
    }

    static void cancel(Context ctx, PendingIntent pi) {
        ((AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE)).cancel(pi);
    }
}
