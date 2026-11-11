package app.currencyexchange.widget;

import android.content.ComponentName;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSession;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;
import android.service.notification.NotificationListenerService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Being an enabled notification listener is what lets Worldly see which media is playing (Spotify, YouTube, a
 * podcast app...), for the Sound widget and the light all the widgets share. Android only shows other apps' media
 * sessions to apps with notification access. It reads no notifications.
 *
 * While connected it follows the media sessions, and when a song or player changes (or one starts or stops) it has
 * {@link Ambience} look again, so every widget takes on the new song's colour, with or without a Sound widget.
 */
public class MediaListener extends NotificationListenerService {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Map<MediaSession.Token, MediaController> watched = new HashMap<>();
    private final Map<MediaSession.Token, MediaController.Callback> callbacks = new HashMap<>();
    private MediaSessionManager sessions;
    private final MediaSessionManager.OnActiveSessionsChangedListener onSessions = this::watch;
    /** Looks again a moment after the last change, as players send several at once. */
    private final Runnable check = () -> Ambience.check(this);

    @Override
    public void onListenerConnected() {
        sessions = getSystemService(MediaSessionManager.class);
        if (sessions == null) return;
        try {
            ComponentName me = new ComponentName(this, MediaListener.class);
            sessions.addOnActiveSessionsChangedListener(onSessions, me, handler);
            watch(sessions.getActiveSessions(me));
        } catch (SecurityException ignored) {
            // Access was turned off again.
        }
    }

    @Override
    public void onListenerDisconnected() {
        if (sessions != null) sessions.removeOnActiveSessionsChangedListener(onSessions);
        for (Map.Entry<MediaSession.Token, MediaController> e : watched.entrySet()) {
            e.getValue().unregisterCallback(callbacks.get(e.getKey()));
        }
        watched.clear();
        callbacks.clear();
        handler.removeCallbacks(check);
    }

    private void watch(List<MediaController> controllers) {
        Map<MediaSession.Token, MediaController> now = new HashMap<>();
        if (controllers != null) for (MediaController c : controllers) now.put(c.getSessionToken(), c);
        // Stop following sessions that ended.
        for (MediaSession.Token token : new HashMap<>(watched).keySet()) {
            if (!now.containsKey(token)) watched.remove(token).unregisterCallback(callbacks.remove(token));
        }
        // Follow new ones.
        for (Map.Entry<MediaSession.Token, MediaController> e : now.entrySet()) {
            if (watched.containsKey(e.getKey())) continue;
            MediaController.Callback callback = new MediaController.Callback() {
                @Override
                public void onMetadataChanged(MediaMetadata metadata) {
                    soon();
                }

                @Override
                public void onPlaybackStateChanged(PlaybackState state) {
                    soon();
                }

                @Override
                public void onSessionDestroyed() {
                    soon();
                }
            };
            e.getValue().registerCallback(callback, handler);
            watched.put(e.getKey(), e.getValue());
            callbacks.put(e.getKey(), callback);
        }
        soon();
    }

    private void soon() {
        handler.removeCallbacks(check);
        handler.postDelayed(check, 400);
    }
}
