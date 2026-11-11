package app.currencyexchange.widget;

import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.SystemClock;
import android.provider.Settings;

import java.util.List;

/**
 * Whatever media is playing (Spotify, YouTube Music, a podcast app...), through Android's media
 * sessions, for the Sound widget. Seeing other apps' sessions needs notification access, see
 * {@link MediaListener}.
 */
final class NowPlaying {
    private NowPlaying() {}

    /** A snapshot of the playing track. */
    static final class Track {
        final String packageName;
        final String app;
        final String title;
        final String artist;
        final Bitmap art;
        final boolean playing;
        final long position;
        final long duration;
        final long updatedAt;
        final float speed;
        final PendingIntent open;

        Track(String packageName, String app, String title, String artist, Bitmap art, boolean playing,
              long position, long duration, long updatedAt, float speed, PendingIntent open) {
            this.packageName = packageName;
            this.app = app;
            this.title = title;
            this.artist = artist;
            this.art = art;
            this.playing = playing;
            this.position = position;
            this.duration = duration;
            this.updatedAt = updatedAt;
            this.speed = speed;
            this.open = open;
        }

        /** Which song this is, whatever its picture (players resend the same cover as new pictures). */
        String song() {
            return packageName + '\n' + title + '\n' + artist;
        }

        /** A square picture is an album cover, shown as an LP; a wide one is a video's, and isn't shown. */
        boolean hasCover() {
            return art != null && !isWide(art.getWidth(), art.getHeight());
        }

        /** Where playback is now, moving on from the last reported position while playing. */
        long positionAt(long elapsedRealtime) {
            long now = playing && updatedAt > 0 ? position + (long) ((elapsedRealtime - updatedAt) * speed) : position;
            return duration > 0 ? Math.max(0, Math.min(now, duration)) : Math.max(0, now);
        }
    }

    static boolean isWide(int width, int height) {
        return width > height * 1.4f;
    }

    /** Whether notification access is on, which is what lets Worldly see other apps' media. */
    static boolean hasAccess(Context ctx) {
        ComponentName me = new ComponentName(ctx, MediaListener.class);
        String enabled = Settings.Secure.getString(ctx.getContentResolver(), "enabled_notification_listeners");
        return enabled != null && (enabled.contains(me.flattenToString()) || enabled.contains(me.flattenToShortString()));
    }

    /** What's playing now, or paused, or null for nothing (or no access). */
    static Track current(Context ctx) {
        MediaSessionManager sessions = ctx.getSystemService(MediaSessionManager.class);
        if (sessions == null) return null;
        List<MediaController> controllers;
        try {
            controllers = sessions.getActiveSessions(new ComponentName(ctx, MediaListener.class));
        } catch (SecurityException e) {
            return null; // notification access is off
        }
        MediaController best = null;
        for (MediaController c : controllers) {
            if (c.getMetadata() == null) continue;
            if (isPlaying(c)) {
                best = c;
                break;
            }
            if (best == null && !stopped(c)) best = c;
        }
        return best == null ? null : snapshot(ctx, best);
    }

    private static boolean isPlaying(MediaController c) {
        PlaybackState s = c.getPlaybackState();
        return s != null && (s.getState() == PlaybackState.STATE_PLAYING || s.getState() == PlaybackState.STATE_BUFFERING);
    }

    private static boolean stopped(MediaController c) {
        PlaybackState s = c.getPlaybackState();
        return s == null || s.getState() == PlaybackState.STATE_STOPPED || s.getState() == PlaybackState.STATE_NONE
                || s.getState() == PlaybackState.STATE_ERROR;
    }

    private static Track snapshot(Context ctx, MediaController c) {
        MediaMetadata m = c.getMetadata();
        String title = text(m, MediaMetadata.METADATA_KEY_DISPLAY_TITLE, MediaMetadata.METADATA_KEY_TITLE);
        String artist = text(m, MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE, MediaMetadata.METADATA_KEY_ARTIST,
                MediaMetadata.METADATA_KEY_ALBUM_ARTIST);
        Bitmap art = m.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
        if (art == null) art = m.getBitmap(MediaMetadata.METADATA_KEY_ART);
        if (art == null) art = m.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON);
        PlaybackState s = c.getPlaybackState();
        long duration = m.getLong(MediaMetadata.METADATA_KEY_DURATION);
        return new Track(c.getPackageName(), appName(ctx, c.getPackageName()), title == null ? "" : title,
                artist == null ? "" : artist, art, isPlaying(c), s == null ? 0 : s.getPosition(), Math.max(0, duration),
                s == null ? 0 : s.getLastPositionUpdateTime(), s == null ? 1f : s.getPlaybackSpeed(),
                c.getSessionActivity());
    }

    private static String text(MediaMetadata m, String... keys) {
        for (String k : keys) {
            CharSequence v = m.getText(k);
            if (v != null && v.length() > 0) return v.toString();
        }
        return null;
    }

    private static String appName(Context ctx, String pkg) {
        try {
            PackageManager pm = ctx.getPackageManager();
            ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
            return pm.getApplicationLabel(ai).toString();
        } catch (Exception e) {
            return pkg;
        }
    }

    static long now() {
        return SystemClock.elapsedRealtime();
    }
}
