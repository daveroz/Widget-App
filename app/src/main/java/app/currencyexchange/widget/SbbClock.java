package app.currencyexchange.widget;

/**
 * Motion model of the Swiss railway (SBB) station clock. Pure maths, no Android classes.
 * Taken from SBB's own digital clock (sbb-design-systems/brand-elements, sbbUhr 1.3) and cross-checked with
 * the SBB Lyne design system text and the bariod/SBB-railway-clock reference:
 *  - the red second hand runs one lap in 58.5 s, then waits at 12 until the minute impulse (about 1.5 s);
 *  - on the impulse the minute hand moves one whole minute with a short elastic overshoot that settles
 *    within 2 s (the mechanical hand swings past and back); the hour hand moves its half degree the same way.
 */
final class SbbClock {
    private SbbClock() {}

    /** Seconds the second hand needs for one full lap. */
    static final double LAP_SECONDS = 58.5;
    /** How long the minute/hour hands take to settle after the impulse. */
    static final double EASE_MS = 2000.0;

    /** Second hand angle in degrees clockwise from 12; {@code sec} is the time into the minute (0 <= sec < 60). */
    static double secondAngle(double sec) {
        if (sec <= 0 || sec >= LAP_SECONDS) return 0; // parked at 12, waiting for the minute impulse
        return 360.0 * sec / LAP_SECONDS;
    }

    /** SBB's easeOutElastic: from b to b+c over EASE_MS; ms = time since the impulse. */
    static double easeOutElastic(double ms, double b, double c) {
        if (ms >= EASE_MS) return b + c;
        double x = ms / EASE_MS;
        return c * Math.pow(2, -10 * x) * Math.sin((ms - 2) * 2 * Math.PI / 300.0) * 1.5 + c + b;
    }

    /**
     * Minute hand. {@code msIntoMinute} 0..59999. With {@code elastic} false the hand sits exactly on the
     * minute (used for still pictures, so a frame never freezes in the middle of the overshoot).
     */
    static double minuteAngle(int minute, double msIntoMinute, boolean elastic) {
        double b = minute * 6.0 - 6.0;
        return elastic ? easeOutElastic(msIntoMinute, b, 6.0) : b + 6.0;
    }

    /** Hour hand: 0.5 degrees per minute, moving with the same impulse and easing. */
    static double hourAngle(int hour24, int minute, double msIntoMinute, boolean elastic) {
        double b = hour24 * 30.0 + minute / 2.0 - 0.5;
        return elastic ? easeOutElastic(msIntoMinute, b, 0.5) : b + 0.5;
    }

    /** True while the minute hand is still swinging. */
    static boolean easing(double msIntoMinute) {
        return msIntoMinute < EASE_MS + 100;
    }
}
