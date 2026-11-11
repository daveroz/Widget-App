package app.currencyexchange.widget;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.SweepGradient;

/**
 * What's playing, as a vinyl LP, as on the Deco launcher: the album cover is the sleeve on the right,
 * and while the song plays its record slides out to the left and spins at 33⅓ rpm. On pause it slows
 * to a stop. When the song changes, the record stops, slides back into the sleeve, the sleeve changes
 * to the new cover, and the new record slides out and spins.
 *
 * The record is pressed in the scene's colour, with fine grooves, and has the cover art on its label,
 * so the spin shows; a fixed sheen of light across it makes it read as vinyl. The sleeve glows softly
 * in the scene's key colour, like a cover lit on a stage.
 */
final class SoundRecord {
    static final float RPM = 100f / 3; // 33⅓
    /** Seconds the record takes to slide all the way out of the sleeve, or back in. */
    static final float SLIDE_SECONDS = 0.6f;
    private static final float FADE_SECONDS = 0.35f;
    /** The record's size, as a share of the sleeve's. */
    static final float DISC = 0.94f;
    /** The sleeve's corners: nearly square, like a real LP. */
    private static final float CORNER_DP = 3f;

    private final Paint sleevePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint edgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint artPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint discPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint sheenPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path clip = new Path();

    private Bitmap cover, previous, pending;
    private String song, pendingSong;
    private boolean playing;
    /** 0 = in the sleeve, 1 = all the way out. */
    private float slide;
    /** 0 = stopped, 1 = at full speed. */
    private float speed;
    private float angle;
    private float fade = 1f;
    private Bitmap disc;
    private String discKey = "";
    private int tint;

    SoundRecord() {
        edgePaint.setStyle(Paint.Style.STROKE);
        edgePaint.setColor(0x1AFFFFFF);
        sheenPaint.setShader(new SweepGradient(0, 0,
                new int[]{0x00FFFFFF, 0x24FFFFFF, 0x00FFFFFF, 0x00FFFFFF, 0x1AFFFFFF, 0x00FFFFFF, 0x00FFFFFF},
                new float[]{0f, 0.12f, 0.2f, 0.54f, 0.62f, 0.7f, 1f}));
    }

    /**
     * Shows this song's cover on the sleeve; the record comes out while {@code nowPlaying}. Only a
     * different song puts the record back and changes the sleeve. The same song with a new copy of its
     * cover (players resend it) just updates the sleeve in place.
     */
    void show(Bitmap art, boolean nowPlaying, String songKey) {
        playing = nowPlaying;
        if (cover == null) {
            song = songKey;
            setCoverNow(art);
        } else if (songKey.equals(pendingSong)) {
            pending = art;
        } else if (songKey.equals(song)) {
            pending = null;
            pendingSong = null;
            cover = art;
        } else {
            pending = art;
            pendingSong = songKey;
        }
    }

    /** No LP: the next cover appears straight away, without the record sliding back first. */
    void clear() {
        cover = previous = pending = null;
        song = pendingSong = null;
        slide = speed = 0f;
        fade = 1f;
    }

    boolean hasCover() {
        return cover != null;
    }

    /** The scene's colour: the record is pressed in it, so the LP shares the cover's colour with the light. */
    void setTint(int colour) {
        tint = colour;
    }

    /** Whether it's still moving (sliding, spinning or changing), so frames should keep coming. */
    boolean moving() {
        return pending != null || fade < 1f || speed > 0f || (playing && slide < 1f);
    }

    /** Moves the record on by {@code dt} seconds. */
    void step(float dt) {
        if (pending != null) {
            // A new song: stop, slide back into the sleeve, then change the cover.
            speed = approach(speed, 0f, dt, 0.25f);
            if (speed < 0.05f) {
                speed = 0f;
                slide = Math.max(0f, slide - dt / SLIDE_SECONDS);
                if (slide == 0f) {
                    song = pendingSong;
                    setCoverNow(pending);
                    pending = null;
                    pendingSong = null;
                }
            }
        } else if (playing) {
            // Out of the sleeve once the new cover has settled, then spin up.
            if (fade >= 1f && slide < 1f) slide = Math.min(1f, slide + dt / SLIDE_SECONDS);
            if (slide >= 1f) speed = approach(speed, 1f, dt, 0.5f);
        } else {
            speed = approach(speed, 0f, dt, 0.45f);
            if (speed < 0.002f) speed = 0f;
        }
        fade = Math.min(1f, fade + dt / FADE_SECONDS);
        angle = (angle + speed * RPM * 6 * dt) % 360f; // rpm × 360° / 60 s
    }

    /** All the way out and spinning at full speed (for previews). */
    void settle() {
        pending = null;
        fade = 1f;
        if (playing) {
            slide = 1f;
            speed = 1f;
        }
        angle = 40f;
    }

    private static float approach(float value, float target, float dt, float seconds) {
        return target + (value - target) * (float) Math.exp(-dt / seconds);
    }

    private void setCoverNow(Bitmap art) {
        previous = cover;
        cover = art;
        fade = previous == null ? 1f : 0f;
    }

