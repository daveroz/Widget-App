package app.currencyexchange.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.widget.RemoteViews;

import java.util.HashMap;
import java.util.Map;

/**
 * The Sound widget: the Deco launcher's sound light show on the home screen. A wall of lamps that
 * follows what's playing in any app (bass on the left, treble on the right), in the album cover's
 * colour or the app's, with the cover as an LP for music; its patterns when paused or nothing plays.
 * {@link LiveService} moves it while the screen is on; a tap opens the playing app. It has no
 * controls, so every frame is just its picture: the Music widget ({@link PlayerWidgetProvider}) has them.
 */
public class SoundWidgetProvider extends AppWidgetProvider {
    /**
     * Pictures aim for this many pixels, so each frame stays light to send to the home screen, but never fewer than
     * {@link #MIN_DP} pixels per dp (so a big widget stays sharp), nor more than {@link #MOST_PIXELS}.
     */
    private static final float PIXELS = 150_000f, MOST_PIXELS = 420_000f;
    private static final float MIN_DP = 1.6f;

    private static final Map<Integer, SoundPicture> PICTURES = new HashMap<>();
    private static final Map<Integer, Bitmap> BUFFERS = new HashMap<>();
    /** Each widget's picture size, worked out when it's placed or resized, not on every frame. */
    private static final Map<Integer, float[]> SIZES = new HashMap<>();
    private static NowPlaying.Track track;
    private static Scene scene = Scene.amber();
    private static boolean access;
    /** Which app a tap opens (a full update is sent when it changes). */
    private static String state = "";
    @Override
    public void onUpdate(Context ctx, AppWidgetManager awm, int[] ids) {
        refresh(ctx);
        updateAll(ctx);
        LiveService.ensure(ctx);
    }

    @Override
    public void onAppWidgetOptionsChanged(Context ctx, AppWidgetManager awm, int id, Bundle options) {
        synchronized (SoundWidgetProvider.class) {
            SIZES.remove(id); // resized: work the size out again
        }
        updateAll(ctx);
    }

    @Override
    public void onDeleted(Context ctx, int[] ids) {
        synchronized (SoundWidgetProvider.class) {
            for (int id : ids) {
                PICTURES.remove(id);
                BUFFERS.remove(id);
                SIZES.remove(id);
            }
        }
    }

    @Override
    public void onDisabled(Context ctx) {
        LiveService.ensure(ctx); // stops it when nothing else needs it
    }

    @Override
    public void onReceive(Context ctx, Intent intent) {
        super.onReceive(ctx, intent);
        String a = intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(a) || Intent.ACTION_MY_PACKAGE_REPLACED.equals(a)) {
            updateAll(ctx);
            LiveService.ensure(ctx);
        }
    }

    static int[] widgetIds(Context ctx) {
        return AppWidgetManager.getInstance(ctx).getAppWidgetIds(new ComponentName(ctx, SoundWidgetProvider.class));
    }

    /**
     * Looks at what's playing now and works out its light: the album cover's primary colour, or the
     * app's own for a video or no cover, or Worldly's amber with nothing playing. Returns true when the
     * playing app changed (or something started or stopped playing), so what a tap opens needs sending again.
     */
    static synchronized boolean refresh(Context ctx) {
        access = NowPlaying.hasAccess(ctx);
        NowPlaying.Track now = access ? NowPlaying.current(ctx) : null;
        track = now;
        scene = Ambience.lightFor(ctx, now);
        Ambience.show(ctx, scene); // the other widgets share the light
        for (SoundPicture p : PICTURES.values()) p.set(now, scene, access);
        String next = now == null ? "" : now.packageName;
        boolean changed = !next.equals(state);
        state = next;
        return changed;
    }

    static synchronized boolean playing() {
        return track != null && track.playing;
    }

    private static synchronized SoundPicture picture(int id) {
        SoundPicture p = PICTURES.get(id);
        if (p == null) {
            p = new SoundPicture();
            p.set(track, scene, access);
            PICTURES.put(id, p);
        }
        return p;
    }

    /** Moves these widgets' pictures on by {@code dt} seconds; true if any wants quick frames. */
    static synchronized boolean step(int[] ids, float dt, SoundWave wave) {
        boolean busy = false;
        for (int id : ids) {
            SoundPicture p = picture(id);
            p.step(dt, wave);
            busy |= p.busy();
        }
        return busy;
    }

    /** The widget's picture size, from its size on the home screen: {width px, height px, px per dp}. */
    static float[] size(Context ctx, int id) {
        Bundle o = AppWidgetManager.getInstance(ctx).getAppWidgetOptions(id);
        // Portrait: the narrowest width and the tallest height the launcher gives it; landscape the other way.
        boolean land = ctx.getResources().getConfiguration().orientation
                == android.content.res.Configuration.ORIENTATION_LANDSCAPE;
        int wDp = o == null ? 0 : o.getInt(land ? AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH : AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH);
        int hDp = o == null ? 0 : o.getInt(land ? AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT : AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT);
        if (wDp <= 0) wDp = 320;
        if (hDp <= 0) hDp = 150;
        float dp = scale(wDp, hDp);
        return new float[]{Math.max(2, Math.round(wDp * dp)), Math.max(2, Math.round(hDp * dp)), dp};
    }

    /** Pixels per dp for a picture of this size in dp. */
    static float scale(int wDp, int hDp) {
        float area = Math.max(1, wDp * hDp);
        float dp = Math.min(SoundPicture.MAX_DP, Math.max(MIN_DP, (float) Math.sqrt(PIXELS / area)));
        return Math.min(dp, (float) Math.sqrt(MOST_PIXELS / area));
    }

    private static synchronized Bitmap draw(Context ctx, int id) {
        float[] s = SIZES.get(id);
        if (s == null) {
            s = size(ctx, id);
            SIZES.put(id, s);
        }
        Bitmap b = picture(id).draw(ctx, (int) s[0], (int) s[1], s[2], BUFFERS.get(id));
        BUFFERS.put(id, b);
        return b;
    }

    /** Full update: the picture and what a tap opens. */
    static void updateAll(Context ctx) {
        AppWidgetManager awm = AppWidgetManager.getInstance(ctx);
        for (int id : widgetIds(ctx)) {
            try {
                RemoteViews rv = new RemoteViews(ctx.getPackageName(), R.layout.widget_sound);
                rv.setImageViewBitmap(R.id.img, draw(ctx, id));
                rv.setOnClickPendingIntent(R.id.root, tapIntent(ctx));
                awm.updateAppWidget(id, rv);
            } catch (Throwable ignored) { }
        }
    }

    /** Cheap picture-only update of these widgets, for each frame. */
    static void pushFrames(Context ctx, int[] ids) {
        AppWidgetManager awm = AppWidgetManager.getInstance(ctx);
        for (int id : ids) {
            RemoteViews rv = new RemoteViews(ctx.getPackageName(), R.layout.widget_sound);
            rv.setImageViewBitmap(R.id.img, draw(ctx, id));
            awm.partiallyUpdateAppWidget(id, rv);
        }
    }

    /** A tap opens the playing app (its player screen when it offers one), or Worldly's settings. */
    private static synchronized PendingIntent tapIntent(Context ctx) {
        if (track != null && track.open != null) return track.open;
        if (track != null) {
            Intent launch = ctx.getPackageManager().getLaunchIntentForPackage(track.packageName);
            if (launch != null) {
                return PendingIntent.getActivity(ctx, 30, launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            }
        }
        return PendingIntent.getActivity(ctx, 31, new Intent(ctx, MainActivity.class),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }
}
