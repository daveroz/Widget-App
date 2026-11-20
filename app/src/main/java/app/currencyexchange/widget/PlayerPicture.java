package app.currencyexchange.widget;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.TextPaint;

import java.util.Locale;

/**
 * The Music widget's picture: the card every widget shares ({@link Stage#fill}, {@link Stage#rim}), in the light of
 * what's playing. The controls are real views laid over it ({@link PlayerWidgetProvider}). With nothing
 * playing (or no media access) it says so, quietly, in the middle, the words glowing in the light.
 */
final class PlayerPicture {
    private PlayerPicture() {}

    static Bitmap draw(Context ctx, NowPlaying.Track track, Scene scene, boolean access, int w, int h, float dp) {
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        float radius = Math.min(28 * dp, h / 2f);
        RectF box = new RectF(0, 0, w, h);
        float[] corners = Stage.round(radius);
        // while there are controls, the play button lights the card round it (10 dp margin, the 46 dp previous
        // button, half the 50 dp play button; the controls are centred top to bottom)
        Stage.Spill[] spills = access && track != null
                ? new Stage.Spill[]{new Stage.Spill(81 * dp, h / 2f, 46 * dp, scene.deep, 0.28f * Look.get().bloom)} : null;
        Stage.fill(c, box, corners, scene, dp, 0f, spills);
        if (!access || track == null) {
            String say = (!access ? "Media access needed  ·  tap to turn it on" : "Nothing playing").toUpperCase(Locale.getDefault());
            TextPaint text = Words.paint();
            text.setTypeface(Theme.uiBold(ctx));
            text.setTextSize(Words.LABEL_DP * dp);
            text.setLetterSpacing(0.22f);
            text.setTextAlign(Paint.Align.CENTER);
            int colour = Glow.label(scene);
            float y = h / 2f + Words.LABEL_DP * dp * 0.36f;
            Glow.text(c, text, say, w / 2f, y, colour, scene.deep, Glow.LABEL * Look.get().bloom, 12 * dp);
            text.setColor(colour);
            c.drawText(say, w / 2f, y, text);
        }
        c.restore();
        Stage.rim(c, box, corners, scene, dp);
        return out;
    }
}