    /** Draws the LP with its sleeve at {@code sleeve}; {@code dp} is pixels per dp. */
    void draw(Canvas canvas, RectF sleeve, float dp, Scene scene) {
        if (cover == null) return;
        float s = sleeve.height();
        float r = s * DISC / 2;
        buildDisc(Math.round(r * 2), dp);
        if (disc != null && slide > 0f) {
            float eased = slide * slide * (3 - 2 * slide);
            float cx = sleeve.centerX() + (sleeve.left - sleeve.centerX()) * eased;
            float cy = sleeve.centerY();
            canvas.save();
            canvas.translate(cx, cy);
            canvas.save();
            canvas.rotate(angle);
            canvas.drawBitmap(disc, -disc.getWidth() / 2f, -disc.getHeight() / 2f, discPaint);
            canvas.restore();
            canvas.drawCircle(0, 0, r, sheenPaint);
            canvas.restore();
        }

        // The sleeve, glowing softly in the key, on top of the record.
        float corner = CORNER_DP * dp;
        sleevePaint.setColor(0xFF0B1220);
        sleevePaint.setShadowLayer(14 * dp, 0, 3 * dp, Scene.alpha(scene.key, 0.36f));
        canvas.drawRoundRect(sleeve, corner, corner, sleevePaint);
        clip.rewind();
        clip.addRoundRect(sleeve, corner, corner, Path.Direction.CW);
        canvas.save();
        canvas.clipPath(clip);
        if (previous != null && fade < 1f) drawArt(canvas, previous, sleeve, Math.round(255 * (1 - fade)));
        drawArt(canvas, cover, sleeve, Math.round(255 * fade));
        canvas.restore();
        edgePaint.setStrokeWidth(dp);
        canvas.drawRoundRect(sleeve, corner, corner, edgePaint);
    }

    /** The record (vinyl, grooves and label) for the current cover, drawn once per cover, size and colour. */
    private void buildDisc(int size, float dp) {
        String key = System.identityHashCode(cover) + "|" + size + "|" + tint;
        if (key.equals(discKey) && disc != null) return;
        discKey = key;
        if (disc != null) disc.recycle();
        disc = null;
        if (size <= 0) return;
        float r = size / 2f;
        disc = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(disc);
        float cx = r;
        int vinyl = vinylFrom(tint != 0 ? tint : Theme.NIGHT);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        // Coloured vinyl, a little darker toward the middle.
        p.setShader(new RadialGradient(cx, cx, r, new int[]{darker(vinyl, 0.55f), darker(vinyl, 0.8f), vinyl},
                new float[]{0f, 0.6f, 1f}, Shader.TileMode.CLAMP));
        c.drawCircle(cx, cx, r, p);
        p.setShader(null);
        // Grooves, and two wider gaps between tracks.
        float label = r * 0.34f;
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(0.6f * dp);
        boolean dark = true;
        for (float g = label + 3 * dp; g < r - 2 * dp; g += 1.6f * dp) {
            p.setColor(dark ? 0x22000000 : 0x0AFFFFFF);
            c.drawCircle(cx, cx, g, p);
            dark = !dark;
        }
        p.setStrokeWidth(1.2f * dp);
        p.setColor(0x14FFFFFF);
        c.drawCircle(cx, cx, r * 0.62f, p);
        c.drawCircle(cx, cx, r * 0.81f, p);
        p.setStrokeWidth(dp);
        p.setColor(0x1FFFFFFF);
        c.drawCircle(cx, cx, r - 0.5f * dp, p); // the rim
        // The label: the cover art, cut round.
        c.save();
        Path round = new Path();
        round.addCircle(cx, cx, label, Path.Direction.CW);
        c.clipPath(round);
        drawArt(c, cover, new RectF(cx - label, cx - label, cx + label, cx + label), 255);
        c.restore();
        p.setColor(0x40000000);
        c.drawCircle(cx, cx, label, p);
        // The spindle hole.
        p.setStyle(Paint.Style.FILL);
        p.setColor(Theme.BG);
        c.drawCircle(cx, cx, Math.max(2 * dp, r * 0.03f), p);
    }

    /** Draws a cover into a square, filling it (cropped). */
    private void drawArt(Canvas canvas, Bitmap art, RectF into, int alpha) {
        int iw = art.getWidth(), ih = art.getHeight();
        if (iw <= 0 || ih <= 0) return;
        float scale = Math.max(into.width() / iw, into.height() / ih);
        float w = iw * scale, h = ih * scale;
        RectF dst = new RectF(into.centerX() - w / 2, into.centerY() - h / 2, into.centerX() + w / 2, into.centerY() + h / 2);
        artPaint.setAlpha(alpha);
        canvas.drawBitmap(art, null, dst, artPaint);
    }

    /** A deep coloured vinyl in this colour's hue. */
    private static int vinylFrom(int colour) {
        float[] hsv = new float[3];
        Color.colorToHSV(colour, hsv);
        hsv[1] = Math.min(1f, Math.max(0.45f, hsv[1]));
        hsv[2] = 0.55f;
        return Color.HSVToColor(0xF2, hsv); // slightly see-through, like coloured vinyl
    }

    private static int darker(int colour, float f) {
        return Color.argb(Color.alpha(colour), Math.round(Color.red(colour) * f),
                Math.round(Color.green(colour) * f), Math.round(Color.blue(colour) * f));
    }
}
