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
 * The Music widget: works whatever is playing in any app, in the light of what's playing (the cover's colour, the
 * app's, or amber). The cover and what's playing on top; along the bottom previous, play / pause, next, the time so
 * far, the progress line and the length. A tap on the progress line jumps the song to that point; a tap on the rest
 * opens the playing app.
 *
 * It's still between changes (no animation), so taps get the home screen's full attention. The media listener
 * redraws it when the song or the player changes, and moves the progress on once a second while something plays.
 */
public class PlayerWidgetProvider extends AppWidgetProvider {
    static final String ACTION_PLAY_PAUSE = "app.currencyexchange.widget.MUSIC_PLAY_PAUSE";
    static final String ACTION_NEXT = "app.currencyexchange.widget.MUSIC_NEXT";
    static final String ACTION_PREV = "app.currencyexchange.widget.MUSIC_PREV";
    static final String ACTION_SEEK = "app.currencyexchange.widget.MUSIC_SEEK";
    /** A tap in the gaps of the controls row: it does nothing (rather than opening the app by mistake). */
    static final String ACTION_NONE = "app.currencyexchange.widget.MUSIC_NONE";
    static final String EXTRA_AT = "at";
    /** The progress line is split into these zones, left to right; a tap on one jumps to its middle. */
    private static final int[] SEEK_ZONES = {R.id.seek_0, R.id.seek_1, R.id.seek_2, R.id.seek_3, R.id.seek_4,
            R.id.seek_5, R.id.seek_6, R.id.seek_7, R.id.seek_8, R.id.seek_9, R.id.seek_10, R.id.seek_11, R.id.seek_12,
            R.id.seek_13, R.id.seek_14, R.id.seek_15, R.id.seek_16, R.id.seek_17, R.id.seek_18, R.id.seek_19,
            R.id.seek_20, R.id.seek_21, R.id.seek_22, R.id.seek_23};
    /** The picture is drawn at up to 2 pixels per dp, and no more than this many pixels. */
    private static final float MOST_PIXELS = 450_000f;

    /** Each widget's picture size, worked out when it's placed or resized: {width px, height px, px per dp}. */
    private static final Map<Integer, float[]> SIZES = new HashMap<>();
    /** What each widget's picture and controls show, so each is only sent again when it changes. */
    private static final Map<Integer, String> PICTURED = new HashMap<>();
    private static final Map<Integer, String> SHOWN = new HashMap<>();
    private static NowPlaying.Track track;
    private static Scene scene = Scene.amber();
    private static boolean access;
    /**
     * What a control just asked for, shown straight away: players take a moment to say they've paused or jumped,
     * and a button that doesn't change looks like it missed the tap (and gets tapped again, undoing it).
     */
    private static Boolean expectPlaying;
    private static long expectPlayingUntil, expectPosition = -1, expectPositionAt, expectPositionUntil;
    private static final long EXPECT_MS = 2500;

    @Override
    public void onUpdate(Context ctx, AppWidgetManager awm, int[] ids) {
        updateAll(ctx);
    }

    @Override
    public void onAppWidgetOptionsChanged(Context ctx, AppWidgetManager awm, int id, Bundle options) {
        synchronized (PlayerWidgetProvider.class) {
            SIZES.remove(id); // resized: work the size out again
        }
        updateAll(ctx);
    }

    @Override
    public void onDeleted(Context ctx, int[] ids) {
        synchronized (PlayerWidgetProvider.class) {
            for (int id : ids) {
                SIZES.remove(id);
                PICTURED.remove(id);
                SHOWN.remove(id);
            }
        }
    }

