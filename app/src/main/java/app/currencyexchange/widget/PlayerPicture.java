package app.currencyexchange.widget;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.text.TextPaint;

/**
 * The Music widget's picture: the panel every widget sits on, lit by the light of what's playing; on the left the
 * cover, glowing softly in that light like a cover on a stage, and beside it what's playing (label, title, artist).
 * The bottom is left for the controls, which are real views laid over the picture ({@link PlayerWidgetProvider}).
 */
final class PlayerPicture {
    private PlayerPicture() {}

    /** The room the controls take along the bottom (50 dp row, 8 dp below, 4 dp above). */
    static final float CONTROLS_DP = 62f;

    static Bitmap draw(Context ctx, NowPlaying.Track track, Scene scene, boolean access, int w, int h, float dp) {
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        float radius = Math.min(28 * dp, h / 2f);
        RectF box = new RectF(0, 0, w, h);
        float[] corners = Stage.round(radius);
        Stage.paint(c, box, corners, scene, 0f, Stage.NIGHT, Stage.NIGHT, 0f, true);

        float pad = 14 * dp;
        float bottom = track != null ? h - CONTROLS_DP * dp : h - pad;
        float band = Math.max(0, bottom - pad);
        float x = pad;
        if (track != null && track.art != null && band >= 24 * dp) {
            float size = Math.min(band, 88 * dp);
            RectF cover = new RectF(pad, pad + (band - size) / 2, pad + size, pad + (band + size) / 2);
            drawCover(c, track.art, cover, scene, dp);
            x = cover.right + 14 * dp;
        }
        TextPaint text = Words.paint();
        Words.draw(ctx, c, text, Words.of(track, access, "Music", "Play something in any app"), scene, x, pad, band, w - pad - x, dp);
        c.restore();
        Stage.edge(c, box, corners, Math.max(1f, dp));
        return out;
    }

    /** The cover, cropped square with soft corners, glowing in the light. */
    private static void drawCover(Canvas c, Bitmap art, RectF into, Scene scene, float dp) {
        float r = 6 * dp;
        Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
        glow.setColor(0xFF0B1220);
        glow.setShadowLayer(14 * dp, 0, 3 * dp, Scene.alpha(scene.key, 0.4f));
        c.drawRoundRect(into, r, r, glow);
        c.save();
        Path clip = new Path();
        clip.addRoundRect(into, r, r, Path.Direction.CW);
        c.clipPath(clip);
        int iw = art.getWidth(), ih = art.getHeight();
        if (iw > 0 && ih > 0) {
            float scale = Math.max(into.width() / iw, into.height() / ih);
            float aw = iw * scale, ah = ih * scale;
            RectF dst = new RectF(into.centerX() - aw / 2, into.centerY() - ah / 2, into.centerX() + aw / 2, into.centerY() + ah / 2);
            c.drawBitmap(art, null, dst, new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG));
        }
        c.restore();
        Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
        edge.setStyle(Paint.Style.STROKE);
        edge.setStrokeWidth(dp);
        edge.setColor(0x1AFFFFFF);
        c.drawRoundRect(into, r, r, edge);
    }
}
