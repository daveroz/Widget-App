package app.currencyexchange.widget;

import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;

import java.time.LocalDate;
import java.time.MonthDay;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * What the world clocks' skies show on special days and in storms (2.27): fireworks after dark on New Year's Eve and
 * Day everywhere, on the Lunar New Year (its eve and day) in the countries that keep it, and on each country's
 * independence or national day; lightning in a thunderstorm; and in December, strings of Christmas lights across the
 * street, twinkling, brightest at night. Everything is drawn from the time alone, so the frames follow each other.
 */
final class Festive {
    private Festive() {}

    /** Independence or national days with fireworks, by country (ISO code): month * 100 + day. */
    private static final Map<String, Integer> NATIONAL = new HashMap<>();
    /** Countries that keep the Lunar New Year. */
    private static final Set<String> LUNAR = new HashSet<>();
    /** The Lunar New Year's day, by year. */
    private static final Map<Integer, MonthDay> LUNAR_DAY = new HashMap<>();

    static {
        Object[][] days = {
                {"US", 704}, {"MX", 916}, {"CO", 720}, {"AR", 709}, {"BR", 907}, {"CL", 918}, {"PE", 728}, {"VE", 705},
                {"EC", 810}, {"BO", 806}, {"PY", 515}, {"UY", 825}, {"CR", 915}, {"GT", 915}, {"HN", 915}, {"SV", 915},
                {"NI", 915}, {"PA", 1103}, {"DO", 227}, {"JM", 806}, {"CA", 701}, {"AU", 126}, {"NZ", 206},
                {"IN", 815}, {"PK", 814}, {"BD", 326}, {"ID", 817}, {"PH", 612}, {"VN", 902}, {"KR", 815},
                {"CN", 1001}, {"TW", 1010}, {"SG", 809}, {"MY", 831}, {"FR", 714}, {"BE", 721}, {"CH", 801},
                {"RO", 1201}, {"DE", 1003}, {"IT", 602}, {"ES", 1012}, {"PT", 610}, {"NL", 427}, {"PL", 1111},
                {"GR", 325}, {"GB", 1105}, {"IE", 317}, {"NO", 517}, {"SE", 606}, {"FI", 1206}, {"AT", 1026},
                {"CZ", 1028}, {"HU", 820}, {"UA", 824}, {"TR", 1029}, {"ZA", 427}, {"NG", 1001}, {"KE", 1212},
                {"EG", 723}, {"MA", 730}, {"SA", 923}, {"AE", 1202}};
        for (Object[] d : days) NATIONAL.put((String) d[0], (Integer) d[1]);
        for (String cc : new String[]{"CN", "HK", "MO", "TW", "SG", "MY", "VN", "KR", "ID"}) LUNAR.add(cc);
        int[][] lny = {{2025, 1, 29}, {2026, 2, 17}, {2027, 2, 6}, {2028, 1, 26}, {2029, 2, 13}, {2030, 2, 3},
                {2031, 1, 23}, {2032, 2, 11}, {2033, 1, 31}, {2034, 2, 19}, {2035, 2, 8}, {2036, 1, 28}};
        for (int[] y : lny) LUNAR_DAY.put(y[0], MonthDay.of(y[1], y[2]));
    }

    /** Whether there are fireworks in country {@code cc} (null: unknown) on {@code d}, its local date. */
    static boolean fireworks(LocalDate d, String cc) {
        int m = d.getMonthValue(), day = d.getDayOfMonth();
        if ((m == 12 && day == 31) || (m == 1 && day == 1)) return true; // New Year's Eve and Day, everywhere
        if (cc == null || cc.isEmpty()) return false;
        String c = cc.toUpperCase(java.util.Locale.ROOT);
        Integer nat = NATIONAL.get(c);
        if (nat != null && nat == m * 100 + day) return true;
        if (LUNAR.contains(c)) {
            MonthDay lny = LUNAR_DAY.get(d.getYear());
            if (lny != null) {
                LocalDate first = lny.atYear(d.getYear());
                if (d.equals(first) || d.equals(first.minusDays(1))) return true; // its eve and its day
            }
        }
        return false;
    }

    /** Whether the weather is a thunderstorm (WMO 95, 96, 99). */
    static boolean storm(int code) {
        return code == 95 || code == 96 || code == 99;
    }

    private static final int[] SPARKS = {0xFFFFD27A, 0xFFFF6464, 0xFF8CFFA8, 0xFF8CCBFF, 0xFFFF9BE0, 0xFFFFF4E0};

