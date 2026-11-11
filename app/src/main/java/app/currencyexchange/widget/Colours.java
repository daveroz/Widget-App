package app.currencyexchange.widget;

import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;

import java.util.HashMap;
import java.util.Map;

/**
 * The primary colour of a picture (an album cover, an app's icon): the colour that covers the most of
 * it, among its real colours. Grey, white or black pictures have none. Players with a well-known
 * brand colour use it, as on the Deco launcher.
 */
final class Colours {
    private Colours() {}

    private static final Map<String, Integer> BRANDS = new HashMap<>();
    private static final Map<String, Integer> APPS = new HashMap<>();

    static {
        BRANDS.put("com.spotify.music", 0xFF1ED760);
        BRANDS.put("com.google.android.apps.youtube.music", 0xFFFF0000);
        BRANDS.put("com.google.android.youtube", 0xFFFF0000);
        BRANDS.put("com.apple.android.music", 0xFFFA243C);
        BRANDS.put("deezer.android.app", 0xFFA238FF);
        BRANDS.put("com.soundcloud.android", 0xFFFF5500);
        BRANDS.put("com.aspiro.tidal", 0xFF33FFEE);
        BRANDS.put("com.amazon.mp3", 0xFF25D1DA);
        BRANDS.put("com.pandora.android", 0xFF3668FF);
        BRANDS.put("com.netflix.mediaclient", 0xFFE50914);
        BRANDS.put("tv.twitch.android.app", 0xFF9146FF);
        BRANDS.put("com.audible.application", 0xFFF8991C);
        BRANDS.put("au.com.shiftyjelly.pocketcasts", 0xFFF43E37);
        BRANDS.put("com.google.android.apps.podcasts", 0xFF4285F4);
    }

    /** Slow-ish (draws and sorts a small copy): call it once per picture. */
    static int primary(Bitmap picture) {
        if (picture == null || picture.getWidth() <= 0 || picture.getHeight() <= 0) return Scene.NONE;
        Bitmap small;
        try {
            small = Bitmap.createScaledBitmap(picture.getConfig() == Bitmap.Config.HARDWARE
                    ? picture.copy(Bitmap.Config.ARGB_8888, false) : picture, 32, 32, true);
        } catch (RuntimeException e) {
            return Scene.NONE;
        }
        // Real colours, grouped by hue: the biggest group wins, and its average is the colour.
        int buckets = 24;
        int[] count = new int[buckets];
        long[] r = new long[buckets], g = new long[buckets], b = new long[buckets];
        float[] hsv = new float[3];
        for (int y = 0; y < 32; y++) {
            for (int x = 0; x < 32; x++) {
                int px = small.getPixel(x, y);
                if (Color.alpha(px) < 200) continue;
                Color.colorToHSV(px, hsv);
                if (hsv[1] < 0.3f || hsv[2] < 0.2f) continue; // grey, black or white
                int k = Math.min(buckets - 1, (int) (hsv[0] / 360f * buckets));
                count[k]++;
                r[k] += Color.red(px);
                g[k] += Color.green(px);
                b[k] += Color.blue(px);
            }
        }
        if (small != picture) small.recycle();
        int best = -1, bestScore = 0;
        for (int k = 0; k < buckets; k++) {
            if (count[k] == 0) continue;
            int score = 2 * count[k] + count[(k + 1) % buckets] + count[(k + buckets - 1) % buckets];
            if (score > bestScore) {
                best = k;
                bestScore = score;
            }
        }
        if (best < 0 || count[best] < 12) return Scene.NONE; // a few stray pixels aren't a colour
        int n = count[best];
        return Color.rgb((int) (r[best] / n), (int) (g[best] / n), (int) (b[best] / n));
    }

    /** An app's own colour: its brand colour, or its icon's primary colour. */
    static synchronized int app(Context ctx, String packageName) {
        if (packageName == null) return Scene.NONE;
        Integer brand = BRANDS.get(packageName);
        if (brand != null) return brand;
        Integer known = APPS.get(packageName);
        if (known != null) return known;
        int colour = Scene.NONE;
        try {
            Drawable icon = ctx.getPackageManager().getApplicationIcon(packageName);
            Bitmap b = Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888);
            icon.setBounds(0, 0, 48, 48);
            icon.draw(new Canvas(b));
            colour = primary(b);
            b.recycle();
        } catch (PackageManager.NameNotFoundException | RuntimeException ignored) { }
        APPS.put(packageName, colour);
        return colour;
    }
}
