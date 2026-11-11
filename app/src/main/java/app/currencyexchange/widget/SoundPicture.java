package app.currencyexchange.widget;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.text.TextPaint;


/**
 * One Sound widget's picture, in the Deco launcher's look: the night glass panel with a wash of the
 * light; on top, the cover as an LP on the right for music (a square cover) and on the left what's
 * playing: a small label in spaced capitals ("Now playing · Spotify"), the title and the artist;
 * below, the wall of lamps from margin to margin, a little of it behind the LP; and a progress line
 * lit in the light along the bottom. (The Music widget has the controls.) Videos (a wide picture) and apps without a cover get the light show alone, in the app's
 * colour. With nothing playing, the wall plays its patterns in Worldly's amber.
 */
final class SoundPicture {
    /** Pictures are drawn at this many pixels per dp at most. */
    static final float MAX_DP = 2f;

    private final SoundLights lights = new SoundLights();
    private final SoundRecord record = new SoundRecord();
    private final TextPaint text = Words.paint();
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF box = new RectF();
    private final RectF wall = new RectF();
    private final RectF sleeve = new RectF();

    private NowPlaying.Track track;
    private Scene scene = Scene.amber();
    private boolean access = true;
    /** The cover, and a small copy of it for drawing every frame (players send them up to 1000 px or more). */
    private Bitmap coverFrom, coverSmall;

    /** What's playing (null for nothing), its light, and whether Worldly may see other apps' media. */
    void set(NowPlaying.Track now, Scene light, boolean mediaAccess) {
        track = now;
        scene = light;
        access = mediaAccess;
        lights.setReactive(now != null && now.playing);
        if (now != null && now.hasCover()) {
            record.show(small(now.art), now.playing, now.song());
            record.setTint(light.key);
        } else {
            record.clear();
        }
    }

    private Bitmap small(Bitmap art) {
        if (art == coverFrom && coverSmall != null) return coverSmall;
        coverFrom = art;
        coverSmall = art;
        int longest = Math.max(art.getWidth(), art.getHeight());
        if (longest > 320) {
            try {
                Bitmap soft = art.getConfig() == Bitmap.Config.HARDWARE ? art.copy(Bitmap.Config.ARGB_8888, false) : art;
                float f = 320f / longest;
                coverSmall = Bitmap.createScaledBitmap(soft, Math.max(1, Math.round(art.getWidth() * f)),
                        Math.max(1, Math.round(art.getHeight() * f)), true);
            } catch (RuntimeException ignored) {
                coverSmall = art;
            }
        }
        return coverSmall;
    }

    /** Moves everything on by {@code dt} seconds. */
    void step(float dt, SoundWave wave) {
        lights.step(dt, wave);
        record.step(dt);
    }

    /** Whether frames should come quickly: following the sound, or the record moving. */
    boolean busy() {
        return lights.isReactive() || lights.blending() || record.moving();
    }

    SoundLights lights() {
        return lights;
    }

    SoundRecord record() {
        return record;
    }

    /** Draws the picture at {@code w} × {@code h} pixels, {@code dp} pixels per dp, into {@code reuse} if it fits. */
    Bitmap draw(Context ctx, int w, int h, float dp, Bitmap reuse) {
        Bitmap out = reuse != null && reuse.getWidth() == w && reuse.getHeight() == h && reuse.isMutable()
                ? reuse : Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        out.eraseColor(0);
        Canvas c = new Canvas(out);
        float bass = lights.bass();

        // The panel every widget sits on: night glass lit by washes of the light (swelling with the bass), with grain.
        float radius = Math.min(28 * dp, h / 2f);
        box.set(0, 0, w, h);
        float[] corners = Stage.round(radius);
        Stage.paint(c, box, corners, scene, bass, Stage.NIGHT, Stage.NIGHT, 0f, true);

        // Zones: on top, the cover with its LP on the right and the words on the left; below, the wall of lamps from
        // margin to margin, its top a little behind the LP; along the bottom, how far through.
        float pad = 14 * dp;
        boolean progress = track != null && track.duration > 0;
        float bottom = h - pad - (progress ? 18 * dp : 0);
        // The LP grows with a taller widget, up to about a third of its width.
        float most = Math.max(84 * dp, Math.min(w * 0.34f, 130 * dp));
        float size = Math.max(28 * dp, Math.min((bottom - pad) * 0.56f, most));
        float overlap = size * 0.2f;
        sleeve.set(w - pad - size, pad, w - pad, pad + size);
        wall.set(pad, pad + size - overlap, w - pad, bottom);
        boolean lp = record.hasCover();
        float textRight = lp ? sleeve.left - size * SoundRecord.DISC / 2 - 10 * dp : w - pad;
        // The whole wall follows the music (the LP only covers its top), and the words are above it, so no lamps dim.
        lights.layout(wall, wall.left - 80 * dp, Float.MAX_VALUE, w, h, dp);
        lights.draw(c, scene);

        record.draw(c, sleeve, dp, scene);
        Words.draw(ctx, c, text, Words.of(track, access, "Sound", "Music from any app lights this up"), scene, pad, pad, wall.top - pad, textRight - pad, dp);
        if (progress) drawProgress(ctx, c, pad, w - pad, h - pad - 4 * dp, dp);
        c.restore();
        Stage.edge(c, box, corners, Math.max(1f, dp));
        return out;
    }

    /** How far through: the time so far, a hairline lit from the left in the light, and the length. */
    private void drawProgress(Context ctx, Canvas c, float from, float to, float y, float dp) {
        long at = track.positionAt(NowPlaying.now());
        String done = SoundControls.clock(at), all = SoundControls.clock(track.duration);
        text.setTypeface(Theme.uiMedium(ctx));
        text.setTextSize(11 * dp);
        text.setLetterSpacing(0.04f);
        text.setColor(Theme.MUTED);
        float baseline = y + 4 * dp;
        c.drawText(done, from, baseline, text);
        float allW = text.measureText(all);
        c.drawText(all, to - allW, baseline, text);
        text.setLetterSpacing(0f);
        float x0 = from + text.measureText("00:00") + 8 * dp, x1 = to - allW - 8 * dp;
        if (x1 <= x0) return;
        float f = Math.max(0f, Math.min(1f, at / (float) track.duration));
        float head = x0 + (x1 - x0) * f;
        line.setShader(null);
        line.setStrokeCap(Paint.Cap.ROUND);
        line.setColor(Theme.BORDER_STRONG);
        line.setStrokeWidth(Math.max(1f, dp));
        c.drawLine(x0, y, x1, y, line);
        if (head <= x0) return;
        line.setColor(Scene.alpha(scene.key, 0.22f));
        line.setStrokeWidth(5 * dp);
        c.drawLine(x0, y, head, y, line); // the glow around it
        line.setShader(new LinearGradient(x0, 0, Math.max(x0 + 1, head), 0, Scene.alpha(scene.key, 0.5f), scene.key,
                Shader.TileMode.CLAMP));
        line.setStrokeWidth(1.6f * dp);
        c.drawLine(x0, y, head, y, line);
        line.setShader(null);
        line.setColor(scene.led);
        c.drawCircle(head, y, 2f * dp, line);
    }
}
