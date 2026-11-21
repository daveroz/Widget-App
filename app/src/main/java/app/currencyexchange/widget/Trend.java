package app.currencyexchange.widget;

import java.time.LocalDate;

/**
 * The last month of market rates behind each conversion row, as {@link Fetcher} keeps it (one point a day). It was
 * drawn as a thread of light across each row until 2.01; the widget no longer shows it, but the history is still kept
 * (it comes with the live market rate from Wise), so a chart can come back without a fresh month to wait for.
 */
final class Trend {
    private Trend() {}

    /** Days of history behind each row (plus today's live rate as the last point). */
    static final int DAYS = 30;
    /** One sample every STEP days (1 = daily). For a longer DAYS, a bigger step keeps the requests calm. */
    static final int STEP = 1;

    /** The fixed days the history is sampled on, so samples already fetched stay valid as time moves on. */
    static boolean sampleDay(LocalDate d) { return Math.floorMod(d.toEpochDay(), STEP) == 0; }
}
