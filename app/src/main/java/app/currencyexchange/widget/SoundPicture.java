package app.currencyexchange.widget;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.text.TextPaint;
import android.text.TextUtils;

import java.util.Locale;
import java.util.Random;

/**
 * One Sound widget's picture, in the Deco launcher's look: the night glass panel with a wash of the
 * light; on top, the cover as an LP on the right for music (a square cover) and on the left what's
 * playing: a small label in spaced capitals ("Now playing · Spotify"), the title and the artist;
 * below, the wall of lamps from margin to margin, a little of it behind the LP; and room along the
 * bottom for the controls while something plays. Videos (a wide picture) and apps without a cover get the light show alone, in the app's
 * colour. With nothing playing, the wall plays its patterns in Worldly's amber.
 */
final class SoundPicture {
    /** Pictures are drawn at this many pixels per dp at most. */
    static final float MAX_DP = 2f;
    /** The room the controls take along the bottom while something plays (50 dp row, 8 dp below, 4 dp above). */
    static final float CONTROLS_DP = 62f;

    private final SoundLights lights = new SoundLights();
    private final SoundRecord record = new SoundRecord();
    private final Paint panel = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint wash = new Paint(Paint.DITHER_FLAG);
    private final Paint grain = new Paint();
    private final TextPaint text = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final Path outline = new Path();
    private final RectF box = new RectF();
    private final RectF wall = new RectF();
    private final RectF sleeve = new RectF();

    private NowPlaying.Track track;
    private Scene scene = Scene.amber();
    private boolean access = true;
    /** The cover, and a small copy of it for drawing every frame (players send them up to 1000 px or more). */
    private Bitmap coverFrom, coverSmall;

    SoundPicture() {
        edge.setStyle(Paint.Style.STROKE);
        edge.setColor(0x2EAADCFF);
        grain.setShader(new BitmapShader(grainTile(), Shader.TileMode.REPEAT, Shader.TileMode.REPEAT));
    }

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

        // The panel: night glass with a hairline edge, like the other widgets, lit by washes of the light.
        float radius = Math.min(28 * dp, h / 2f);
        box.set(0, 0, w, h);
        outline.rewind();
        outline.addRoundRect(box, radius, radius, Path.Direction.CW);
        c.save();
        c.clipPath(outline);
        panel.setColor(0xF0070A10);
        c.drawRect(box, panel);
        float big = Math.max(w, h);
        wash.setShader(new RadialGradient(w * 0.86f, 0, big * 0.62f,
                new int[]{Scene.alpha(scene.key, 0.16f + 0.08f * bass), Scene.alpha(scene.key, 0.05f), scene.key & 0x00FFFFFF},
                new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(box, wash);
        wash.setShader(new RadialGradient(w * 0.12f, h, big * 0.5f,
                new int[]{Scene.alpha(scene.key, 0.07f + 0.05f * bass), scene.key & 0x00FFFFFF}, null, Shader.TileMode.CLAMP));
        c.drawRect(box, wash);

        // Zones: on top, the cover with its LP on the right and the words on the left; below, the wall of lamps from
        // margin to margin, its top a little behind the LP; along the bottom, the controls (real views laid over
        // the picture, see SoundWidgetProvider) while something plays.
        float pad = 14 * dp;
        float bottom = track != null ? h - CONTROLS_DP * dp : h - pad;
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
        drawWords(ctx, c, pad, pad, wall.top - pad, textRight - pad, dp);
        c.drawRect(box, grain);
        c.restore();
        edge.setStrokeWidth(Math.max(1f, dp));
        c.drawRoundRect(new RectF(dp / 2, dp / 2, w - dp / 2, h - dp / 2), radius, radius, edge);
        return out;
    }

    /** The small label, the title and the artist, in the band at the top left, beside the LP. */
    private void drawWords(Context ctx, Canvas c, float x, float top, float band, float width, float dp) {
        String label, source = null, title, detail;
        if (!access) {
            label = "Sound";
            title = "Media access needed";
            detail = "Tap to turn it on in Worldly";
        } else if (track == null) {
            label = "Sound";
            title = "Nothing playing";
            detail = "Music from any app lights this up";
        } else {
            label = track.playing ? "Now playing" : "Paused";
            source = track.app;
            title = track.title.isEmpty() ? track.app : track.title;
            detail = track.artist;
        }
        // The Calendar widget's sizes: 9.5 dp labels, 15 dp titles, 11 dp second lines, at every widget size. Three
        // lines when there's room, else the label and the title; centred in the band.
        boolean three = detail != null && !detail.isEmpty() && band >= 37 * dp;
        float block = (three ? 39 : 26) * dp;
        // the label, in spaced capitals: what's happening in the light's colour, the app in grey
        float y = top + Math.max(0, (band - block) / 2) + 7.5f * dp;
        text.setTypeface(Theme.uiBold(ctx));
        text.setTextSize(9.5f * dp);
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
        // the title
        text.setLetterSpacing(0f);
        text.setTextSize(15 * dp);
        text.setColor(Theme.TEXT);
        y += 18.5f * dp;
        c.drawText(TextUtils.ellipsize(title, text, width, TextUtils.TruncateAt.END).toString(), x, y, text);
        // the artist
        if (three) {
            text.setTypeface(Theme.uiMedium(ctx));
            text.setTextSize(11 * dp);
            text.setColor(Theme.MUTED);
            y += 14.5f * dp;
            c.drawText(TextUtils.ellipsize(detail, text, width, TextUtils.TruncateAt.END).toString(), x, y, text);
        }
    }

    /** A tile of faint white specks, like film grain, as on the launcher's stage. */
    private static Bitmap grainTile() {
        int size = 96;
        int[] pixels = new int[size * size];
        Random random = new Random(7);
        for (int i = 0; i < pixels.length; i++) {
            float v = random.nextFloat();
            int a = v > 0.5f ? Math.round((v - 0.5f) * 2 * 12) : 0;
            pixels[i] = (a << 24) | 0x00FFFFFF;
        }
        return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888);
    }
}