    /**
     * Fireworks over the city at {@code t} seconds, {@code strength} 0 to 1 (how dark it is): a new shell every
     * 0.9 s or so, each rising on a faint trail, bursting into a ring of sparks that spread, sag and fade over about
     * two seconds, a soft flash at its heart; up to three at once, all over the sky, in festive colours.
     */
    static void fireworks(Canvas c, int w, int h, float px, float t, long seed, float strength, Paint p) {
        if (strength <= 0.02f) return;
        float every = 0.9f, life = 2.6f, rise = 0.55f;
        long last = (long) Math.floor(t / every);
        float big = Math.min(w, h);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        for (long k = last - (long) Math.ceil(life / every); k <= last; k++) {
            float age = t - k * every;
            if (age < 0 || age > life) continue;
            Random r = new Random(seed * 1_000_003L + k * 7919L);
            if (r.nextFloat() < 0.18f) continue; // now and then a pause
            float cx = w * (0.14f + 0.72f * r.nextFloat()), cy = h * (0.12f + 0.28f * r.nextFloat());
            float reach = big * (0.2f + 0.14f * r.nextFloat());
            int colour = SPARKS[r.nextInt(SPARKS.length)], second = SPARKS[r.nextInt(SPARKS.length)];
            int n = 18 + r.nextInt(10);
            float spin = r.nextFloat() * 6.2832f;
            if (age < rise) { // the shell rising on its trail
                float f = age / rise, y0 = h * 0.9f, y = y0 + (cy - y0) * (1 - (1 - f) * (1 - f));
                p.setStrokeWidth(1.1f * px);
                p.setShader(null);
                p.setColor(Scene.alpha(0xFFFFE8C0, 0.7f * strength));
                c.drawLine(cx, y + 6 * px, cx, y, p);
                continue;
            }
            float a = age - rise, span = life - rise, fade = (float) Math.pow(Math.max(0, 1 - a / span), 1.4);
            if (a < 0.25f) { // the flash at its heart
                p.setStyle(Paint.Style.FILL);
                p.setShader(new RadialGradient(cx, cy, reach * 0.6f, Scene.alpha(colour, 0.45f * strength * (1 - a / 0.25f)),
                        colour & 0x00FFFFFF, Shader.TileMode.CLAMP));
                c.drawCircle(cx, cy, reach * 0.6f, p);
                p.setShader(null);
                p.setStyle(Paint.Style.STROKE);
            }
            for (int j = 0; j < n + n / 2; j++) { // an outer ring and an inner one, every spark a little its own way
                boolean inner = j >= n;
                float ang = spin + (inner ? (j - n) * 6.2832f / (n / 2) + 0.2f : j * 6.2832f / n) + (r.nextFloat() - 0.5f) * 0.22f;
                float sp = reach * (inner ? 0.45f + 0.15f * r.nextFloat() : 0.75f + 0.4f * r.nextFloat());
                float d1 = sp * (1 - (float) Math.exp(-a * 2.4f)), d0 = sp * (1 - (float) Math.exp(-Math.max(0, a - 0.12f) * 2.4f));
                float sag = h * 0.06f * a * a;
                float x1 = cx + (float) Math.cos(ang) * d1, y1 = cy + (float) Math.sin(ang) * d1 + sag;
                float x0 = cx + (float) Math.cos(ang) * d0, y0 = cy + (float) Math.sin(ang) * d0 + sag * 0.8f;
                int col = inner ? second : (j % 4 == 0 ? second : colour);
                p.setStrokeWidth(1.2f * px);
                p.setColor(Scene.alpha(col, 0.55f * fade * strength));
                c.drawLine(x0, y0, x1, y1, p); // its streak
                p.setStrokeWidth(1.9f * px);
                p.setColor(Scene.alpha(Scene.mix(col, 0xFFFFFFFF, 0.45f), 0.95f * fade * strength));
                c.drawPoint(x1, y1, p); // the spark
            }
        }
        p.setStyle(Paint.Style.FILL);
    }

    /** How bright a lightning flash is at {@code t} seconds (0 between strikes): a strike every few seconds, flickering. */
    static float flash(float t, long seed) {
        float cycle = 6.5f;
        long k = (long) Math.floor(t / cycle);
        Random r = new Random(seed ^ (k * 0x9E3779B97F4A7C15L));
        float at = 0.5f + r.nextFloat() * (cycle - 1.5f), age = t - k * cycle - at;
        if (age < 0 || age > 0.5f) return 0f;
        if (age < 0.07f) return 1f;
        if (age < 0.13f) return 0.3f;
        if (age < 0.21f) return 0.85f;
        return 0.6f * (0.5f - age) / 0.29f;
    }

