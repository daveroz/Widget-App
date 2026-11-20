package app.currencyexchange.widget;

import android.Manifest;
import android.app.WallpaperColors;
import android.app.WallpaperManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.util.DisplayMetrics;

import java.util.HashMap;
import java.util.Map;

/**
 * What's behind each widget: the phone's own wallpaper, blurred as if seen through frosted glass, so each widget's
 * glass shows the home screen behind it.
 *
 * Android doesn't let a widget blur what's behind it, so Worldly does it itself: it reads the home screen's wallpaper
 * (Android 11 and up only lets it with "All files access"; Worldly reads nothing but the wallpaper), shrinks and blurs
 * it once per wallpaper, and gives each panel the part of it that lies behind that panel on the screen. A widget
 * can't ask where it is on the screen either, but each tap on it says where it was tapped ({@link #saw}), so a widget
 * lines its glass up with the wallpaper the first time it's tapped; until then it's placed in the middle of the
 * screen, a good guess under this much blur.
 *
 * Without the wallpaper (not allowed, or a live wallpaper), the glass still takes the wallpaper's own colours, which
 * Android gives every app ({@link Behind#colours}).
 */
final class Backdrop {
    private Backdrop() {}

    /** The blurred wallpaper is kept at this fraction of the screen's size: the blur loses all finer detail anyway. */
    private static final int SCALE = 6;
    /** How often (ms) to look whether the wallpaper changed. */
    private static final long LOOK_MS = 3000;

    private static Bitmap blur;
    private static int[] colours;
    private static String wallpaperKey = "";
    private static long checkedAt;
    private static final Map<String, Rect> PLACES = new HashMap<>();
    private static Handler worker;

    /** One panel's backdrop: the blurred wallpaper and where in it the panel sits, and the wallpaper's colours. */
    static final class Behind {
        /** The blurred wallpaper (null when Worldly can't read it), and the panel's place in its pixels. */
        final Bitmap blur;
        final RectF region;
        /** The wallpaper's own colours (primary, secondary, tertiary), or null when Android gives none. */
        final int[] colours;
        /** How bright the wallpaper behind the panel is, 0 to 1: the glass is dimmed more over a bright one. */
        final float shade;
        /** What a picture drawn over this backdrop depends on, for caches. */
        final String key;

        Behind(Bitmap blur, RectF region, int[] colours, float shade, String key) {
            this.blur = blur;
            this.region = region;
            this.colours = colours;
            this.shade = shade;
            this.key = key;
        }
    }

    /** Whether Worldly may read the wallpaper: "All files access" on Android 11 and up, storage before that. */
    static boolean allowed(Context ctx) {
        try {
            if (Build.VERSION.SDK_INT >= 30) return Environment.isExternalStorageManager();
            return ctx.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        } catch (RuntimeException e) { // can't tell: treat it as not allowed
            return false;
        }
    }

    /**
     * The backdrop of one panel of a widget: the widget ({@code widget}: "currency", "calendar", "music", "sound",
     * "sbb") is {@code wDp} × {@code hDp}; the panel is {@code pwDp} × {@code phDp} at ({@code leftDp}, {@code topDp})
     * in it.
     */
    static Behind behind(Context ctx, String widget, float wDp, float hDp, float leftDp, float topDp, float pwDp,
                         float phDp) {
        Context app = ctx.getApplicationContext();
        refresh(app);
        int[] screen = screen(app);
        float d = app.getResources().getDisplayMetrics().density;
        Rect r = place(app, widget, screen[0]);
        float scale, x0, y0;
        if (r != null) {
            scale = r.width() / Math.max(1f, wDp * d);
            x0 = r.left;
            y0 = r.top;
        } else { // not tapped yet: across the middle of the screen
            scale = 1f;
            x0 = (screen[0] - wDp * d) / 2f;
            y0 = screen[1] * 0.42f - hDp * d / 2f;
        }
        RectF at = new RectF(x0 + leftDp * d * scale, y0 + topDp * d * scale, x0 + (leftDp + pwDp) * d * scale,
                y0 + (topDp + phDp) * d * scale);
        Bitmap b;
        int[] cs;
        String wk;
        synchronized (Backdrop.class) {
            b = blur;
            cs = colours;
            wk = wallpaperKey;
        }
        if (b == null) {
            return new Behind(null, null, cs, 0f, cs == null ? "none" : "c" + cs[0] + "," + cs[1] + "," + cs[2]);
        }
        float k = b.getWidth() / (float) screen[0];
        RectF region = new RectF(at.left * k, at.top * k, at.right * k, at.bottom * k);
        // keep it on the wallpaper (a guess, or a widget near the edge, can reach past it)
        if (region.left < 0) region.offset(-region.left, 0);
        if (region.right > b.getWidth()) region.offset(Math.max(-region.left, b.getWidth() - region.right), 0);
        if (region.top < 0) region.offset(0, -region.top);
        if (region.bottom > b.getHeight()) region.offset(0, Math.max(-region.top, b.getHeight() - region.bottom));
        String key = wk + "|" + Math.round(region.left * 4) + "," + Math.round(region.top * 4) + ","
                + Math.round(region.width() * 4) + "x" + Math.round(region.height() * 4);
        return new Behind(b, region, cs, shade(b, region), key);
    }

