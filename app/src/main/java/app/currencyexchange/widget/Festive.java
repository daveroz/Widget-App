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
 * Day everywhere, on Christmas Day everywhere (2.34, from its midnight), on the Lunar New Year (its eve and day) in the countries that keep it, and on each country's
 * independence or national day; lightning in a thunderstorm. (December's strings of Christmas lights across the
 * street, from 2.27, were taken out in 2.34.) Everything is drawn from the time alone, so the frames follow each other.
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
        if (m == 12 && day == 25) return true; // Christmas Day, everywhere, from its midnight (2.34)
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
     * Fireworks over the city at {@code t} seconds, {@code strength} 0 to 1 (how dark it is): a new shell every 0.9 s or
     * so, each rising on a faint trail and bursting with a soft flash at its heart. Since 2.31 its sparks move as the
     * fireworks libraries' do (fireworks-js's friction, gravity and fading trails): each flies out, slows, sags and
     * fades, drawing a short trail behind it, some flickering as they die; three kinds of shell: a peony (sparks at all
     * speeds, a full ball), a ring (one speed, a crisp circle) and a willow (gold, slow, long drooping trails).
     */
    static void fireworks(Canvas c, int w, int h, float px, float t, long seed, float strength, Paint p) {
        if (strength <= 0.02f) return;
        float every = 0.9f, rise = 0.55f;
        long last = (long) Math.floor(t / every);
        float big = Math.min(w, h);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        for (long k = last - 5; k <= last; k++) {
            float age = t - k * every;
            if (age < 0) continue;
            Random r = new Random(seed * 1_000_003L + k * 7919L);
            if (r.nextFloat() < 0.18f) continue; // now and then a pause
            int kind = r.nextInt(5); // 0, 1 peony; 2, 3 ring; 4 willow
            boolean willow = kind == 4, ring = kind == 2 || kind == 3;
            float life = willow ? 3.6f : 2.6f;
            if (age > life) continue;
            float cx = w * (0.14f + 0.72f * r.nextFloat()), cy = h * (0.1f + 0.26f * r.nextFloat());
            float reach = big * (0.17f + 0.14f * r.nextFloat()) * (willow ? 0.8f : 1f);
            int colour = willow ? 0xFFFFC870 : SPARKS[r.nextInt(SPARKS.length)], second = SPARKS[r.nextInt(SPARKS.length)];
            boolean flicker = r.nextBoolean();
            if (age < rise) { // the shell rising on its trail
                float f = age / rise, y0 = h * 0.9f, y = y0 + (cy - y0) * (1 - (1 - f) * (1 - f));
                p.setStrokeWidth(1.1f * px);
                p.setShader(null);
                p.setColor(Scene.alpha(0xFFFFE8C0, 0.7f * strength));
                c.drawLine(cx, y + 7 * px, cx, y, p);
                continue;
            }
            float a = age - rise, span = life - rise, fade = (float) Math.pow(Math.max(0, 1 - a / span), willow ? 1.0 : 1.5);
            if (a < 0.25f) { // the flash at its heart
                p.setStyle(Paint.Style.FILL);
                p.setShader(new RadialGradient(cx, cy, reach * 0.6f, Scene.alpha(colour, 0.45f * strength * (1 - a / 0.25f)),
                        colour & 0x00FFFFFF, Shader.TileMode.CLAMP));
                c.drawCircle(cx, cy, reach * 0.6f, p);
                p.setShader(null);
                p.setStyle(Paint.Style.STROKE);
            }
            float drag = willow ? 1.6f : 2.6f, grav = h * (willow ? 0.16f : 0.09f), trail = willow ? 0.4f : 0.14f;
            int n = ring ? 22 + r.nextInt(8) : 30 + r.nextInt(12);
            float spin = r.nextFloat() * 6.2832f;
            for (int j = 0; j < n; j++) {
                float ang = ring ? spin + j * 6.2832f / n + (r.nextFloat() - 0.5f) * 0.08f : r.nextFloat() * 6.2832f;
                float sp = reach * (ring ? 0.95f + 0.1f * r.nextFloat() : 0.25f + 0.85f * (float) Math.sqrt(r.nextFloat()));
                int col = j % 5 == 0 ? second : colour;
                float a0 = Math.max(0, a - trail);
                // out at its speed, slowed by the air, falling a little more each moment
                float d1 = sp * (1 - (float) Math.exp(-a * drag)), d0 = sp * (1 - (float) Math.exp(-a0 * drag));
                float x1 = cx + (float) Math.cos(ang) * d1, y1 = cy + (float) Math.sin(ang) * d1 + grav * a * a * 0.5f;
                float x0 = cx + (float) Math.cos(ang) * d0, y0 = cy + (float) Math.sin(ang) * d0 + grav * a0 * a0 * 0.5f;
                float lit = fade;
                if (flicker && a > span * 0.45f) lit *= 0.45f + 0.55f * Math.abs((float) Math.sin(t * 37 + j * 2.1f));
                p.setStrokeWidth((willow ? 0.9f : 1.1f) * px);
                p.setColor(Scene.alpha(col, (willow ? 0.6f : 0.5f) * lit * strength));
                c.drawLine(x0, y0, x1, y1, p); // its trail
                p.setStrokeWidth(1.8f * px);
                p.setColor(Scene.alpha(Scene.mix(col, 0xFFFFFFFF, 0.45f), 0.95f * lit * strength));
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

}
