package app.currencyexchange.widget;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.text.TextPaint;

import java.util.Arrays;


/**
 * One Sound widget's picture, in the Deco launcher's look: the night glass panel with a wash of the
 * light; on top, the cover as an LP on the right for music (a square cover) and on the left what's
 * playing: a small label in spaced capitals ("Now playing · Spotify"), the title and the artist;
 * below, the wall of lamps from margin to margin, a little of it behind the LP, down to the bottom.
 * (The Music widget has the controls and how far through the song is.) Videos (a wide picture) and apps without a cover get the light show alone, in the app's
 * colour. With nothing playing, the wall plays its patterns in Worldly's amber.
 */
final class SoundPicture {
    /** Pictures are drawn at this many pixels per dp at most. */
    static final float MAX_DP = 2f;

    private final SoundLights lights = new SoundLights();
    private final SoundRecord record = new SoundRecord();
    private final TextPaint text = Words.paint();
    private final RectF box = new RectF();
    private final RectF wall = new RectF();
    private final RectF sleeve = new RectF();
    /** What's playing, written with its glow, drawn once and reused every frame until it changes. */
    private Bitmap words;
    private String wordsKey = "";
    /** A title too long for its room (2.27): the strip it scrolls along, and since when it shows. */
    private Bitmap strip;
    private String stripKey = "";
    private final float[] stripAt = new float[3];
    private long stripSince;
    private float titleY;
    private final android.graphics.Paint fade = new android.graphics.Paint();

    private NowPlaying.Track track;
    private Scene scene = Scene.idle();
    private boolean access = true;
    /** The cover, and a small copy of it for drawing every frame (players send them up to 1000 px or more). */
    private Bitmap coverFrom, coverSmall;
    /** When the LP's cover was last seen, and from which app (to ride over the gap between songs). */
    private long coverAt;
    private String coverApp;

    /** What's playing (null for nothing), its light, and whether Worldly may see other apps' media. */
    void set(NowPlaying.Track now, Scene light, boolean mediaAccess) {
        track = now;
        scene = light;
        access = mediaAccess;
        lights.setReactive(now != null && now.playing);
        if (now != null && now.hasCover()) {
            record.show(small(now.art), now.playing, now.song());
            record.setTint(light.key);
            coverAt = NowPlaying.now();
            coverApp = now.packageName;
        } else if (now != null && record.hasCover() && now.packageName.equals(coverApp)
                && NowPlaying.now() - coverAt < NowPlaying.BLIP_MS) {
            // The next song's title came before its cover: keep the LP as it is until the cover arrives, when the
            // record slides back, the sleeve changes and the new record slides out.
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

        // Its card, as every widget's: the mild see-through black, a hint of the light (swelling with the bass).
        float radius = Math.min(28 * dp, h / 2f);
        box.set(0, 0, w, h);
        float[] corners = Stage.round(radius);
        Stage.fill(c, box, corners, scene, dp, bass, null);

        // Zones: on top, the cover with its LP on the right and the words on the left; below, the wall of lamps from
        // margin to margin, its top a little behind the LP, down to the bottom margin.
        float pad = 14 * dp;
        float bottom = h - pad;
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
        c.drawBitmap(words(ctx, w, h, pad, wall.top - pad, textRight - pad, dp), 0, 0, null);
        if (strip != null) marquee(c, pad, textRight - pad, dp);
        c.restore();
        c.drawBitmap(Stage.edge(w, h, corners, dp), 0, 0, null); // its edge, drawn once per light and size
        return out;
    }

    /** The words with their glow, at the picture's top left: drawn again only when they, the light or the size change. */
    private Bitmap words(Context ctx, int w, int h, float pad, float band, float width, float dp) {
        String[] say = Words.of(track, access, "Sound", "Music from any app lights this up");
        int lw = Math.max(1, Math.min(w, (int) Math.ceil(pad + width + 14 * dp)));
        int lh = Math.max(1, Math.min(h, (int) Math.ceil(pad + band + 14 * dp)));
        String key = Arrays.toString(say) + '|' + scene.tag() + '|' + lw + 'x' + lh + '|' + band + '|' + width + '|' + dp;
        if (words == null || !key.equals(wordsKey)) {
            wordsKey = key;
            words = Bitmap.createBitmap(lw, lh, Bitmap.Config.ARGB_8888);
            Words.draw(ctx, new Canvas(words), text, say, scene, pad, pad, band, width, dp);
            titleY = Words.titleBaseline(ctx, text, pad, dp);
            // a title too long for its room scrolls across, as a media player's (2.27)
            if (Words.titleWidth(ctx, text, say[2], dp) > width) {
                String sk = say[2] + '|' + dp;
                if (strip == null || !sk.equals(stripKey)) {
                    strip = Words.strip(ctx, text, say[2], 40 * dp, dp, stripAt);
                    stripKey = sk;
                    stripSince = android.os.SystemClock.uptimeMillis();
                }
            } else {
                strip = null;
                stripKey = "";
            }
        }
        return words;
    }

    /**
     * The scrolling title (2.27): it waits a moment at the start, then slides left at an easy pace until its second copy
     * is where the first began, and starts again; its right edge fades out (and its left while it moves) so the words
     * come and go softly within their room, {@code width} pixels from {@code x}.
     */
    private void marquee(Canvas c, float x, float width, float dp) {
        float hold = 2.2f, speed = 30 * dp, run = stripAt[2];
        float cycle = hold + run / speed;
        float t = ((android.os.SystemClock.uptimeMillis() - stripSince) / 1000f) % cycle;
        float off = t < hold ? 0 : Math.min(run, (t - hold) * speed);
        float top = titleY - stripAt[0], bottom = top + strip.getHeight();
        int layer = c.saveLayer(x - 4 * dp, top, x + width, bottom, null);
        c.clipRect(x - 4 * dp, top, x + width, bottom);
        c.drawBitmap(strip, x - stripAt[1] - off, top, null);
        float edge = 18 * dp;
        fade.setXfermode(new android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.DST_OUT));
        fade.setShader(new android.graphics.LinearGradient(x + width - edge, 0, x + width, 0, 0x00000000, 0xFF000000,
                android.graphics.Shader.TileMode.CLAMP));
        c.drawRect(x + width - edge, top, x + width, bottom, fade);
        if (off > 0) {
            fade.setShader(new android.graphics.LinearGradient(x - 4 * dp, 0, x + edge, 0, 0xFF000000, 0x00000000,
                    android.graphics.Shader.TileMode.CLAMP));
            c.drawRect(x - 4 * dp, top, x + edge, bottom, fade);
        }
        fade.setShader(null);
        c.restoreToCount(layer);
    }
}
