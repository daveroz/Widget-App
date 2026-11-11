package app.currencyexchange.widget;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;

/**
 * The light all the widgets share: the colour of what's playing (the album cover's primary colour, or the playing
 * app's own for videos and apps without a cover), or Worldly's amber when nothing plays. The Sound widget keeps it
 * current while it runs; the media listener does when a song changes, so the Currency, Calendar and SBB clock
 * widgets follow the music even without a Sound widget. When it changes, those widgets are redrawn in its colour.
 */
final class Ambience {
    private Ambience() {}

    private static Scene scene = Scene.amber();
    /** The cover picture or app the last light was worked out for, and that light. */
    private static Object litFor;
    private static Scene lit = Scene.amber();
    /** When a cover's light was last seen (and from which app), and when anything was: to ride over the gap between songs. */
    private static long coverAt, seenAt;
    private static String coverApp;
    private static Handler worker;

    static synchronized Scene scene() {
        return scene;
    }

    /** The light for what's playing (null for nothing): worked out once per cover or app. */
    static synchronized Scene lightFor(Context ctx, NowPlaying.Track now) {
        long t = NowPlaying.now();
        // Between songs the player goes quiet for a moment, or sends the title before the cover: keep the light.
        if (now == null && litFor != null && t - seenAt < NowPlaying.BLIP_MS) return lit;
        if (now != null && !now.hasCover() && litFor instanceof android.graphics.Bitmap
                && now.packageName.equals(coverApp) && t - coverAt < NowPlaying.BLIP_MS) {
            seenAt = t;
            return lit;
        }
        Object key = now == null ? null : now.hasCover() ? now.art : now.packageName;
        if (key == null) {
            litFor = null;
            return Scene.amber();
        }
        seenAt = t;
        if (now.hasCover()) {
            coverAt = t;
            coverApp = now.packageName;
        }
        if (!key.equals(litFor)) {
            litFor = key;
            lit = Scene.of(now.hasCover() ? Colours.primary(now.art) : Colours.app(ctx, now.packageName));
        }
        return lit;
    }

    /** Shows this light on every widget: the others are redrawn in it (in the background) when it changes. */
    static void show(Context ctx, Scene light) {
        boolean changed;
        synchronized (Ambience.class) {
            changed = !light.sameAs(scene);
            scene = light;
        }
        if (changed) redrawOthers(ctx.getApplicationContext());
    }

    /** Looks at what's playing and shows its light: for when no Sound widget is keeping it current. */
    static void check(Context ctx) {
        NowPlaying.Track now = NowPlaying.hasAccess(ctx) ? NowPlaying.current(ctx) : null;
        show(ctx, lightFor(ctx, now));
    }

    private static void redrawOthers(Context app) {
        Handler h;
        synchronized (Ambience.class) {
            if (worker == null) {
                HandlerThread t = new HandlerThread("ambience");
                t.start();
                worker = new Handler(t.getLooper());
            }
            h = worker;
        }
        h.removeCallbacksAndMessages(null);
        h.post(() -> {
            try { WidgetProvider.updateAll(app); } catch (Throwable ignored) { }
            try { CalendarWidgetProvider.updateAll(app); } catch (Throwable ignored) { }
            try { SbbWidgetProvider.updateAll(app); } catch (Throwable ignored) { }
        });
    }
}