    /**
     * A widget was tapped: {@code bounds} (on the screen) is where its whole frame is, as worked out from the view that
     * was tapped. Remembered, and the widget is drawn again with its glass lined up, if it moved.
     */
    static void saw(Context ctx, String widget, Rect bounds) {
        if (bounds == null || bounds.width() <= 0) return;
        Context app = ctx.getApplicationContext();
        int sw = screen(app)[0];
        Rect was = place(app, widget, sw);
        if (was != null && Math.abs(was.left - bounds.left) < 6 && Math.abs(was.top - bounds.top) < 6
                && Math.abs(was.width() - bounds.width()) < 6) return;
        synchronized (PLACES) {
            PLACES.put(widget, new Rect(bounds));
        }
        Store.sp(app).edit().putString("place_" + widget, bounds.left + "," + bounds.top + "," + bounds.right + ","
                + bounds.bottom + "," + sw).apply();
        redraw(app, widget);
    }

    /** Where a widget's frame was last seen on a screen {@code screenWidth} wide (null: never, or turned since). */
    private static Rect place(Context app, String widget, int screenWidth) {
        synchronized (PLACES) {
            if (!PLACES.containsKey(widget)) {
                Rect r = null;
                String s = Store.sp(app).getString("place_" + widget, null);
                if (s != null) {
                    try {
                        String[] p = s.split(",");
                        if (Integer.parseInt(p[4]) == screenWidth) {
                            r = new Rect(Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2]),
                                    Integer.parseInt(p[3]));
                        }
                    } catch (RuntimeException ignored) { }
                }
                PLACES.put(widget, r);
            }
            Rect r = PLACES.get(widget);
            return r == null ? null : new Rect(r);
        }
    }

    /** Looks (at most every few seconds) whether the wallpaper, or the permission to read it, changed. */
    private static void refresh(Context app) {
        long now = SystemClock.uptimeMillis();
        synchronized (Backdrop.class) {
            if (checkedAt != 0 && now - checkedAt < LOOK_MS) return;
            checkedAt = now;
        }
        WallpaperManager wm = WallpaperManager.getInstance(app);
        String id;
        try {
            id = String.valueOf(wm.getWallpaperId(WallpaperManager.FLAG_SYSTEM));
        } catch (RuntimeException e) {
            id = "t" + (System.currentTimeMillis() / 600_000L); // can't tell: look again every 10 minutes
        }
        boolean may = allowed(app);
        String key = id + "|" + may;
        synchronized (Backdrop.class) {
            if (key.equals(wallpaperKey)) return;
        }
        int[] cs = readColours(wm);
        Bitmap b = may ? readBlur(app, wm) : null;
        synchronized (Backdrop.class) {
            wallpaperKey = key;
            colours = cs;
            blur = b;
        }
    }

    /** The wallpaper's own colours, which Android gives every app (null for none, or before Android 8.1). */
    private static int[] readColours(WallpaperManager wm) {
        if (Build.VERSION.SDK_INT < 27) return null;
        try {
            WallpaperColors c = wm.getWallpaperColors(WallpaperManager.FLAG_SYSTEM);
            if (c == null) return null;
            int p = c.getPrimaryColor().toArgb();
            int s = c.getSecondaryColor() != null ? c.getSecondaryColor().toArgb() : p;
            int t = c.getTertiaryColor() != null ? c.getTertiaryColor().toArgb() : s;
            return new int[]{p | 0xFF000000, s | 0xFF000000, t | 0xFF000000};
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** The wallpaper, as the home screen shows it (filling the screen, centred), shrunk and blurred; null if unreadable. */
    private static Bitmap readBlur(Context app, WallpaperManager wm) {
        Drawable d;
        try {
            d = wm.getDrawable();
        } catch (RuntimeException e) { // SecurityException without the permission, or no static wallpaper
            return null;
        }
        if (d == null) return null;
        int[] screen = screen(app);
        try {
            return blurred(d, screen[0], screen[1]);
        } catch (RuntimeException | OutOfMemoryError e) {
            return null;
        }
    }

    /** A wallpaper laid over a screen {@code sw} × {@code sh} (filling it, centred), at 1/{@link #SCALE}, blurred. */
    static Bitmap blurred(Drawable d, int sw, int sh) {
        int w = Math.max(16, sw / SCALE), h = Math.max(16, sh / SCALE);
        Bitmap small = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(small);
        int iw = d.getIntrinsicWidth(), ih = d.getIntrinsicHeight();
        if (iw <= 0 || ih <= 0) {
            iw = w;
            ih = h;
        }
        float s = Math.max(w / (float) iw, h / (float) ih);
        float dw = iw * s, dh = ih * s;
        d.setBounds(Math.round((w - dw) / 2), Math.round((h - dh) / 2), Math.round((w + dw) / 2), Math.round((h + dh) / 2));
        d.draw(c);
        int[] px = new int[w * h];
        small.getPixels(px, 0, w, 0, 0, w, h);
        int radius = Math.max(2, w / 18);
        for (int pass = 0; pass < 3; pass++) {
            boxBlur(px, w, h, radius, true);
            boxBlur(px, w, h, radius, false);
        }
        small.setPixels(px, 0, w, 0, 0, w, h);
        return small;
    }

    /** One pass of a box blur, {@code 2 radius + 1} wide, along the rows ({@code across}) or the columns. */
    private static void boxBlur(int[] v, int w, int h, int radius, boolean across) {
        int lines = across ? h : w, length = across ? w : h;
        int[] line = new int[length];
        int n = 2 * radius + 1;
        for (int a = 0; a < lines; a++) {
            for (int i = 0; i < length; i++) line[i] = v[across ? a * w + i : i * w + a];
            int sr = 0, sg = 0, sb = 0;
            for (int k = -radius; k <= radius; k++) {
                int c = line[Math.max(0, Math.min(length - 1, k))];
                sr += (c >> 16) & 0xFF;
                sg += (c >> 8) & 0xFF;
                sb += c & 0xFF;
            }
            for (int i = 0; i < length; i++) {
                v[across ? a * w + i : i * w + a] = 0xFF000000 | ((sr / n) << 16) | ((sg / n) << 8) | (sb / n);
                int in = line[Math.min(length - 1, i + radius + 1)], out = line[Math.max(0, i - radius)];
                sr += ((in >> 16) & 0xFF) - ((out >> 16) & 0xFF);
                sg += ((in >> 8) & 0xFF) - ((out >> 8) & 0xFF);
                sb += (in & 0xFF) - (out & 0xFF);
            }
        }
    }

    /** How bright the blurred wallpaper is over a region, 0 to 1 (a few samples, as luminance). */
    private static float shade(Bitmap b, RectF region) {
        float sum = 0f;
        int n = 0;
        for (int y = 0; y < 5; y++) {
            for (int x = 0; x < 5; x++) {
                int px = (int) Math.max(0, Math.min(b.getWidth() - 1, region.left + region.width() * (x + 0.5f) / 5));
                int py = (int) Math.max(0, Math.min(b.getHeight() - 1, region.top + region.height() * (y + 0.5f) / 5));
                int c = b.getPixel(px, py);
                sum += (0.2126f * Color.red(c) + 0.7152f * Color.green(c) + 0.0722f * Color.blue(c)) / 255f;
                n++;
            }
        }
        return n == 0 ? 0f : sum / n;
    }

    /** The screen's real size in pixels, as the wallpaper is laid over it. */
    private static int[] screen(Context app) {
        DisplayMetrics m = new DisplayMetrics();
        try {
            android.view.WindowManager wm = (android.view.WindowManager) app.getSystemService(Context.WINDOW_SERVICE);
            if (Build.VERSION.SDK_INT >= 30) {
                Rect b = wm.getMaximumWindowMetrics().getBounds();
                return new int[]{Math.max(1, b.width()), Math.max(1, b.height())};
            }
            wm.getDefaultDisplay().getRealMetrics(m);
        } catch (RuntimeException e) {
            m = app.getResources().getDisplayMetrics();
        }
        return new int[]{Math.max(1, m.widthPixels), Math.max(1, m.heightPixels)};
    }

    /** Draws a widget again (in the background), so its glass lines up with where it was just seen. */
    static void redraw(Context app, String widget) {
        Handler h;
        synchronized (Backdrop.class) {
            if (worker == null) {
                HandlerThread t = new HandlerThread("backdrop");
                t.start();
                worker = new Handler(t.getLooper());
            }
            h = worker;
        }
        h.post(() -> {
            try {
                switch (widget) {
                    case "currency": WidgetProvider.updateAll(app); break;
                    case "calendar": CalendarWidgetProvider.updateAll(app); break;
                    case "music": PlayerWidgetProvider.updateAll(app); break;
                    case "sound": SoundWidgetProvider.updateAll(app); break;
                    case "sbb": SbbWidgetProvider.updateAll(app); break;
                    default: break;
                }
            } catch (Throwable ignored) { }
        });
    }

    /** Every widget again (the permission or the wallpaper changed). */
    static void redrawAll(Context app) {
        synchronized (Backdrop.class) {
            checkedAt = 0; // look at the wallpaper again straight away
        }
        for (String w : new String[]{"currency", "calendar", "music", "sound", "sbb"}) redraw(app, w);
    }
}
