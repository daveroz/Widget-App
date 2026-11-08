package app.currencyexchange.widget;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;

/** Where you are: the device's last known location, saved for weather and sunrise/sunset. */
final class Locator {
    private Locator() {}

    static boolean allowed(Context c) {
        return c.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    /** Saves the newest known location. Returns true if it moved noticeably (or none was saved before). */
    static boolean refreshFromLastKnown(Context c) {
        if (!allowed(c)) return false;
        LocationManager lm = (LocationManager) c.getSystemService(Context.LOCATION_SERVICE);
        Location best = null;
        try {
            for (String p : lm.getProviders(true)) {
                Location l = lm.getLastKnownLocation(p);
                if (l != null && (best == null || l.getTime() > best.getTime())) best = l;
            }
        } catch (SecurityException ignored) { }
        return best != null && save(c, best);
    }

    static boolean save(Context c, Location l) {
        double[] old = Store.weatherLatLon(c);
        boolean moved = Double.isNaN(old[0]) || Math.abs(old[0] - l.getLatitude()) > 0.05
                || Math.abs(old[1] - l.getLongitude()) > 0.05;
        Store.setDeviceLocation(c, l.getLatitude(), l.getLongitude());
        return moved;
    }
}
