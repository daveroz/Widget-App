package app.currencyexchange.widget;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * Offline sunrise/sunset (NOAA / "sunrise equation"). Replaces the Sun Surveyor app and the
 * sunrisesunset.io calls the original KWGT preset used. Accuracy is about +-2 minutes.
 */
final class SunCalc {
    private SunCalc() {}

    /**
     * @return {sunriseMinute, sunsetMinute} as local minutes-of-day in {@code zone}.
     * Polar day returns {0, 1440}; polar night returns {0, 0}.
     */
    static int[] times(double lat, double lon, LocalDate date, ZoneId zone) {
        double jd = date.toEpochDay() + 2440587.5 + 0.5; // noon UTC of that date
        double n = Math.ceil(jd - 2451545.0 + 0.0008);
        double jStar = n - lon / 360.0; // east-positive longitude: eastern places transit earlier (UTC)
        double m = norm360(357.5291 + 0.98560028 * jStar);
        double mr = Math.toRadians(m);
        double c = 1.9148 * Math.sin(mr) + 0.0200 * Math.sin(2 * mr) + 0.0003 * Math.sin(3 * mr);
        double lam = norm360(m + c + 180.0 + 102.9372);
        double lr = Math.toRadians(lam);
        double jTransit = 2451545.0 + jStar + 0.0053 * Math.sin(mr) - 0.0069 * Math.sin(2 * lr);
        double sinD = Math.sin(lr) * Math.sin(Math.toRadians(23.4397));
        double cosD = Math.sqrt(1.0 - sinD * sinD);
        double latR = Math.toRadians(lat);
        double cosW = (Math.sin(Math.toRadians(-0.833)) - Math.sin(latR) * sinD) / (Math.cos(latR) * cosD);
        if (cosW < -1) return new int[]{0, 1440};
        if (cosW > 1) return new int[]{0, 0};
        double w0 = Math.toDegrees(Math.acos(cosW));
        double jRise = jTransit - w0 / 360.0;
        double jSet = jTransit + w0 / 360.0;
        return new int[]{minuteOfDay(jRise, zone), minuteOfDay(jSet, zone)};
    }

    /** True if it is currently daytime at the given place. */
    static boolean isDay(double lat, double lon, ZoneId zone, ZonedDateTime now) {
        int m = now.getHour() * 60 + now.getMinute();
        if (Double.isNaN(lat) || Double.isNaN(lon)) return m >= 6 * 60 && m < 18 * 60;
        int[] t = times(lat, lon, now.toLocalDate(), zone);
        if (t[0] <= t[1]) return m >= t[0] && m < t[1];
        return m >= t[0] || m < t[1]; // wraps past midnight (extreme longitudes)
    }

    static final int DAY = 0, SUNSET = 1, BLUE = 2, NIGHT = 3;

    /** Sun height in degrees above the horizon at this place and instant (about +-0.5 degree). */
    static double elevation(double lat, double lon, long epochMs) {
        double d = epochMs / 86400000.0 + 2440587.5 - 2451545.0;
        double m = norm360(357.5291 + 0.98560028 * d);
        double mr = Math.toRadians(m);
        double c = 1.9148 * Math.sin(mr) + 0.0200 * Math.sin(2 * mr) + 0.0003 * Math.sin(3 * mr);
        double lam = Math.toRadians(norm360(m + c + 180.0 + 102.9372));
        double eps = Math.toRadians(23.4397);
        double dec = Math.asin(Math.sin(lam) * Math.sin(eps));
        double ra = Math.atan2(Math.cos(eps) * Math.sin(lam), Math.cos(lam));
        double gmst = norm360(280.46061837 + 360.98564736629 * d);
        double h = Math.toRadians(gmst + lon) - ra;
        double latR = Math.toRadians(lat);
        return Math.toDegrees(Math.asin(Math.sin(latR) * Math.sin(dec) + Math.cos(latR) * Math.cos(dec) * Math.cos(h)));
    }

    /**
     * The sun at this place and instant (2.15): {altitude, azimuth} in radians, the azimuth from the south, westward (as
     * {@link #moon}'s), so a clock can paint it where it really is.
     */
    static double[] sun(double lat, double lon, long epochMs) {
        double rad = Math.PI / 180, d = epochMs / 86400000.0 + 2440587.5 - 2451545.0, e = rad * 23.4397;
        double sm = rad * (357.5291 + 0.98560028 * d);
        double sl = sm + rad * (1.9148 * Math.sin(sm) + 0.02 * Math.sin(2 * sm) + 0.0003 * Math.sin(3 * sm)) + rad * 102.9372 + Math.PI;
        double ra = Math.atan2(Math.sin(sl) * Math.cos(e), Math.cos(sl)), dec = Math.asin(Math.sin(e) * Math.sin(sl));
        double phi = rad * lat, H = rad * (280.16 + 360.9856235 * d) + rad * lon - ra;
        double alt = Math.asin(Math.sin(phi) * Math.sin(dec) + Math.cos(phi) * Math.cos(dec) * Math.cos(H));
        double az = Math.atan2(Math.sin(H), Math.cos(H) * Math.sin(phi) - Math.tan(dec) * Math.cos(phi));
        return new double[]{alt, az};
    }

