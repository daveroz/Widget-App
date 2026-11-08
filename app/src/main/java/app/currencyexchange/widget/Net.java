package app.currencyexchange.widget;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** Tiny HTTP GET helper (no third-party libraries). */
final class Net {
    private Net() {}

    private static final String UA =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36";

    static String get(String url) throws IOException {
        return new String(fetch(url, null, false), StandardCharsets.UTF_8);
    }

    static byte[] getBytes(String url) throws IOException {
        return fetch(url, null, false);
    }

    /**
     * Plain request with the platform's default headers and nothing else, the same kind of request the
     * original KWGT formula made. Used for Visa, which answered KWGT but refused requests that claimed
     * to be a browser.
     */
    static String getPlain(String url) throws IOException {
        return new String(fetch(url, null, true), StandardCharsets.UTF_8);
    }

    private static byte[] fetch(String url, String referer, boolean plain) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        try {
            c.setConnectTimeout(8000);
            c.setReadTimeout(10000);
            if (!plain) {
                c.setRequestProperty("User-Agent", UA);
                c.setRequestProperty("Accept", "application/json, image/png, */*");
                c.setRequestProperty("Accept-Language", "en-US,en;q=0.9");
                if (referer != null) c.setRequestProperty("Referer", referer);
            }
            int code = c.getResponseCode();
            if (code < 200 || code >= 300) throw new IOException("HTTP " + code + " for " + url);
            try (InputStream in = c.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                return out.toByteArray();
            }
        } finally {
            c.disconnect();
        }
    }
}