    /**
     * A lightning strike at {@code t} seconds (see {@link #flash}): the sky lit an icy white, and a jagged bolt from the
     * clouds down behind the city, glowing, with a branch.
     */
    static void lightning(Canvas c, int w, int h, float px, float t, long seed, Paint p) {
        float f = flash(t, seed);
        if (f <= 0f) return;
        p.setStyle(Paint.Style.FILL);
        p.setShader(null);
        p.setColor(Scene.alpha(0xFFDCE6FF, 0.32f * f));
        c.drawRect(0, 0, w, h, p);
        long k = (long) Math.floor(t / 6.5f);
        Random r = new Random(seed * 31 + k);
        Path bolt = new Path(), branch = new Path();
        float x = w * (0.2f + 0.6f * r.nextFloat()), y = h * 0.04f, end = h * (0.62f + 0.12f * r.nextFloat());
        bolt.moveTo(x, y);
        int steps = 9, at = 3 + r.nextInt(3);
        float bx = 0, by = 0;
        for (int i = 1; i <= steps; i++) {
            x += (r.nextFloat() - 0.5f) * w * 0.12f;
            y = h * 0.04f + (end - h * 0.04f) * i / steps;
            bolt.lineTo(x, y);
            if (i == at) { bx = x; by = y; }
        }
        branch.moveTo(bx, by);
        float dir = r.nextBoolean() ? 1 : -1;
        for (int i = 1; i <= 3; i++) branch.lineTo(bx + dir * w * 0.06f * i + (r.nextFloat() - 0.5f) * w * 0.03f, by + h * 0.06f * i);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeJoin(Paint.Join.ROUND);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setMaskFilter(new BlurMaskFilter(3 * px, BlurMaskFilter.Blur.NORMAL));
        p.setStrokeWidth(3.2f * px);
        p.setColor(Scene.alpha(0xFFB8CCFF, 0.6f * f));
        c.drawPath(bolt, p); // its glow
        c.drawPath(branch, p);
        p.setMaskFilter(null);
        p.setStrokeWidth(1.1f * px);
        p.setColor(Scene.alpha(0xFFFFFFFF, 0.95f * f));
        c.drawPath(bolt, p);
        p.setStrokeWidth(0.7f * px);
        c.drawPath(branch, p);
        p.setStyle(Paint.Style.FILL);
    }

    private static final int[] BULBS = {0xFFFF5050, 0xFFFFC850, 0xFF50E888, 0xFF58B8FF, 0xFFFFE6C0};

    /**
     * Christmas lights (December): two strings of bulbs across the street, sagging between the cell's edges, each bulb
     * twinkling on its own, glowing more the darker it is ({@code night}, 0 to 1).
     */
    static void christmas(Canvas c, int w, int h, float px, float t, long seed, float night, Paint p) {
        float[][] strings = {{0.86f, 0.05f}, {0.79f, 0.035f}}; // height of the ends, how far each sags (of the height)
        Random r = new Random(seed ^ 0xC4215L);
        p.setStyle(Paint.Style.FILL);
        for (int s = 0; s < strings.length; s++) {
            float yEnd = h * strings[s][0], sag = h * strings[s][1];
            float x0 = s == 0 ? -0.04f * w : 0.18f * w, x1 = s == 0 ? 1.04f * w : 0.86f * w;
            // the wire
            Paint wire = new Paint(Paint.ANTI_ALIAS_FLAG);
            wire.setStyle(Paint.Style.STROKE);
            wire.setStrokeWidth(Math.max(1f, 0.6f * px));
            wire.setColor(Scene.alpha(0xFF101418, 0.55f));
            Path line = new Path();
            for (int i = 0; i <= 24; i++) {
                float u = i / 24f, x = x0 + (x1 - x0) * u, y = yEnd + sag * 4 * u * (1 - u);
                if (i == 0) line.moveTo(x, y); else line.lineTo(x, y);
            }
            c.drawPath(line, wire);
            int n = Math.max(6, Math.round((x1 - x0) / (5.5f * px)));
            for (int i = 1; i < n; i++) {
                float u = i / (float) n, x = x0 + (x1 - x0) * u, y = yEnd + sag * 4 * u * (1 - u) + 1.4f * px;
                int col = BULBS[(i + s * 2) % BULBS.length];
                float phase = r.nextFloat() * 6.2832f, speed = 1.2f + r.nextFloat() * 1.8f;
                float tw = 0.55f + 0.45f * (float) Math.sin(t * speed + phase);
                float bright = (0.45f + 0.55f * night) * tw;
                if (night > 0.2f) {
                    float gr = 3.2f * px;
                    p.setShader(new RadialGradient(x, y, gr, Scene.alpha(col, 0.5f * bright * night), col & 0x00FFFFFF,
                            Shader.TileMode.CLAMP));
                    c.drawCircle(x, y, gr, p);
                    p.setShader(null);
                }
                p.setColor(Scene.alpha(Scene.mix(col, 0xFFFFFFFF, 0.25f * tw), Math.min(1f, 0.35f + bright)));
                c.drawCircle(x, y, 1.05f * px, p);
            }
        }
    }
}
