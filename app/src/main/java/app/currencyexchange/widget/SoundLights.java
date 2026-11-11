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
 * back gently, its top lamp fading in and out. With an LP on screen, the music's columns end where the
 * record starts and the ones behind it echo the treble. When nothing plays, or it's paused, each panel
 * runs its own pattern for a couple of seconds before switching: filling down or up, a band running
 * down it, a flickering pixel pattern, a flash that fades, all on, or dark. The two blend into each
 * other.
 *
 * Dark lamps keep a faint ember, so the panels still read. Each lit lamp is sharp, white-hot at full
 * strength, with a soft round glow, and lit panels throw soft beams down into the haze. Behind the
 * text on the left the lamps are dimmer, so it stays easy to read.
 */
final class SoundLights {
    static final int ROWS = 10;
    /** Strips are this many lamps wide, with a dark column between them. */
    private static final int STRIP = 3;
    private static final int PANEL_ROWS = ROWS / 2;
    /** Each lamp's size, as a share of the pitch. */
    private static final float LAMP = 0.7f;
    /** How long a panel keeps one pattern, in seconds, and how long it takes to switch. */
    private static final float PATTERN_SECONDS = 2.2f, SWITCH_SECONDS = 0.12f;
    private static final int OFF = 0, FILL_DOWN = 1, FILL_UP = 2, CHASE = 3, PIXELS = 4, FLASH = 5, FULL = 6;
    /** How often each pattern comes up, out of 20. */
    private static final int[] PATTERN_WEIGHTS = {4, 3, 2, 3, 4, 2, 2};
    private static final float SOFT = 1 / 8f;
    private static final int BANDS = SoundWave.COLUMNS;
    /** Following the sound: the top lamp of each column, and the one under it, are a little softer. */
    private static final float[] TIP = {0.6f, 0.85f};
    /** How long the wall takes to go from its patterns to the sound, or back. */
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
    private float left, top, pitch, cell, dp = 1;
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

    /** Whether a column is a dark gap between strips: the first of every four. */
    private static boolean gap(int column) {
        return column % (STRIP + 1) == 0;
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
        pitch = wall.height() / ROWS;
        cell = pitch * LAMP;
        columns = Math.max(0, (int) (wall.width() / pitch));
        strips = columns <= 1 ? 0 : (columns - 2) / (STRIP + 1) + 1;
        left = wall.right - columns * pitch;
        top = wall.top;
        values = new float[ROWS * columns];
        panelLight = new float[strips * 2];
        columnStrength = new float[columns];
        soundStrength = new float[columns];
        columnBand = new int[columns];
        for (int c = 0; c < columns; c++) {
            float x = left + (c + 0.5f) * pitch;
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
        for (int c = 0; c < columns; c++) if (!gap(c) && left + (c + 1) * pitch <= edge) music++;
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

    /** Follow the sound (something is playing), or play the patterns (paused, or nothing playing). */
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
        float gap = (pitch - cell) / 2;
        // Beams from the lit panels, falling down and a little to the left into the haze.
        for (int s = 0; s < strips; s++) {
            for (int p = 0; p < 2; p++) {
                float b = panelLight[s * 2 + p];
                if (b < 0.15f) continue;
                int first = s * (STRIP + 1) + 1;
                int width = Math.min(STRIP, columns - first);
                if (width <= 0) continue;
                float x = left + (first + width / 2f) * pitch;
                float y = top + (p + 1) * PANEL_ROWS * pitch - gap;
                drawBeam(scene, x, y, width * pitch * 2.4f, 70 * dp, 0.35f * b);
            }
        }
        // A soft round glow around every lit lamp.
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < columns; c++) {
                float v = values[r * columns + c];
                if (v < 0.08f) continue;
                float x = left + c * pitch + gap, y = top + r * pitch + gap;
                glowPaint.setColor(Scene.alpha(scene.key, v * 0.5f));
                rect.set(x - 1.6f * cell, y - 1.6f * cell, x + 2.6f * cell, y + 2.6f * cell);
                softCanvas.drawBitmap(sprite, null, rect, glowPaint);
            }
        }
        canvas.drawBitmap(soft, null, softBounds, softPaint);

