package app.currencyexchange.widget;

import android.service.notification.NotificationListenerService;

/**
 * Being an enabled notification listener is what lets Worldly see which media is playing (Spotify,
 * YouTube, a podcast app...), for the Sound widget. Android only shows other apps' media sessions to
 * apps with notification access. It reads no notifications.
 */
public class MediaListener extends NotificationListenerService {
}