    /**
     * The moon at this place and instant (2.08; Meeus' "Astronomical Algorithms", as in V. Agafonkin's SunCalc, good to a
     * degree or two): {altitude, azimuth (radians, from the south, westward; refraction included), the share of its disc
     * that's lit, the angle of its bright limb from the zenith (radians, anticlockwise as seen)}.
     */
    static double[] moon(double lat, double lon, long epochMs) {
        double rad = Math.PI / 180, d = epochMs / 86400000.0 + 2440587.5 - 2451545.0, e = rad * 23.4397;
        // the moon's ecliptic longitude, latitude and distance (km), then its right ascension and declination
        double L = rad * (218.316 + 13.176396 * d), M = rad * (134.963 + 13.064993 * d), F = rad * (93.272 + 13.229350 * d);
        double l = L + rad * 6.289 * Math.sin(M), b = rad * 5.128 * Math.sin(F), dist = 385001 - 20905 * Math.cos(M);
        double mra = Math.atan2(Math.sin(l) * Math.cos(e) - Math.tan(b) * Math.sin(e), Math.cos(l));
        double mdec = Math.asin(Math.sin(b) * Math.cos(e) + Math.cos(b) * Math.sin(e) * Math.sin(l));
        // where that is in this place's sky
        double phi = rad * lat, H = rad * (280.16 + 360.9856235 * d) + rad * lon - mra;
        double alt = Math.asin(Math.sin(phi) * Math.sin(mdec) + Math.cos(phi) * Math.cos(mdec) * Math.cos(H));
        double az = Math.atan2(Math.sin(H), Math.cos(H) * Math.sin(phi) - Math.tan(mdec) * Math.cos(phi));
        double pa = Math.atan2(Math.sin(H), Math.tan(phi) * Math.cos(mdec) - Math.sin(mdec) * Math.cos(H));
        double h0 = Math.max(0, alt);
        alt += 0.0002967 / Math.tan(h0 + 0.00312536 / (h0 + 0.08901179));
        // its phase, from where the sun is
        double sm = rad * (357.5291 + 0.98560028 * d);
        double sl = sm + rad * (1.9148 * Math.sin(sm) + 0.02 * Math.sin(2 * sm) + 0.0003 * Math.sin(3 * sm)) + rad * 102.9372 + Math.PI;
        double sra = Math.atan2(Math.sin(sl) * Math.cos(e), Math.cos(sl)), sdec = Math.asin(Math.sin(e) * Math.sin(sl));
        double sun = 149598000;
        double ph = Math.acos(Math.sin(sdec) * Math.sin(mdec) + Math.cos(sdec) * Math.cos(mdec) * Math.cos(sra - mra));
        double inc = Math.atan2(sun * Math.sin(ph), dist - sun * Math.cos(ph));
        double angle = Math.atan2(Math.cos(sdec) * Math.sin(sra - mra),
                Math.sin(sdec) * Math.cos(mdec) - Math.cos(sdec) * Math.sin(mdec) * Math.cos(sra - mra));
        return new double[]{alt, az, (1 + Math.cos(inc)) / 2, angle - pa};
    }

    /** DAY (sun above 6 deg), SUNSET (golden hour, sunrise or sunset, 6 to -0.8 deg), BLUE (blue hour, -0.8 to -8 deg), NIGHT. */
    static int phase(double lat, double lon, ZoneId zone, ZonedDateTime now) {
        if (Double.isNaN(lat) || Double.isNaN(lon)) return isDay(lat, lon, zone, now) ? DAY : NIGHT;
        double el = elevation(lat, lon, now.toInstant().toEpochMilli());
        if (el >= 6) return DAY;
        if (el >= -0.833) return SUNSET;
        if (el >= -8) return BLUE;
        return NIGHT;
    }

    private static int minuteOfDay(double julian, ZoneId zone) {
        long ms = Math.round((julian - 2440587.5) * 86400000.0);
        ZonedDateTime z = Instant.ofEpochMilli(ms).atZone(zone);
        return z.getHour() * 60 + z.getMinute();
    }

    private static double norm360(double d) {
        double r = d % 360.0;
        return r < 0 ? r + 360.0 : r;
    }
}