        // The lamps: a faint ember when dark, white-hot at full strength.
        int ember = Scene.mix(scene.key, 0xFF000000, 0.35f);
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < columns; c++) {
                if (gap(c)) continue; // the dark column between strips
                float v = values[r * columns + c];
                int colour = v < 0.02f ? ember : Scene.mix(scene.key, scene.led, 0.5f * v * v);
                lamp.setColor(Scene.alpha(colour, 0.06f + 0.84f * v));
                float x = left + c * pitch + gap, y = top + r * pitch + gap;
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
            float lit = shown[columnBand[c]] * ROWS;
            int full = (int) lit;
            int topRow = full < ROWS && lit - full >= 0.03f ? full : full - 1;
            for (int r = 0; r < ROWS; r++) {
                int fromBottom = ROWS - 1 - r;
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
                for (int r = p * PANEL_ROWS; r < (p + 1) * PANEL_ROWS; r++) {
                    for (int k = 0; k < STRIP; k++) {
                        int c = s * (STRIP + 1) + 1 + k;
                        if (c >= columns) continue;
                        sum += values[r * columns + c];
                        count++;
                    }
                }
                panelLight[s * 2 + p] = count == 0 ? 0f : sum / count;
            }
        }
    }

    /** Works out every lamp's strength for now, panel by panel, easing from one pattern to the next. */
    private void light() {
        for (int s = 0; s < strips; s++) {
            for (int p = 0; p < 2; p++) {
                int panel = s * 2 + p;
                float clock = time / PATTERN_SECONDS + seed(panel * 7 + 3) * 3;
                int turn = (int) Math.floor(clock);
                float local = (clock - turn) * PATTERN_SECONDS;
                int now = pattern(panel, turn), before = pattern(panel, turn - 1);
                float blend = Math.min(1f, local / SWITCH_SECONDS);
                for (int r = 0; r < PANEL_ROWS; r++) {
                    for (int k = 0; k < STRIP; k++) {
                        int c = s * (STRIP + 1) + 1 + k;
                        if (c >= columns) continue;
                        float v = lamp(now, local, r, k, panel, turn);
                        if (blend < 1f) {
                            float old = lamp(before, local + PATTERN_SECONDS, r, k, panel, turn - 1);
                            v = old + (v - old) * blend;
                        }
                        v *= columnStrength[c];
                        values[(p * PANEL_ROWS + r) * columns + c] = v;
                    }
                }
            }
        }
    }

    /** Which pattern a panel plays on a given turn: picked by chance, the same every time. */
    private static int pattern(int panel, int turn) {
        float pick = seed(panel * 131 + turn * 17) * 20;
        for (int i = 0; i < PATTERN_WEIGHTS.length; i++) {
            pick -= PATTERN_WEIGHTS[i];
            if (pick < 0) return i;
        }
        return OFF;
    }

    /** One lamp's strength in a pattern, {@code local} seconds into it; row and column within the panel. */
    private float lamp(int pattern, float local, int row, int column, int panel, int turn) {
        float progress = Math.min(1f, local / (PATTERN_SECONDS * 0.6f));
        float eased = progress * progress * (3 - 2 * progress);
        switch (pattern) {
            case FILL_DOWN:
                return row < eased * (PANEL_ROWS + 0.99f) ? flicker(row, column, panel) : 0f;
            case FILL_UP:
                return PANEL_ROWS - 1 - row < eased * (PANEL_ROWS + 0.99f) ? flicker(row, column, panel) : 0f;
            case CHASE: {
                float head = (local * 4f) % (PANEL_ROWS + 2) - 1;
                float d = Math.abs(row - head);
                return d < 1.5f ? 1f - d / 1.5f : 0f;
            }
            case PIXELS: {
                int frame = (int) (local / 0.18f);
                return seed(panel * 59 + turn * 211 + frame * 31 + row * 7 + column) > 0.5f ? 0.9f : 0f;
            }
            case FLASH:
                return (float) Math.exp(-local * 2.5f);
            case FULL:
                return flicker(row, column, panel);
            default:
                return 0f;
        }
    }

    /** Lit lamps shimmer a little, each in its own time. */
    private float flicker(int row, int column, int panel) {
        float s = seed(panel * 37 + row * 5 + column);
        return 0.82f + 0.18f * (float) Math.sin(time * (9 + s * 6) + s * 30);
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

    /** A fixed pseudo-random number, so the patterns never repeat in step. */
    private static float seed(int i) {
        double s = Math.sin(i * 127.1 + 311.7) * 43758.5453;
        return (float) (s - Math.floor(s));
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
