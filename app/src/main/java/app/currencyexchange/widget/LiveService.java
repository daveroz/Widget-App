package app.currencyexchange.widget;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.PowerManager;

import java.time.ZoneId;

/**
 * Keeps all widgets live while the screen is on, independent of alarms (which some phones delay):
 *  - on every screen-on and at every minute boundary it repaints the clocks (Currency + Calendar + SBB),
 *  - for the SBB clock it also pushes frames for the second hand (about 10 a second, or 1 a second).
 * With the screen off it does nothing at all, so there is no battery cost; the alarm chains stay as a backup.
 */
public class LiveService extends Service {
    private static final String CHANNEL = "widgets_live";
    private static final int NOTIF_ID = 42;

    private HandlerThread thread;
    private Handler handler;
    private BroadcastReceiver screen;
    private Bitmap buf;
    private boolean on;

    // ------------------------------------------------------------------ control

    static boolean wanted(Context ctx) {
        // Only the moving SBB second hand needs a running service (Android requires a notification for that).
        // World clocks and the calendar run on exact alarms alone, with no notification.
        return SbbWidgetProvider.widgetIds(ctx).length > 0 && !"minute".equals(Store.sbbMode(ctx));
    }

    static void ensure(Context ctx) {
        if (!wanted(ctx)) {
            stop(ctx);
            return;
        }
        try {
            Intent i = new Intent(ctx, LiveService.class);
            if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i);
            else ctx.startService(i);
        } catch (Exception ignored) {
            // A background start can be refused; the alarm chains still run and opening the app restarts this.
        }
    }

    static void stop(Context ctx) {
        try { ctx.stopService(new Intent(ctx, LiveService.class)); } catch (Exception ignored) { }
    }

    // ------------------------------------------------------------------ service

    @Override
    public void onCreate() {
        super.onCreate();
        startAsForeground();
        thread = new HandlerThread("widgets-live");
        thread.start();
        handler = new Handler(thread.getLooper());
        screen = new BroadcastReceiver() {
            @Override
            public void onReceive(Context c, Intent i) {
                String a = i.getAction();
                if (Intent.ACTION_TIME_TICK.equals(a)) {
                    // the system's own minute tick: the most reliable trigger for the clocks
                    if (on) handler.post(() -> minuteWork(getApplicationContext(), "tick"));
                } else if (Intent.ACTION_SCREEN_ON.equals(a)) start();
                else stopLoops();
            }
        };
        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_SCREEN_ON);
        f.addAction(Intent.ACTION_SCREEN_OFF);
        f.addAction(Intent.ACTION_TIME_TICK);
        registerReceiver(screen, f);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startAsForeground(); // every startForegroundService call must be answered
        if (!wanted(this)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (pm.isInteractive()) start();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        stopLoops();
        try { unregisterReceiver(screen); } catch (Exception ignored) { }
        if (thread != null) thread.quitSafely();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    private void startAsForeground() {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, "Keeping widgets live", NotificationManager.IMPORTANCE_MIN);
            ch.setShowBadge(false);
            nm.createNotificationChannel(ch);
        }
        PendingIntent open = PendingIntent.getActivity(this, 12, new Intent(this, MainActivity.class),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification n = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_clock)
                .setContentTitle("Widgets running")
                .setContentText("Updates the clocks every minute while the screen is on")
                .setContentIntent(open)
                .setOngoing(true)
                .build();
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTIF_ID, n);
        }
    }

    // ------------------------------------------------------------------ loops

    private void start() {
        if (on) return;
        on = true;
        handler.post(minuteLoop);
        handler.post(frameLoop);
    }

    private void stopLoops() {
        on = false;
        if (handler != null) {
            handler.removeCallbacks(minuteLoop);
            handler.removeCallbacks(frameLoop);
        }
    }

    /** Immediately, then at every minute boundary: repaint Currency + Calendar (+ a still SBB picture). */
    private final Runnable minuteLoop = new Runnable() {
        @Override
        public void run() {
            if (!on) return;
            Context ctx = getApplicationContext();
            if (!wanted(ctx)) {
                stopSelf();
                return;
            }
            minuteWork(ctx, "service");
            long now = System.currentTimeMillis();
            long next = (now / 60_000L + 1) * 60_000L + 100L;
            handler.postDelayed(this, Math.max(1000L, next - now));
        }
    };

    private static long lastWork;

    /** Repaint Currency + Calendar (+ a still SBB picture); runs at most once per minute. */
    static synchronized void minuteWork(Context ctx, String who) {
        long now = System.currentTimeMillis();
        if (now / 60_000L == lastWork / 60_000L) return; // already done this minute
        lastWork = now;
        try {
            WidgetProvider.updateAll(ctx);
            Store.diag(ctx, "clockOk", now, who, null);
        } catch (Throwable t) {
            Store.diag(ctx, "clockErr", now, who, t.getClass().getSimpleName() + ": " + t.getMessage());
        }
        try { CalendarWidgetProvider.updateAll(ctx); } catch (Throwable ignored) { }
        if ("minute".equals(Store.sbbMode(ctx))) {
            try { SbbWidgetProvider.updateAll(ctx); } catch (Throwable ignored) { }
        }
    }

    /** SBB second hand. */
    private final Runnable frameLoop = new Runnable() {
        @Override
        public void run() {
            if (!on) return;
            Context ctx = getApplicationContext();
            String mode = Store.sbbMode(ctx);
            int[] ids = SbbWidgetProvider.widgetIds(ctx);
            if (ids.length == 0 || "minute".equals(mode)) return; // nothing to animate; minuteLoop keeps going
            boolean smooth = "smooth".equals(mode);
            try {
                long now = System.currentTimeMillis();
                ZoneId zone = ZoneId.systemDefault();
                // the elastic swing of the minute hand is only shown in smooth mode; other modes keep it settled
                buf = SbbRenderer.render(now, zone, SbbRenderer.SIZE, buf, smooth);
                SbbWidgetProvider.pushFrame(ctx, ids, buf);
            } catch (Throwable ignored) { }
            long now = System.currentTimeMillis();
            long msIn = Math.floorMod(now, 60_000L);
            long step = SbbClock.easing(msIn) ? 33L : 100L; // faster while the minute hand swings (0.3 s wobble)
            long next = smooth ? (now / step + 1) * step + 3L : (now / 1000L + 1) * 1000L + 20L;
            handler.postDelayed(this, Math.max(15L, next - now));
        }
    };
}
