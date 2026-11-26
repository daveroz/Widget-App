package app.currencyexchange.widget;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.TextPaint;
import android.text.TextUtils;

import java.util.Locale;

/**
 * The Music widget's picture: the card every widget shares ({@link Stage#fill}, {@link Stage#rim}), in the light of
 * what's playing. The controls are real views laid over it ({@link PlayerWidgetProvider}). With nothing
 * playing (or no media access) it says so as a line of the app's Status tab (2.23): the note in a glass disc, what's up
 * in white capitals, what to do in grey.
 */
final class PlayerPicture {
    private PlayerPicture() {}

    static Bitmap draw(Context ctx, NowPlaying.Track track, Scene scene, boolean access, int w, int h, float dp) {
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        float radius = Math.min(28 * dp, h / 2f);
        RectF box = new RectF(0, 0, w, h);
        float[] corners = Stage.round(radius);
        // no light round the play button: it's a deep, solid button, as in the app (no glow)
        Stage.fill(c, box, corners, scene, dp, 0f, null);
        if (!access || track == null) {
            // as a line of the app's Status tab (2.23): the note in a glass disc, what's up in white capitals and what
            // to do in grey; in the card's middle, as the controls are (2.26)
            String title = (!access ? "Media access needed" : "Nothing playing").toUpperCase(Locale.getDefault());
            String detail = !access ? "Tap to turn it on in Worldly" : "Play something to control it here";
            float cy = h / 2f, x = 18 * dp, r = 21 * dp;
            Paint disc = new Paint(Paint.ANTI_ALIAS_FLAG);
            disc.setShader(Theme.crystal(new RectF(x, cy - r, x + 2 * r, cy + r), 0xFF1E222A));
            c.drawCircle(x + r, cy, r, disc);
            disc.setShader(null);
            disc.setStyle(Paint.Style.STROKE);
            disc.setStrokeWidth(Math.max(1f, 0.8f * dp));
            disc.setColor(Scene.alpha(Stage.ICE, 0.12f));
            c.drawCircle(x + r, cy, r - 0.4f * dp, disc);
            Glyph note = new Glyph(Glyph.NOTE, Theme.TEXT, false, 1.6f);
            int half = Math.round(11 * dp);
            note.setBounds(Math.round(x + r) - half, Math.round(cy) - half, Math.round(x + r) + half, Math.round(cy) + half);
            note.draw(c);
            float tx = x + 2 * r + 14 * dp, room = w - tx - 18 * dp;
            TextPaint text = Words.paint();
            text.setTypeface(Theme.uiBold(ctx));
            text.setTextSize(Theme.FONT_SCALE * Words.LABEL_DP * dp); // the family's label size (2.26)
            text.setLetterSpacing(0.16f);
            String head = TextUtils.ellipsize(title, text, room, TextUtils.TruncateAt.END).toString();
            Lens.text(c, text, head, tx, cy - 4 * dp, dp); // the fringe all text has, no glow
            text.setColor(Theme.TEXT);
            c.drawText(head, tx, cy - 4 * dp, text);
            text.setTypeface(Theme.uiMedium(ctx));
            text.setTextSize(Theme.FONT_SCALE * Words.DETAIL_DP * dp);
            text.setLetterSpacing(0f);
            String more = TextUtils.ellipsize(detail, text, room, TextUtils.TruncateAt.END).toString();
            Lens.text(c, text, more, tx, cy + 16 * dp, dp);
            text.setColor(Theme.MUTED);
            c.drawText(more, tx, cy + 16 * dp, text);
        }
        c.restore();
        Stage.rim(c, box, corners, dp);
        return out;
    }
}
