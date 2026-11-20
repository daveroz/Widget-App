package app.currencyexchange.widget;

import android.app.Activity;
import android.app.ActivityOptions;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;

/**
 * A tap on a widget passes through here for an instant, unseen: the launcher says where on the screen the widget was
 * tapped (its whole frame, for these taps), which is how the widget's frosted glass lines up with the wallpaper behind
 * it ({@link Backdrop}); then what the tap always opened opens (the calendar, the playing app, Worldly's settings).
 */
public class TapActivity extends Activity {
    private static final String EXTRA_WIDGET = "widget", EXTRA_THEN = "then";

    /** A tap on the whole frame of {@code widget} that opens {@code then} (another app's, or Worldly's own). */
    static PendingIntent wrap(Context ctx, int code, String widget, PendingIntent then) {
        Intent i = new Intent(ctx, TapActivity.class).putExtra(EXTRA_WIDGET, widget).putExtra(EXTRA_THEN, then)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION);
        return PendingIntent.getActivity(ctx, code, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Intent in = getIntent();
        Rect bounds = in.getSourceBounds();
        String widget = in.getStringExtra(EXTRA_WIDGET);
        if (widget != null && bounds != null) Backdrop.saw(this, widget, bounds);
        PendingIntent then = in.getParcelableExtra(EXTRA_THEN);
        if (then != null) {
            try {
                if (Build.VERSION.SDK_INT >= 34) { // it may start an activity: this one is in front, so it can
                    ActivityOptions o = ActivityOptions.makeBasic();
                    o.setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED);
                    then.send(this, 0, null, null, null, null, o.toBundle());
                } else {
                    then.send();
                }
            } catch (PendingIntent.CanceledException ignored) { }
        }
        finish();
        overridePendingTransition(0, 0);
    }
}
