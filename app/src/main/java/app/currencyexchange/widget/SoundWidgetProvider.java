package app.currencyexchange.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.PlaybackState;
import android.os.Bundle;
import android.view.View;
import android.widget.RemoteViews;

import java.util.HashMap;
import java.util.Map;

/**
 * The Sound widget: the Deco launcher's sound light show on the home screen. A wall of lamps that
 * follows what's playing in any app (bass on the left, treble on the right), in the album cover's
 * colour or the app's, with the cover as an LP for music; its patterns when paused or nothing plays.
 * {@link LiveService} moves it while the screen is on; a tap opens the playing app. While something
 * plays, controls along the bottom work the player: previous, play / pause, next, and the progress
 * line, where a tap jumps the song to that point.
 */
public class SoundWidgetProvider extends AppWidgetProvider {
    static final String ACTION_PLAY_PAUSE = "app.currencyexchange.widget.SOUND_PLAY_PAUSE";
    static final String ACTION_NEXT = "app.currencyexchange.widget.SOUND_NEXT";
    static final String ACTION_PREV = "app.currencyexchange.widget.SOUND_PREV";
    static final String ACTION_SEEK = "app.currencyexchange.widget.SOUND_SEEK";
    /** A tap in the gaps of the controls row: it does nothing (rather than opening the app by mistake). */
    static final String ACTION_NONE = "app.currencyexchange.widget.SOUND_NONE";
    static final String EXTRA_AT = "at";
    /** The progress line is split into these zones, left to right; a tap on one jumps to its middle. */
    private static final int[] SEEK_ZONES = {R.id.seek_0, R.id.seek_1, R.id.seek_2, R.id.seek_3, R.id.seek_4, R.id.seek_5, R.id.seek_6, R.id.seek_7, R.id.seek_8, R.id.seek_9, R.id.seek_10, R.id.seek_11, R.id.seek_12, R.id.seek_13, R.id.seek_14, R.id.seek_15, R.id.seek_16, R.id.seek_17, R.id.seek_18, R.id.seek_19, R.id.seek_20, R.id.seek_21, R.id.seek_22, R.id.seek_23};
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
    /** What each widget's controls show (the second, the light, play or pause), so they're only sent when it changes. */
    private static final Map<Integer, String> SHOWN = new HashMap<>();
    private static NowPlaying.Track track;
    private static Scene scene = Scene.amber();
    private static boolean access;
    /** Which picture the light was worked out for, and which app the tap opens. */
    private static Object sceneFor;
    private static String state = "";
    /**
     * What a control just asked for, shown straight away: players take a moment to say they've paused or jumped,
     * and a button that doesn't change looks like it missed the tap (and gets tapped again, undoing it).
     */
    private static Boolean expectPlaying;
    private static long expectPlayingUntil, expectPosition = -1, expectPositionAt, expectPositionUntil;
    /** How long a control's answer is shown before the player's own word counts again. */
    private static final long EXPECT_MS = 2500;
    /** Frames pause for a moment after a tap, so the home screen is free to take the next one. */
    private static long quietUntil;

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
                SHOWN.remove(id);
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
        } else if (ACTION_PLAY_PAUSE.equals(a) || ACTION_NEXT.equals(a) || ACTION_PREV.equals(a) || ACTION_SEEK.equals(a)) {
            control(ctx, a, intent.getFloatExtra(EXTRA_AT, -1f));
        }
    }

    /** A control was tapped: tell the player, show what it asked for straight away, and let the player catch up. */
    static void control(Context ctx, String action, float at) {
        long now = NowPlaying.now();
        MediaController player = NowPlaying.controller(ctx);
        if (player != null) {
            MediaController.TransportControls t = player.getTransportControls();
            if (ACTION_PLAY_PAUSE.equals(action)) {
                // Toggle what the widget shows (which may be a tap ago, ahead of the player), so taps never cancel out.
                boolean want = !shownPlaying(player, now);
                if (want) t.play();
                else t.pause();
                synchronized (SoundWidgetProvider.class) {
                    expectPlaying = want;
                    expectPlayingUntil = now + EXPECT_MS;
                }
            } else if (ACTION_NEXT.equals(action)) {
                t.skipToNext();
            } else if (ACTION_PREV.equals(action)) {
                t.skipToPrevious();
            } else if (ACTION_SEEK.equals(action) && at >= 0) {
                MediaMetadata m = player.getMetadata();
                long duration = m == null ? 0 : m.getLong(MediaMetadata.METADATA_KEY_DURATION);
                if (duration > 0) {
                    long to = (long) (Math.min(1f, at) * duration);
                    t.seekTo(to);
                    synchronized (SoundWidgetProvider.class) {
                        expectPosition = to;
                        expectPositionAt = now;
                        expectPositionUntil = now + EXPECT_MS;
                    }
                }
            }
        }
        synchronized (SoundWidgetProvider.class) {
            quietUntil = now + 350;
        }
        refresh(ctx);
        updateAll(ctx);
        LiveService.ensure(ctx);
    }

    /** Whether the widget shows the player as playing: what the last tap asked for, while that's fresh. */
    private static synchronized boolean shownPlaying(MediaController player, long now) {
        if (expectPlaying != null && now < expectPlayingUntil) return expectPlaying;
        PlaybackState s = player.getPlaybackState();
        return s != null && (s.getState() == PlaybackState.STATE_PLAYING || s.getState() == PlaybackState.STATE_BUFFERING);
    }

    /** Whether frames should hold off for a moment, right after a tap. */
    static synchronized boolean quiet() {
        return NowPlaying.now() < quietUntil;
    }

    /** What's playing, with what a control just asked for laid over it until the player says the same. */
    private static NowPlaying.Track expected(NowPlaying.Track now) {
        if (now == null) return null;
        long t = NowPlaying.now();
        if (expectPlaying != null) {
            if (t < expectPlayingUntil && now.playing != expectPlaying) now = now.withPlaying(expectPlaying);
            else expectPlaying = null; // the player caught up (or never will): its word counts again
        }
        if (expectPosition >= 0) {
            long target = expectPosition + (now.playing ? t - expectPositionAt : 0);
            if (t < expectPositionUntil && Math.abs(now.positionAt(t) - target) > 1500) {
                now = now.withPosition(target, t);
            } else {
                expectPosition = -1;
            }
        }
        return now;
    }

    static int[] widgetIds(Context ctx) {
        return AppWidgetManager.getInstance(ctx).getAppWidgetIds(new ComponentName(ctx, SoundWidgetProvider.class));
    }

    /**
     * Looks at what's playing now and works out its light: the album cover's primary colour, or the
     * app's own for a video or no cover, or Worldly's amber with nothing playing. Returns true when the
     * playing app, play or pause, or the light changed, so the tap targets and controls need sending again.
     */
    static synchronized boolean refresh(Context ctx) {
        access = NowPlaying.hasAccess(ctx);
        NowPlaying.Track now = expected(access ? NowPlaying.current(ctx) : null);
        track = now;
        Object key = now == null ? null : now.hasCover() ? now.art : now.packageName;
        if (key == null) {
            scene = Scene.amber();
            sceneFor = null;
        } else if (!key.equals(sceneFor)) { // a new cover (by picture) or a new app (by name)
            sceneFor = key;
            scene = Scene.of(now.hasCover() ? Colours.primary(now.art) : Colours.app(ctx, now.packageName));
        }
        for (SoundPicture p : PICTURES.values()) p.set(now, scene, access);
        String next = now == null ? "" : now.packageName + "|" + now.playing + "|" + scene.key;
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
                controls(ctx, rv, id, true);
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
            controls(ctx, rv, id, false);
            awm.partiallyUpdateAppWidget(id, rv);
        }
    }

    /**
     * The controls along the bottom, shown while something plays: previous, play / pause, next, the time so far,
     * the progress line and the length. Small widgets leave out the times. On a frame ({@code full} false) only
     * what changed is sent: the progress once a second, the button when play / pause changes.
     */
    private static synchronized void controls(Context ctx, RemoteViews rv, int id, boolean full) {
        NowPlaying.Track t = track;
        float[] size = SIZES.get(id);
        int wDp = size == null ? 320 : Math.round(size[0] / size[2]);
        long at = t == null ? 0 : t.positionAt(NowPlaying.now());
        String key = t == null ? "none" : t.playing + "|" + scene.key + "|" + at / 1000 + "|" + t.duration + "|" + wDp;
        if (!full && key.equals(SHOWN.get(id))) return;
        SHOWN.put(id, key);
        if (t == null) {
            rv.setViewVisibility(R.id.controls, View.GONE);
            return;
        }
        rv.setViewVisibility(R.id.controls, View.VISIBLE);
        boolean timed = t.duration > 0;
        boolean times = timed && wDp >= 300;
        // The line's width: what's left of the row after its margins, the buttons and the times.
        float used = 6 + 14 + 46 + 50 + 46;
        Bitmap done = null, all = null;
        if (times) {
            done = TextBmp.tight(Theme.uiMedium(ctx), 11, SoundControls.clock(at), Theme.MUTED);
            all = TextBmp.tight(Theme.uiMedium(ctx), 11, SoundControls.clock(t.duration), Theme.MUTED);
            used += 8 + (done.getWidth() + all.getWidth()) / 2.5f; // the time pictures are 2.5 px per dp
        }
        int barDp = Math.max(40, Math.round(wDp - used));
        if (full) {
            rv.setImageViewBitmap(R.id.btn_prev, SoundControls.skip(false, 46));
            rv.setImageViewBitmap(R.id.btn_next, SoundControls.skip(true, 46));
            rv.setOnClickPendingIntent(R.id.btn_prev, action(ctx, ACTION_PREV, 40, -1f));
            rv.setOnClickPendingIntent(R.id.btn_play, action(ctx, ACTION_PLAY_PAUSE, 41, -1f));
            rv.setOnClickPendingIntent(R.id.btn_next, action(ctx, ACTION_NEXT, 42, -1f));
            rv.setOnClickPendingIntent(R.id.controls, action(ctx, ACTION_NONE, 43, -1f));
            // Each zone jumps to the point of the line under its middle (the line stops short of the ends, so the
            // point fits there).
            for (int i = 0; i < SEEK_ZONES.length; i++) {
                float middle = (i + 0.5f) / SEEK_ZONES.length * barDp;
                rv.setOnClickPendingIntent(SEEK_ZONES[i], action(ctx, ACTION_SEEK, 100 + i, SoundControls.fractionAt(middle, barDp)));
            }
        }
        rv.setImageViewBitmap(R.id.btn_play, SoundControls.playButton(scene, t.playing, 50));
        rv.setViewVisibility(R.id.seek, timed ? View.VISIBLE : View.INVISIBLE);
        rv.setViewVisibility(R.id.t_elapsed, times ? View.VISIBLE : View.GONE);
        rv.setViewVisibility(R.id.t_total, times ? View.VISIBLE : View.GONE);
        if (!timed) return;
        if (times) {
            rv.setImageViewBitmap(R.id.t_elapsed, done);
            rv.setImageViewBitmap(R.id.t_total, all);
        }
        rv.setImageViewBitmap(R.id.bar, SoundControls.bar(scene, at / (float) t.duration, barDp));
    }

    private static PendingIntent action(Context ctx, String action, int code, float at) {
        // A foreground broadcast: delivered at once, not queued behind other apps' (which could hold a tap back for
        // a second or more, so a second tap arrived with it and undid it).
        Intent i = new Intent(ctx, SoundWidgetProvider.class).setAction(action).addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
        if (at >= 0) i.putExtra(EXTRA_AT, at);
        return PendingIntent.getBroadcast(ctx, code, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
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
