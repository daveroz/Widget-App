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
import android.os.SystemClock;

import java.time.ZoneId;

/**
 * Keeps all widgets live while the screen is on, independent of alarms (which some phones delay):
 *  - on every screen-on and at every minute boundary it repaints the clocks (Currency + Calendar + SBB),
 *  - for the SBB clock it also pushes frames for the second hand (about 10 a second, or 1 a second),
 *  - for the Sound widget it pushes frames of its light show (about 20 a second while something plays,
 *    12 while it plays its patterns), and listens to the sound while something plays.
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
    private SoundWave wave;
    private long soundLast, soundPoll;
    private int[] soundIds = new int[0];
    private String soundStatus = "";

    // ------------------------------------------------------------------ control

    static boolean wanted(Context ctx) {
        // Only the moving SBB second hand and the Sound widget's lights need a running service (Android requires a
        // notification for that). World clocks and the calendar run on exact alarms alone, with no notification.
        return (SbbWidgetProvider.widgetIds(ctx).length > 0 && !"minute".equals(Store.sbbMode(ctx)))
                || SoundWidgetProvider.widgetIds(ctx).length > 0;
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
        if (pm.isInteractive()) {
            start();
            kick(); // a widget may have just been added: make sure its loop runs
        }
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
                .setContentText("Keeps the widgets moving while the screen is on")
                .setContentIntent(open)
                .setOngoing(true)
                .build();
        if (Build.VERSION.SDK_INT >= 34) {
            // The Sound widget hears the phone's sound only while this runs as a "microphone" service. It never opens
            // a microphone: Android files the sound-levels permission under it. Android only allows that type while
            // the permission is on (and refuses it in some cases started from the background): then carry on without.
            int type = ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE;
            if (wantsSound(this)) {
                try {
                    startForeground(NOTIF_ID, n, type | ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
                    return;
                } catch (RuntimeException ignored) { }
            }
            startForeground(NOTIF_ID, n, type);
        } else {
            startForeground(NOTIF_ID, n);
        }
    }

    /** Whether there's a Sound widget that may listen to the phone's sound. */
    private static boolean wantsSound(Context ctx) {
        return SoundWidgetProvider.widgetIds(ctx).length > 0
                && ctx.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED;
    }

    // ------------------------------------------------------------------ loops

    private void start() {
        if (on) return;
        on = true;
        handler.post(minuteLoop);
        handler.post(frameLoop);
        handler.post(soundLoop);
    }

    /** Restarts the frame loops, which stop by themselves while they have no widget to move. */
    private void kick() {
        if (!on || handler == null) return;
        handler.removeCallbacks(frameLoop);
        handler.post(frameLoop);
        handler.removeCallbacks(soundLoop);
        handler.post(soundLoop);
    }

    private void stopLoops() {
        on = false;
        if (handler != null) {
            handler.removeCallbacks(minuteLoop);
            handler.removeCallbacks(frameLoop);
            handler.removeCallbacks(soundLoop);
            handler.post(this::stopListening);
        }
    }

    private void stopListening() {
        if (wave != null) wave.stop();
        soundLast = 0;
        soundPoll = 0;
    }

    /**
     * The Sound widget's light show: about twice a second it looks at what's playing (and listens while something
     * plays), and every frame it moves the lights and the record on and sends the new picture.
     */
    private final Runnable soundLoop = new Runnable() {
        @Override
        public void run() {
            if (!on) return;
            Context ctx = getApplicationContext();
            long now = SystemClock.uptimeMillis();
            if (soundPoll == 0 || now - soundPoll >= 500) soundIds = SoundWidgetProvider.widgetIds(ctx);
            if (soundIds.length == 0) {
                stopListening();
                return; // nothing to move; kick() restarts this when one is added
            }
            float dt = soundLast == 0 ? 0f : Math.min(0.1f, (now - soundLast) / 1000f);
            soundLast = now;
            if (wave == null) wave = new SoundWave(ctx);
            boolean busy = false;
            try {
                if (now - soundPoll >= 500) {
                    soundPoll = now;
                    if (SoundWidgetProvider.refresh(ctx)) SoundWidgetProvider.updateAll(ctx); // a new app to open on tap
                    if (SoundWidgetProvider.playing()) {
                        wave.start();
                        wave.setPlaying(true);
                    } else {
                        wave.stop();
                    }
                    String st = wave.status().name();
                    if (!st.equals(soundStatus)) {
                        soundStatus = st;
                        Store.sp(ctx).edit().putString("soundStatus", st).apply();
                    }
                }
                busy = SoundWidgetProvider.step(soundIds, dt, wave);
                SoundWidgetProvider.pushFrames(ctx, soundIds);
            } catch (Throwable ignored) { }
            handler.postDelayed(this, busy ? 50L : 83L); // 20 frames a second with the sound, 12 for the patterns
        }
    };

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
