package app.currencyexchange.widget;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;

/**
 * The Sound widget's wall of lights, as on the Deco launcher's Home: square lamps right across the
 * widget from margin to margin, in one colour (the album cover's, or the playing app's), like the LED
 * panels on a stage. The lamps stand in strips three wide, split into an upper and a lower panel.
 *
 * While something plays, the wall follows the sound: each lamp column is a part of the music, bass on
 * the left and treble on the right, filling up from the bottom with it, rising quickly and settling
 * back gently, its top lamp fading in and out. (Columns hidden behind something, like the launcher's
 * LP, can echo the treble instead.) When nothing plays, or it's paused, steam drifts across it
 * ({@link Steam}): wisps of light curling from left to right, with a slow swell of energy travelling
 * the same way, the top row lit wherever there's energy, each lit lamp as bright as when the music
 * kicks in. The two blend into each other.
 *
 * Dark lamps keep a faint ember, so the panels still read. Each lit lamp is sharp, white-hot at full
 * strength, with a soft round glow, and lit panels throw soft beams down into the haze. Lamps behind
 * text can be dimmer, so it stays easy to read. The lamps are about the same size on every widget: a
 * short wall has fewer rows, a tall one more.
 */
final class SoundLights {
    /**
     * The wall has as many rows as fit at about this distance from one lamp to the next (in dp), so the lamps are
     * the same size on every widget: a short wall has fewer rows, a tall one more, as tall as it is.
     */
    private static final float PITCH_DP = 10f;
    private static final int MIN_ROWS = 4, MAX_ROWS = 24;
    /** Strips are this many lamps wide, with a dark column between them. */
    private static final int STRIP = 3;
    /** Each lamp's size, as a share of the pitch. */
    private static final float LAMP = 0.7f;
    private static final float SOFT = 1 / 8f;
    private static final int BANDS = SoundWave.COLUMNS;
    /** Following the sound: the top lamp of each column, and the one under it, are a little softer. */
    private static final float[] TIP = {0.6f, 0.85f};
    /** How long the wall takes to go from the steam to the sound, or back. */
    private static final float REACT_SECONDS = 0.6f;

    private final Paint lamp = new Paint();
    private final Paint glowPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint softPaint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
    private final Bitmap sprite = glowSprite();
    private final Bitmap beam = beamSprite();
    private final RectF rect = new RectF();
    private final Rect softBounds = new Rect();
    private float[] values = new float[0];
    private float[] panelLight = new float[0];
    private float[] columnStrength = new float[0];
    private int[] columnBand = new int[0];
    private float[] soundStrength = new float[0];
    private int columns, strips;
    private Bitmap soft;
    private Canvas softCanvas;
    private float time;
    /** The steam: each lamp's light, easing toward its target. */
    private float[] steam = new float[0];
    /** When the steam was last worked out (to ease it by the time since), and whether it has been yet. */
    private float steamAt;
    private boolean steamStarted;
    /** The wall's top-left corner, the distance from one lamp to the next across and down, and a lamp's size. */
    private float left, top, pitchX, pitchY, cell, dp = 1;
    /** How many rows the wall has, half of them in each panel. */
    private int rows = 10, panelRows = 5;
    private final float[] target = new float[BANDS];
    private final float[] shown = new float[BANDS];
    private double soundTime;
    private boolean reactive;
    private float react;
    /** The size and zones this layout was made for, so it's only redone when they change. */
    private String laidOut = "";

