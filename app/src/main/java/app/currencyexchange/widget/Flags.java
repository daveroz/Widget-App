package app.currencyexchange.widget;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Flag + Visa-logo images: downloaded once, then read from app storage (works offline). */
final class Flags {
    private Flags() {}

    private static final String FLAG_URL =
            "https://raw.githubusercontent.com/google/region-flags/refs/heads/gh-pages/png/";
    private static final String VISA_URL =
            "https://raw.githubusercontent.com/slaterjohn/payment-logos/master/Rounded%20Corners/PNG/medium/visa%402x.png";

    private static final Map<String, Bitmap> MEM = new HashMap<>();

    private static File flagFile(Context c, String cc) {
        File d = new File(c.getFilesDir(), "flags");
        //noinspection ResultOfMethodCallIgnored
        d.mkdirs();
        return new File(d, cc.toUpperCase(Locale.ROOT) + ".png");
    }

    private static File visaFile(Context c) { return new File(c.getFilesDir(), "visa.png"); }

    /** Downloads the flag if missing. Network: call from a background thread only. */
    static void ensureFlag(Context c, String cc) {
        if (cc == null || cc.length() != 2) return;
        File f = flagFile(c, cc);
        if (f.exists() && f.length() > 0) return;
        if (!download(FLAG_URL + cc.toUpperCase(Locale.ROOT) + ".png", f)) {
            download("https://flagcdn.com/w320/" + cc.toLowerCase(Locale.ROOT) + ".png", f); // free mirror
        }
    }

    static void ensureVisa(Context c) {
        File f = visaFile(c);
        if (f.exists() && f.length() > 0) return;
        download(VISA_URL, f);
    }

    private static boolean download(String url, File dest) {
        try {
            byte[] b = Net.getBytes(url);
            try (FileOutputStream o = new FileOutputStream(dest)) { o.write(b); }
            return true;
        } catch (Exception ignored) { /* retried on next refresh */ }
        return false;
    }

    static synchronized Bitmap flag(Context c, String cc) {
        if (cc == null || cc.length() != 2) return null;
        return cached("f_" + cc.toLowerCase(Locale.ROOT), flagFile(c, cc));
    }

    static synchronized Bitmap visa(Context c) { return cached("visa", visaFile(c)); }

    private static Bitmap cached(String k, File f) {
        Bitmap b = MEM.get(k);
        if (b != null) return b;
        if (!f.exists()) return null;
        b = BitmapFactory.decodeFile(f.getAbsolutePath());
        if (b != null) MEM.put(k, b);
        return b;
    }
}
