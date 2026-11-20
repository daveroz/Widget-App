package app.currencyexchange.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.text.TextPaint;
import android.text.TextUtils;

import java.util.Locale;

/**
 * What's playing, as the Sound and Music widgets write it: a small label in spaced capitals ("Now playing" in the
 * light's colour, "· Spotify" in grey), the title, and the artist, centred in the band they're given. Three lines
 * when there's room, else the label and the title.
 *
 * No line glows: every line has the lens's colour fringe instead (a red edge on one side, a blue one on the other;
 * see {@link Lens}), the title a warm white. Draw the words once into a picture and reuse it, not every frame.
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
        Typeface bold = Theme.uiBold(ctx), regular = Theme.uiMedium(ctx);

        // Lay the lines out first.
        text.setTypeface(bold);
        text.setTextSize(LABEL_DP * dp);
        text.setLetterSpacing(0.22f);
        String head = label.toUpperCase(Locale.getDefault());
        float used = text.measureText(head);
        String tail = null;
        if (source != null && !source.isEmpty()) {
            tail = "  ·  " + source.toUpperCase(Locale.getDefault());
            if (text.measureText(tail) > width - used) tail = null; // the app's name only when it fits whole
        }
        text.setLetterSpacing(0f);
        text.setTextSize(TITLE_DP * dp);
        String name = TextUtils.ellipsize(title, text, width, TextUtils.TruncateAt.END).toString();
        String more = null;
        if (three) {
            text.setTypeface(regular);
            text.setTextSize(DETAIL_DP * dp);
            more = TextUtils.ellipsize(detail, text, width, TextUtils.TruncateAt.END).toString();
        }
        float titleY = y + 20 * dp, detailY = titleY + 16 * dp;
        int labelColour = Glow.label(scene);
        int titleColour = Scene.mix(Theme.TEXT, scene.hot, 0.3f); // a warm white, lit by the key (warmer than elsewhere: it's the star)
        int detailColour = Glow.muted(scene);

        // Then twice: first the fringe of every line (so no fringe lies over another line's letters), then the letters.
        for (int pass = 0; pass < 2; pass++) {
            boolean fringe = pass == 0;
            text.setTypeface(bold);
            text.setTextSize(LABEL_DP * dp);
            text.setLetterSpacing(0.22f);
            line(c, text, head, x, y, labelColour, fringe, dp);
            if (tail != null) line(c, text, tail, x + used, y, Theme.DIM, fringe, dp);
            text.setLetterSpacing(0f);
            text.setTextSize(TITLE_DP * dp);
            line(c, text, name, x, titleY, titleColour, fringe, dp);
            if (more != null) {
                text.setTypeface(regular);
                text.setTextSize(DETAIL_DP * dp);
                line(c, text, more, x, detailY, detailColour, fringe, dp);
            }
        }
    }

    /** One line of text: its fringe ({@code fringe}; see {@link Lens#text}), or its letters in {@code colour}. */
    private static void line(Canvas c, TextPaint text, String s, float x, float y, int colour, boolean fringe, float dp) {
        if (fringe) {
            Lens.text(c, text, s, x, y, dp, Look.get().textFringe);
            return;
        }
        text.setColor(colour);
        c.drawText(s, x, y, text);
    }

    static TextPaint paint() {
        return new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    }
}