    SoundLights() {
        glowPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.ADD)); // light adds up
    }

    /** Whether a column is a dark gap between strips: the last of every four (the wall starts and ends on lamps). */
    private static boolean gap(int column) {
        return column % (STRIP + 1) == STRIP;
    }

    /** The first column of a strip. */
    private static int firstOf(int strip) {
        return strip * (STRIP + 1);
    }

    /**
     * Places the wall: its box, where the text ends (lamps are dimmer to its left), where the music's
     * columns end (an LP's record), and the whole picture's size for the glows and beams.
     */
    void layout(RectF wall, float textRight, float musicEdge, int width, int height, float dpPx) {
        String key = wall + "|" + textRight + "|" + musicEdge + "|" + width + "x" + height;
        if (key.equals(laidOut)) return;
        laidOut = key;
        dp = dpPx;
        // Whole strips from margin to margin, as many as fit at about PITCH_DP apart: the wall always starts and ends
        // on a full strip, and the rows fill its height, so resizing adds or takes away strips and rows, never cuts one.
        float target = PITCH_DP * dp;
        strips = Math.max(1, Math.round((wall.width() / target + 1) / (STRIP + 1)));
        columns = strips * (STRIP + 1) - 1;
        pitchX = wall.width() / columns;
        rows = Math.max(MIN_ROWS, Math.min(MAX_ROWS, 2 * Math.round(wall.height() / (2 * pitchX))));
        panelRows = rows / 2;
        pitchY = wall.height() / rows;
        cell = Math.min(pitchX, pitchY) * LAMP; // square lamps, each centred in its place
        left = wall.left;
        top = wall.top;
        values = new float[rows * columns];
        steam = new float[rows * columns];
        steamStarted = false;
        panelLight = new float[strips * 2];
        columnStrength = new float[columns];
        soundStrength = new float[columns];
        columnBand = new int[columns];
        for (int c = 0; c < columns; c++) {
            float x = left + (c + 0.5f) * pitchX;
            float t = Math.max(0f, Math.min(1f, (x - (textRight - 12 * dp)) / (48 * dp)));
            float ease = t * t * (3 - 2 * t);
            columnStrength[c] = 0.55f + 0.45f * ease;
            soundStrength[c] = 0.85f + 0.15f * ease; // the music's bass is under the words: dim it only a little
        }
        mapBands(musicEdge);
        softBounds.set(0, 0, width, height);
        if (soft != null) soft.recycle();
        soft = Bitmap.createBitmap(Math.max(1, Math.round(width * SOFT)), Math.max(1, Math.round(height * SOFT)),
                Bitmap.Config.ARGB_8888);
        softCanvas = new Canvas(soft);
        softCanvas.scale(SOFT, SOFT);
    }

    /** Shares the music out between the lamp columns, bass first, up to {@code edge}; behind it, echoes. */
    private void mapBands(float edge) {
        if (columns == 0) return;
        int music = 0;
        for (int c = 0; c < columns; c++) if (!gap(c) && left + (c + 1) * pitchX <= edge) music++;
        music = Math.max(2, music);
        int index = 0;
        for (int c = 0; c < columns; c++) {
            if (gap(c)) {
                columnBand[c] = -1;
                continue;
            }
            columnBand[c] = index < music ? Math.round(index * (BANDS - 1) / (float) (music - 1))
                    : Math.max(0, BANDS - 1 - (index - music) * 2);
            soundStrength[c] = Math.abs(soundStrength[c]) * (index < music ? 1f : -1f); // negative: an echo
            index++;
        }
    }

    /** Follow the sound (something is playing), or show the steam (paused, or nothing playing). */
    void setReactive(boolean on) {
        reactive = on;
    }

    boolean isReactive() {
        return reactive;
    }

    /** Whether the wall is still moving over to the sound or back (frames should come quickly). */
    boolean blending() {
        return reactive ? react < 1f : react > 0f;
    }

    /** How loud the bass is on the wall right now, 0 to 1: the washes swell with it. */
    float bass() {
        float sum = 0f;
        for (int b = 0; b < 8; b++) sum += shown[b];
        return react * sum / 8;
    }

    /** Moves the show on by {@code dt} seconds, listening to {@code source} while it follows the sound. */
    void step(float dt, SoundWave source) {
        time += dt;
        float stepReact = dt / REACT_SECONDS;
        react = reactive ? Math.min(1f, react + stepReact) : Math.max(0f, react - stepReact);
        soundTime += dt;
        if (source != null && (reactive || react > 0f)) source.levels(soundTime, target);
        else java.util.Arrays.fill(target, 0f);
        // Rise quickly but eased; settle back over about a second.
        float rise = Math.min(1f, dt * 18);
        float settle = (float) Math.pow(0.06, dt);
        for (int b = 0; b < BANDS; b++) {
            float l = target[Math.max(0, b - 1)], r = target[Math.min(BANDS - 1, b + 1)];
            float goal = 0.2f * l + 0.6f * target[b] + 0.2f * r;
            shown[b] = goal > shown[b] ? shown[b] + (goal - shown[b]) * rise : goal + (shown[b] - goal) * settle;
        }
    }

    /** Draws the wall: soft glows and beams, then the sharp lamps. */
    void draw(Canvas canvas, Scene scene) {
        if (soft == null || columns == 0) return;
        light();
        if (react > 0f) followSound();
        measurePanels();

        soft.eraseColor(0);
        float gapX = (pitchX - cell) / 2, gapY = (pitchY - cell) / 2;
        // Beams from the lit panels, falling down and a little to the left into the haze.
        for (int s = 0; s < strips; s++) {
            for (int p = 0; p < 2; p++) {
                float b = panelLight[s * 2 + p];
                if (b < 0.15f) continue;
                int first = firstOf(s);
                float x = left + (first + STRIP / 2f) * pitchX;
                float y = top + (p + 1) * panelRows * pitchY - gapY;
                drawBeam(scene, x, y, STRIP * pitchX * 2.4f, 70 * dp, 0.35f * b);
            }
        }
        // A soft round glow around every lit lamp.
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < columns; c++) {
                float v = values[r * columns + c];
                if (v < 0.08f) continue;
                float x = left + c * pitchX + gapX, y = top + r * pitchY + gapY;
                glowPaint.setColor(Scene.alpha(scene.key, v * 0.5f));
                rect.set(x - 1.6f * cell, y - 1.6f * cell, x + 2.6f * cell, y + 2.6f * cell);
                softCanvas.drawBitmap(sprite, null, rect, glowPaint);
            }
        }
        canvas.drawBitmap(soft, null, softBounds, softPaint);

        // The lamps: a faint ember when dark, white-hot at full strength.
        int ember = Scene.mix(scene.key, 0xFF000000, 0.35f);
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < columns; c++) {
                if (gap(c)) continue; // the dark column between strips
                float v = values[r * columns + c];
                int colour = v < 0.02f ? ember : Scene.mix(scene.key, scene.led, 0.5f * v * v);
                lamp.setColor(Scene.alpha(colour, 0.06f + 0.84f * v));
                float x = left + c * pitchX + gapX, y = top + r * pitchY + gapY;
                canvas.drawRect(x, y, x + cell, y + cell, lamp);
            }
        }
    }

    /** Blends each lamp toward the sound: columns filling up from the bottom with their part of the music. */
    private void followSound() {
        float blend = react * react * (3 - 2 * react);
        for (int c = 0; c < columns; c++) {
            if (columnBand[c] < 0) continue;
            boolean echo = soundStrength[c] < 0;
            float strength = Math.abs(soundStrength[c]) * (echo ? 0.5f : 1f);
            float lit = shown[columnBand[c]] * rows;
            int full = (int) lit;
            int topRow = full < rows && lit - full >= 0.03f ? full : full - 1;
            for (int r = 0; r < rows; r++) {
                int fromBottom = rows - 1 - r;
                float v = fromBottom < full ? 1f : fromBottom == full ? lit - full : 0f;
                int fromTop = topRow - fromBottom;
                if (v > 0f && fromTop >= 0 && fromTop < TIP.length) v *= TIP[fromTop];
                int i = r * columns + c;
                values[i] += (v * strength - values[i]) * blend;
            }
        }
    }

    /** Each panel's average light, for its beam. */
    private void measurePanels() {
        for (int s = 0; s < strips; s++) {
            for (int p = 0; p < 2; p++) {
                float sum = 0f;
                int count = 0;
                for (int r = p * panelRows; r < (p + 1) * panelRows; r++) {
                    for (int k = 0; k < STRIP; k++) {
                        int c = firstOf(s) + k;
                        sum += values[r * columns + c];
                        count++;
                    }
                }
                panelLight[s * 2 + p] = count == 0 ? 0f : sum / count;
            }
        }
    }

    /**
     * Works out every lamp's strength for now from the steam: each lamp eases toward its target (quickly, so the
     * filaments keep crisp edges) and is dimmer behind the text. Lit lamps are as bright as the music's, with no dimming
     * of their own, so the wall is as bright between songs as during them.
     */
    private void light() {
        float ease = steamStarted ? Math.min(1f, (time - steamAt) * Steam.EASE) : 1f;
        steamAt = time;
        steamStarted = true;
        float t = time * Steam.SPEED;
        for (int c = 0; c < columns; c++) {
            if (gap(c)) continue;
            float energy = Steam.energy(c, t);
            for (int r = 0; r < rows; r++) {
                int i = r * columns + c;
                float target = Steam.target(c, r, t, energy, r == 0);
                steam[i] += (target - steam[i]) * ease;
                values[i] = steam[i] * columnStrength[c];
            }
        }
    }

    /** A soft beam from a point, falling down and a little to the left, widening as it fades. */
    private void drawBeam(Scene scene, float x, float y, float width, float length, float strength) {
        glowPaint.setColor(Scene.alpha(scene.key, Math.min(1f, strength)));
        softCanvas.save();
        softCanvas.rotate(12f, x, y);
        rect.set(x - width / 2, y, x + width / 2, y + length);
        softCanvas.drawBitmap(beam, null, rect, glowPaint);
        softCanvas.restore();
    }

    /** The soft round glow behind a sharp light: full in the middle, fading to nothing at the edge. */
    static Bitmap glowSprite() {
        int size = 32;
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ALPHA_8);
        int[] pixels = new int[size * size];
        float r = size / 2f;
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                float d = (float) Math.hypot(x + 0.5f - r, y + 0.5f - r) / r;
                float a = d >= 1f ? 0f : (1 - d) * (1 - d);
                pixels[y * size + x] = Math.round(a * 255) << 24;
            }
        }
        bitmap.setPixels(pixels, 0, size, 0, 0, size, size);
        return bitmap;
    }

    /** A beam's shape: narrow and strong at the top, wider and fading to nothing at the bottom. */
    private static Bitmap beamSprite() {
        int w = 32, h = 96;
        Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ALPHA_8);
        int[] pixels = new int[w * h];
        for (int y = 0; y < h; y++) {
            float t = y / (float) (h - 1);
            float half = 0.3f + 0.7f * t;
            for (int x = 0; x < w; x++) {
                float across = Math.abs((x + 0.5f) / w * 2 - 1) / half;
                float a = across >= 1f ? 0f : (float) Math.pow(1 - t, 1.4) * (1 - across * across);
                pixels[y * w + x] = Math.round(a * 255) << 24;
            }
        }
        bitmap.setPixels(pixels, 0, w, 0, 0, w, h);
        return bitmap;
    }

    /** Moves the show on without listening (for previews). */
    void advance(float seconds) {
        time += seconds;
    }

    /** Sets the music's levels straight away and goes all the way over to the sound (for previews). */
    void showLevels(float[] levels) {
        System.arraycopy(levels, 0, shown, 0, Math.min(levels.length, BANDS));
        react = reactive ? 1f : 0f;
    }
}
