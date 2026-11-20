package app.currencyexchange.widget;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.TextPaint;

import java.util.Locale;

/**
 * The Music widget's picture: the panel every widget sits on, lit by the light of what's playing, in its haze and
 * with a fine film grain. The controls are real views laid over it ({@link PlayerWidgetProvider}). With nothing
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
        Stage.paint(c, box, corners, scene, 0f, Stage.NIGHT, Stage.NIGHT, 0f, true);
        Stage.haze(c, box, scene);
        Stage.glass(c, box, scene);
        if (!access || track == null) {
            String say = (!access ? "Media access needed  ·  tap to turn it on" : "Nothing playing").toUpperCase(Locale.getDefault());
            TextPaint text = Words.paint();
            text.setTypeface(Theme.uiBold(ctx));
            text.setTextSize(Words.LABEL_DP * dp);
            text.setLetterSpacing(0.22f);
            text.setTextAlign(Paint.Align.CENTER);
            int colour = Glow.label(scene);
            float y = h / 2f + Words.LABEL_DP * dp * 0.36f;
            Glow.text(c, text, say, w / 2f, y, colour, scene.deep, Glow.LABEL, 12 * dp);
            text.setColor(colour);
            c.drawText(say, w / 2f, y, text);
        }
        Stage.film(c, box);
        c.restore();
        Stage.edge(c, box, corners, Math.max(1f, dp), Theme.BORDER);
        return out;
    }
}