    @Override
    public void onReceive(Context ctx, Intent intent) {
        super.onReceive(ctx, intent);
        String a = intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(a) || Intent.ACTION_MY_PACKAGE_REPLACED.equals(a)) {
            updateAll(ctx);
        } else if (ACTION_PLAY_PAUSE.equals(a) || ACTION_NEXT.equals(a) || ACTION_PREV.equals(a) || ACTION_SEEK.equals(a)) {
            control(ctx, a, intent.getFloatExtra(EXTRA_AT, -1f));
        }
    }

    static int[] widgetIds(Context ctx) {
        return AppWidgetManager.getInstance(ctx).getAppWidgetIds(new ComponentName(ctx, PlayerWidgetProvider.class));
    }

    static synchronized boolean playing() {
        return track != null && track.playing;
    }

    /** Looks at what's playing (with what a tap just asked for laid over it) and its light. */
    private static synchronized void refresh(Context ctx) {
        access = NowPlaying.hasAccess(ctx);
        track = expected(access ? NowPlaying.current(ctx) : null);
        scene = Ambience.lightFor(ctx, track);
        Ambience.show(ctx, scene);
    }

    /** Full update of every Music widget: picture, controls and what taps do. */
    static void updateAll(Context ctx) {
        int[] ids = widgetIds(ctx);
        if (ids.length == 0) return;
        refresh(ctx);
        AppWidgetManager awm = AppWidgetManager.getInstance(ctx);
        for (int id : ids) {
            try {
                RemoteViews rv = new RemoteViews(ctx.getPackageName(), R.layout.widget_player);
                synchronized (PlayerWidgetProvider.class) {
                    rv.setImageViewBitmap(R.id.img, picture(ctx, id));
                    rv.setOnClickPendingIntent(R.id.root, tapIntent(ctx));
                    controls(ctx, rv, id, true);
                }
                awm.updateAppWidget(id, rv);
            } catch (Throwable ignored) { }
        }
    }

    /**
     * Once a second while something plays (from the media listener): the progress moves on. Only what changed is
     * sent: the line and the time, or everything when the song or play / pause changed.
     */
    static void tick(Context ctx) {
        int[] ids = widgetIds(ctx);
        if (ids.length == 0) return;
        refresh(ctx);
        AppWidgetManager awm = AppWidgetManager.getInstance(ctx);
        for (int id : ids) {
            boolean full;
            synchronized (PlayerWidgetProvider.class) {
                full = !pictureKey(id).equals(PICTURED.get(id));
            }
            if (full) {
                updateAll(ctx);
                return;
            }
            RemoteViews rv = new RemoteViews(ctx.getPackageName(), R.layout.widget_player);
            boolean sent;
            synchronized (PlayerWidgetProvider.class) {
                sent = controls(ctx, rv, id, false);
            }
            if (sent) {
                try { awm.partiallyUpdateAppWidget(id, rv); } catch (Throwable ignored) { }
            }
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
                synchronized (PlayerWidgetProvider.class) {
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
                    synchronized (PlayerWidgetProvider.class) {
                        expectPosition = to;
                        expectPositionAt = now;
                        expectPositionUntil = now + EXPECT_MS;
                    }
                }
            }
        }
        // Show it at once with a tiny update of just the controls; the picture follows when the player says so.
        int[] ids = widgetIds(ctx);
        AppWidgetManager awm = AppWidgetManager.getInstance(ctx);
        for (int id : ids) {
            RemoteViews rv = new RemoteViews(ctx.getPackageName(), R.layout.widget_player);
            boolean sent;
            synchronized (PlayerWidgetProvider.class) {
                track = expected(track);
                sent = controls(ctx, rv, id, false);
            }
            if (sent) {
                try { awm.partiallyUpdateAppWidget(id, rv); } catch (Throwable ignored) { }
            }
        }
    }

    /** Whether the widget shows the player as playing: what the last tap asked for, while that's fresh. */
    private static synchronized boolean shownPlaying(MediaController player, long now) {
        if (expectPlaying != null && now < expectPlayingUntil) return expectPlaying;
        PlaybackState s = player.getPlaybackState();
        return s != null && (s.getState() == PlaybackState.STATE_PLAYING || s.getState() == PlaybackState.STATE_BUFFERING);
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

    /** The widget's picture size, from its size on the home screen: {width px, height px, px per dp}. */
    private static float[] size(Context ctx, int id) {
        float[] s = SIZES.get(id);
        if (s != null) return s;
        Bundle o = AppWidgetManager.getInstance(ctx).getAppWidgetOptions(id);
        boolean land = ctx.getResources().getConfiguration().orientation
                == android.content.res.Configuration.ORIENTATION_LANDSCAPE;
        int wDp = o == null ? 0 : o.getInt(land ? AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH : AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH);
        int hDp = o == null ? 0 : o.getInt(land ? AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT : AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT);
        if (wDp <= 0) wDp = 320;
        if (hDp <= 0) hDp = 150;
        float dp = scale(wDp, hDp);
        s = new float[]{Math.max(2, Math.round(wDp * dp)), Math.max(2, Math.round(hDp * dp)), dp};
        SIZES.put(id, s);
        return s;
    }

    static float scale(int wDp, int hDp) {
        return Math.min(2f, (float) Math.sqrt(MOST_PIXELS / Math.max(1, wDp * hDp)));
    }

    /** What the picture shows: it's drawn again only when this changes. */
    private static String pictureKey(int id) {
        float[] s = SIZES.get(id);
        return (track == null ? "none" : track.song() + "|" + System.identityHashCode(track.art) + "|" + track.playing
                + "|" + (track.duration > 0)) + "|" + scene.key + "|" + access + "|" + (s == null ? "" : s[0] + "x" + s[1]);
    }

    private static Bitmap picture(Context ctx, int id) {
        float[] s = size(ctx, id);
        PICTURED.put(id, pictureKey(id));
        return PlayerPicture.draw(ctx, track, scene, access, (int) s[0], (int) s[1], s[2]);
    }

    /**
     * The controls along the bottom, shown while something plays (or is paused): previous, play / pause, next, the
     * time so far, the progress line and the length. Small widgets leave out the times. Unless {@code full}, only
     * what changed is set; returns whether anything was.
     */
    private static boolean controls(Context ctx, RemoteViews rv, int id, boolean full) {
        NowPlaying.Track t = track;
        float[] size = size(ctx, id);
        int wDp = Math.round(size[0] / size[2]);
        long at = t == null ? 0 : t.positionAt(NowPlaying.now());
        String key = t == null ? "none" : t.playing + "|" + scene.key + "|" + at / 1000 + "|" + t.duration + "|" + wDp;
        if (!full && key.equals(SHOWN.get(id))) return false;
        SHOWN.put(id, key);
        if (t == null) {
            rv.setViewVisibility(R.id.controls, View.GONE);
            return true;
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
            rv.setOnClickPendingIntent(R.id.btn_prev, action(ctx, ACTION_PREV, 60, -1f));
            rv.setOnClickPendingIntent(R.id.btn_play, action(ctx, ACTION_PLAY_PAUSE, 61, -1f));
            rv.setOnClickPendingIntent(R.id.btn_next, action(ctx, ACTION_NEXT, 62, -1f));
            rv.setOnClickPendingIntent(R.id.controls, action(ctx, ACTION_NONE, 63, -1f));
            // Each zone jumps to the point of the line under its middle (the line stops short of the ends, so the
            // point fits there).
            for (int i = 0; i < SEEK_ZONES.length; i++) {
                float middle = (i + 0.5f) / SEEK_ZONES.length * barDp;
                rv.setOnClickPendingIntent(SEEK_ZONES[i], action(ctx, ACTION_SEEK, 200 + i, SoundControls.fractionAt(middle, barDp)));
            }
        }
        rv.setImageViewBitmap(R.id.btn_play, SoundControls.playButton(scene, t.playing, 50));
        rv.setViewVisibility(R.id.seek, timed ? View.VISIBLE : View.INVISIBLE);
        rv.setViewVisibility(R.id.t_elapsed, times ? View.VISIBLE : View.GONE);
        rv.setViewVisibility(R.id.t_total, times ? View.VISIBLE : View.GONE);
        if (!timed) return true;
        if (times) {
            rv.setImageViewBitmap(R.id.t_elapsed, done);
            rv.setImageViewBitmap(R.id.t_total, all);
        }
        rv.setImageViewBitmap(R.id.bar, SoundControls.bar(scene, at / (float) t.duration, barDp));
        return true;
    }

    private static PendingIntent action(Context ctx, String action, int code, float at) {
        // A foreground broadcast: delivered at once, not queued behind other apps'.
        Intent i = new Intent(ctx, PlayerWidgetProvider.class).setAction(action).addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
        if (at >= 0) i.putExtra(EXTRA_AT, at);
        return PendingIntent.getBroadcast(ctx, code, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    /** A tap opens the playing app (its player screen when it offers one), or Worldly's settings. */
    private static PendingIntent tapIntent(Context ctx) {
        if (track != null && track.open != null) return track.open;
        if (track != null) {
            Intent launch = ctx.getPackageManager().getLaunchIntentForPackage(track.packageName);
            if (launch != null) {
                return PendingIntent.getActivity(ctx, 64, launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            }
        }
        return PendingIntent.getActivity(ctx, 65, new Intent(ctx, MainActivity.class),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }
}
