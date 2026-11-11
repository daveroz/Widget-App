package app.currencyexchange.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.TextPaint;
import android.text.TextUtils;

import java.util.Locale;

/**
 * What's playing, as the Sound and Music widgets write it: a small label in spaced capitals ("Now playing" in the
 * light's colour, "· Spotify" in grey), the title, and the artist, centred in the band they're given. Three lines
 * when there's room, else the label and the title.
 */
final class Words {
    private Words() {}

    static final float LABEL_DP = 10.5f, TITLE_DP = 16.5f, DETAIL_DP = 12f;

    /** The three lines for what's playing (or for nothing: {@code idle}, or for no access). */
    static String[] of(NowPlaying.Track track, boolean access, String what, String idle) {
        if (!access) return new String[]{what, null, "Media access needed", "Tap to turn it on in Worldly"};
        if (track == null) return new String[]{what, null, "Nothing playing", idle};
        return new String[]{track.playing ? "Now playing" : "Paused", track.app,
                track.title.isEmpty() ? track.app : track.title, track.artist};
    }

    /** Draws {label, source, title, detail} at {@code x} in the band from {@code top}, {@code band} tall, {@code width} wide. */
    static void draw(Context ctx, Canvas c, TextPaint text, String[] w, Scene scene, float x, float top, float band,
                     float width, float dp) {
        String label = w[0], source = w[1], title = w[2], detail = w[3];
        boolean three = detail != null && !detail.isEmpty() && band >= 42 * dp;
        float block = (three ? 44 : 29) * dp;
        float y = top + Math.max(0, (band - block) / 2) + 8 * dp;
        text.setTypeface(Theme.uiBold(ctx));
        text.setTextSize(LABEL_DP * dp);
        text.setLetterSpacing(0.22f);
        String head = label.toUpperCase(Locale.getDefault());
        text.setColor(scene.label);
        c.drawText(head, x, y, text);
        if (source != null && !source.isEmpty()) {
            float used = text.measureText(head);
            String tail = "  ·  " + source.toUpperCase(Locale.getDefault());
            if (text.measureText(tail) <= width - used) { // the app's name only when it fits whole
                text.setColor(Theme.DIM);
                c.drawText(tail, x + used, y, text);
            }
        }
        text.setLetterSpacing(0f);
        text.setTextSize(TITLE_DP * dp);
        text.setColor(Theme.TEXT);
        y += 20 * dp;
        c.drawText(TextUtils.ellipsize(title, text, width, TextUtils.TruncateAt.END).toString(), x, y, text);
        if (three) {
            text.setTypeface(Theme.uiMedium(ctx));
            text.setTextSize(DETAIL_DP * dp);
            text.setColor(Theme.MUTED);
            y += 16 * dp;
            c.drawText(TextUtils.ellipsize(detail, text, width, TextUtils.TruncateAt.END).toString(), x, y, text);
        }
    }

    static TextPaint paint() {
        return new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    }
}
