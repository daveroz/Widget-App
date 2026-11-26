package app.currencyexchange.widget;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.text.TextPaint;
import android.text.TextUtils;

/**
 * What's playing, as the Sound widget writes it: the title, and the artist under it, at the top of the band they're
 * given (since 2.27 with no "Now playing" label over them). A title too long for its room scrolls across, as a media
 * player's does ({@link #strip}); the artist is cut short with an ellipsis.
 *
 * No line glows: every line has the lens's colour fringe instead (a red edge on one side, a blue one on the other;
 * see {@link Lens}). Draw the words once into a picture and reuse it, not every frame.
 */
final class Words {
    private Words() {}

    // the sizes of the other widgets' words (2.09; 2.13 and 2.14 a little larger); since 2.26 the artist as tall as the
    // Calendar's "Today, Wednesday", the title a bit bigger (LABEL_DP is the size of a label in the same family: the
    // Music widget's empty state)
    static final float LABEL_DP = 14f, TITLE_DP = 18f, DETAIL_DP = 14f;

    /** The lines for what's playing (or for nothing: {@code idle}, or for no access): {label, source, title, detail}. */
    static String[] of(NowPlaying.Track track, boolean access, String what, String idle) {
        if (!access) return new String[]{what, null, "Media access needed", "Tap to turn it on in Worldly"};
        if (track == null) return new String[]{what, null, "Nothing playing", idle};
        return new String[]{track.playing ? "Now playing" : "Paused", track.app,
                track.title.isEmpty() ? track.app : track.title, track.artist};
    }

    /**
     * The title's baseline in the band from {@code top}: its capitals 19.6 dp under the card's top (the Calendar's month
     * line), whatever the band's height.
     */
    static float titleBaseline(Context ctx, TextPaint text, float top, float dp) {
        text.setTypeface(Theme.uiMedium(ctx));
        text.setTextSize(Theme.FONT_SCALE * TITLE_DP * dp);
        text.setLetterSpacing(0f);
        Rect caps = new Rect();
        text.getTextBounds("H", 0, 1, caps);
        return top + Math.max(0f, 19.6f * dp - top) + caps.height();
    }

    /** How wide the title is drawn. */
    static float titleWidth(Context ctx, TextPaint text, String title, float dp) {
        text.setTypeface(Theme.uiMedium(ctx));
        text.setTextSize(Theme.FONT_SCALE * TITLE_DP * dp);
        text.setLetterSpacing(0f);
        return text.measureText(title == null ? "" : title);
    }

    /**
     * Draws the title (when it fits {@code width}; else see {@link #strip}) and the artist under it, at {@code x} in
     * the band from {@code top}, {@code band} tall.
     */
    static void draw(Context ctx, Canvas c, TextPaint text, String[] w, Scene scene, float x, float top, float band,
                     float width, float dp) {
        String title = w[2], detail = w[3];
        boolean two = detail != null && !detail.isEmpty() && band >= 36 * dp;
        Typeface regular = Theme.uiMedium(ctx);
        float titleY = titleBaseline(ctx, text, top, dp), detailY = titleY + 20 * dp;
        boolean fits = titleWidth(ctx, text, title, dp) <= width;
        String more = null;
        if (two) {
            text.setTypeface(regular);
            text.setTextSize(Theme.FONT_SCALE * DETAIL_DP * dp);
            more = TextUtils.ellipsize(detail, text, width, TextUtils.TruncateAt.END).toString();
        }
        // twice: first the fringe of every line (so no fringe lies over another line's letters), then the letters
        for (int pass = 0; pass < 2; pass++) {
            boolean fringe = pass == 0;
            text.setTypeface(regular);
            if (fits) {
                text.setTextSize(Theme.FONT_SCALE * TITLE_DP * dp);
                line(c, text, title, x, titleY, Theme.TEXT, fringe, dp);
            }
            if (more != null) {
                text.setTextSize(Theme.FONT_SCALE * DETAIL_DP * dp);
                line(c, text, more, x, detailY, Theme.MUTED, fringe, dp); // the artist, in the app's neutral grey
            }
        }
    }

    /**
     * A title too long for its room, as a strip to scroll (2.27): the title, a gap of {@code gap} pixels, and the
     * title again, in white with its fringe; the baseline {@code baseline} pixels down the strip. Scrolled by its
     * width less one copy, it comes back to where it started.
     */
    static Bitmap strip(Context ctx, TextPaint text, String title, float gap, float dp, float[] baseline) {
        text.setTypeface(Theme.uiMedium(ctx));
        text.setTextSize(Theme.FONT_SCALE * TITLE_DP * dp);
        text.setLetterSpacing(0f);
        Paint.FontMetrics fm = text.getFontMetrics();
        float tw = text.measureText(title), room = 3 * dp; // room for the fringe
        int w = (int) Math.ceil(2 * tw + gap + 2 * room), h = (int) Math.ceil(fm.descent - fm.ascent + 2 * room);
        Bitmap b = Bitmap.createBitmap(Math.max(1, w), Math.max(1, h), Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        float y = room - fm.ascent;
        for (int pass = 0; pass < 2; pass++) {
            line(c, text, title, room, y, Theme.TEXT, pass == 0, dp);
            line(c, text, title, room + tw + gap, y, Theme.TEXT, pass == 0, dp);
        }
        baseline[0] = y;
        baseline[1] = room;
        baseline[2] = tw + gap; // how far it scrolls before it repeats
        return b;
    }

    /** One line of text: its fringe ({@code fringe}; see {@link Lens#text}), or its letters in {@code colour}. */
    private static void line(Canvas c, TextPaint text, String s, float x, float y, int colour, boolean fringe, float dp) {
        if (fringe) {
            Lens.text(c, text, s, x, y, dp);
            return;
        }
        text.setColor(colour);
        c.drawText(s, x, y, text);
    }

    static TextPaint paint() {
        return new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    }
}
