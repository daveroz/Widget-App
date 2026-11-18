package app.currencyexchange.widget;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
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
 * The record is pressed in the scene's colour, luminous like lit coloured vinyl, with fine grooves, and has the cover
 * art on its label, so the spin shows. Warm light catches in its grooves, in two soft wedges that stay where the light
 * is while the record turns (so it reads as vinyl), and a soft luminous halo rings its edge, as if the disc gave off
 * light into the haze. It only changes colour while it's back in the sleeve, so each song's record comes out as a
 * different disc. The sleeve glows in the scene's key colour, like a cover lit on a stage, with a warm rim of light
 * just outside it. The cover art itself, on the sleeve and on the label, is shown as it is: no light or grain on it.
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
    private final Paint haloPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    /** Light over light: screened, so it brightens and never darkens. */
    private final Paint screenPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Path clip = new Path();
    /** The label's size, as a share of the record's radius. */
    private static final float LABEL = 0.34f;
    /** The halo's reach beyond the record's edge, as a share of its radius. */
    private static final float HALO = 1.34f;

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
    /** The light caught in the grooves, drawn still over the turning record. */
    private Bitmap glint;
    /** Where the cover art was last drawn: the sleeve, and the label (its middle and radius; radius 0 when hidden). */
    private final RectF artSleeve = new RectF();
    private final Path artShape = new Path();
    private float labelX, labelY, labelR;
    /** The colour the record is pressed in, and the one it will be pressed in next time it's out of sight. */
    private int tint, wantTint;

    SoundRecord() {
        edgePaint.setStyle(Paint.Style.STROKE);
        screenPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SCREEN));
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

    /**
     * The scene's colour: the record is pressed in it, so the LP shares the cover's colour with the light. A record
     * only changes colour while it's back in its sleeve, out of sight: the next one comes out in its own colour,
     * like a different disc, rather than the one on show changing colour.
     */
    void setTint(int colour) {
        wantTint = colour;
        if (slide == 0f) tint = colour;
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
        if (slide == 0f) tint = wantTint; // in its sleeve, out of sight: the next one out is in the new colour
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
        labelR = 0f;
        if (disc != null && slide > 0f) {
            float eased = slide * slide * (3 - 2 * slide);
            float cx = sleeve.centerX() + (sleeve.left - sleeve.centerX()) * eased;
            float cy = sleeve.centerY();
            labelX = cx;
            labelY = cy;
            labelR = r * LABEL;
            canvas.save();
            canvas.translate(cx, cy);
            canvas.save();
            canvas.rotate(angle);
            canvas.drawBitmap(disc, -disc.getWidth() / 2f, -disc.getHeight() / 2f, discPaint);
            canvas.restore();
            // The light in the grooves stays where the light is while the record turns.
            canvas.drawBitmap(glint, -glint.getWidth() / 2f, -glint.getHeight() / 2f, screenPaint);
            // The halo around its edge, out in the haze as it comes out of the sleeve.
            haloPaint.setAlpha(Math.round(255 * eased));
            canvas.drawCircle(0, 0, r * HALO, haloPaint);
            canvas.restore();
        }

        // The sleeve on top of the record, glowing in the key like a cover lit on a stage.
        float corner = CORNER_DP * dp;
        sleevePaint.setColor(0xFF0B1220);
        sleevePaint.setShadowLayer(16 * dp, 0, 0, Scene.alpha(scene.deep, 0.75f));
        canvas.drawRoundRect(sleeve, corner, corner, sleevePaint);
        clip.rewind();
        clip.addRoundRect(sleeve, corner, corner, Path.Direction.CW);
        canvas.save();
        canvas.clipPath(clip);
        if (previous != null && fade < 1f) drawArt(canvas, previous, sleeve, Math.round(255 * (1 - fade)));
        drawArt(canvas, cover, sleeve, Math.round(255 * fade));
        canvas.restore();
        artSleeve.set(sleeve);
        // A warm rim of light around the sleeve, just outside it, so it doesn't touch the art.
        float half = dp / 2;
        edgePaint.setStrokeWidth(dp);
        edgePaint.setColor(Scene.alpha(Scene.mix(scene.deep, scene.hot, 0.3f), 0.65f));
        canvas.drawRoundRect(sleeve.left - half, sleeve.top - half, sleeve.right + half, sleeve.bottom + half,
                corner + half, corner + half, edgePaint);
    }

    /**
     * Keeps whatever is drawn next off the cover art (the sleeve, and the label on the record) as last drawn: for the
     * film grain over the picture. Call between {@code save()} and {@code restore()}.
     */
    void clipOutArt(Canvas canvas, float dp) {
        if (cover == null) return;
        float corner = CORNER_DP * dp;
        artShape.rewind();
        artShape.addRoundRect(artSleeve, corner, corner, Path.Direction.CW);
        if (labelR > 0f) artShape.addCircle(labelX, labelY, labelR, Path.Direction.CW);
        artShape.setFillType(Path.FillType.WINDING);
        canvas.clipOutPath(artShape);
    }

    /**
     * The record (vinyl, grooves and label) for the current cover, its glint (the light caught in its grooves) and its
     * halo, made once per cover, size and colour.
     */
    private void buildDisc(int size, float dp) {
        String key = System.identityHashCode(cover) + "|" + size + "|" + tint;
        if (key.equals(discKey) && disc != null) return;
        discKey = key;
        if (disc != null) disc.recycle();
        if (glint != null) glint.recycle();
        disc = glint = null;
        if (size <= 0) return;
        float r = size / 2f;
        disc = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(disc);
        float cx = r;
        Scene light = new Scene(tint != 0 ? tint : Theme.AMBER);
        int vinyl = vinylFrom(light.deep);
        int shine = Scene.mix(vinyl, light.hot, 0.6f);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        // Coloured vinyl, lit: deeper toward the middle, brightest near the edge.
        p.setShader(new RadialGradient(cx, cx, r,
                new int[]{darker(vinyl, 0.5f), darker(vinyl, 0.72f), vinyl, Scene.alpha(shine, 0.95f)},
                new float[]{0f, 0.5f, 0.92f, 1f}, Shader.TileMode.CLAMP));
        c.drawCircle(cx, cx, r, p);
        p.setShader(null);
        // Grooves: fine dark lines, each with a warm glint beside it, and bands where the pressing catches more light.
        float label = r * LABEL;
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(0.6f * dp);
        boolean dark = true;
        int ring = 0;
        for (float g = label + 3 * dp; g < r - 2 * dp; g += 1.6f * dp) {
            float band = 0.5f + 0.5f * (float) Math.sin(ring++ * 0.55f);
            p.setColor(dark ? 0x26000000 : Scene.alpha(shine, 0.05f + 0.1f * band));
            c.drawCircle(cx, cx, g, p);
            dark = !dark;
        }
        // Two wider gaps between tracks, dark with a lit edge.
        for (float gap : new float[]{0.62f, 0.81f}) {
            p.setStrokeWidth(1.4f * dp);
            p.setColor(0x30000000);
            c.drawCircle(cx, cx, r * gap, p);
            p.setStrokeWidth(0.6f * dp);
            p.setColor(Scene.alpha(shine, 0.3f));
            c.drawCircle(cx, cx, r * gap + 1.1f * dp, p);
        }
        p.setStrokeWidth(dp);
        p.setColor(Scene.alpha(shine, 0.6f));
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

        // The glint: warm light caught in the grooves, in two soft wedges (the key light from above, and the wall's
        // light from below, on the side out of the sleeve), plus a broad soft sheen in the same places.
        glint = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas g = new Canvas(glint);
        Paint q = new Paint(Paint.ANTI_ALIAS_FLAG);
        q.setStyle(Paint.Style.FILL);
        q.setColor(Scene.alpha(light.hot, 0.12f));
        g.drawCircle(cx, cx, r - 0.5f * dp, q);
        q.setStyle(Paint.Style.STROKE);
        q.setStrokeWidth(0.7f * dp);
        boolean bright = true;
        for (float at = label + 3.8f * dp; at < r - 1.5f * dp; at += 1.6f * dp) {
            q.setColor(Scene.alpha(light.hot, bright ? 0.6f : 0.3f));
            g.drawCircle(cx, cx, at, q);
            bright = !bright;
        }
        q.setStyle(Paint.Style.FILL);
        q.setShader(new SweepGradient(cx, cx,
                new int[]{0x00000000, 0x55000000, 0x00000000, 0x00000000, 0xCC000000, 0x00000000, 0x00000000,
                        0xFF000000, 0x00000000, 0x00000000},
                new float[]{0f, 0.08f, 0.16f, 0.27f, 0.37f, 0.47f, 0.52f, 0.6f, 0.7f, 1f}));
        q.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
        g.drawRect(0, 0, size, size, q);
        q.setShader(null);
        q.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
        g.drawCircle(cx, cx, label + 1.5f * dp, q);

        // The halo: a bright rim of light at the record's edge, fading out into the haze around it.
        float reach = r * HALO;
        haloPaint.setShader(new RadialGradient(0, 0, reach,
                new int[]{0, 0, Scene.alpha(light.hot, 0.4f), light.hot, Scene.alpha(light.deep, 0.75f),
                        Scene.alpha(light.deep, 0.22f), light.deep & 0x00FFFFFF},
                new float[]{0f, 0.9f / HALO, 0.975f / HALO, 1f / HALO, 1.03f / HALO, 1.13f / HALO, 1f},
                Shader.TileMode.CLAMP));
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

    /** A rich coloured vinyl in this colour's hue, bright as if lit from within (silver for a light with no colour). */
    private static int vinylFrom(int colour) {
        float[] hsv = new float[3];
        Color.colorToHSV(colour, hsv);
        boolean none = hsv[1] < 0.3f;
        hsv[1] = none ? hsv[1] : Math.min(0.95f, Math.max(0.8f, hsv[1]));
        hsv[2] = none ? 0.72f : 0.94f;
        return Color.HSVToColor(0xF2, hsv); // slightly see-through, like coloured vinyl
    }

    private static int darker(int colour, float f) {
        return Color.argb(Color.alpha(colour), Math.round(Color.red(colour) * f),
                Math.round(Color.green(colour) * f), Math.round(Color.blue(colour) * f));
    }
}
