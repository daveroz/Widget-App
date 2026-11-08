package app.currencyexchange.widget;

import android.Manifest;
import android.content.ContentUris;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CalendarContract;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/** Next 5 events from the device calendar (every calendar the user has switched on, including Google's). No account or key. */
final class CalendarData {
    private CalendarData() {}

    static final int MAX = 8;

    static final class Event {
        final String title;
        final LocalDate date; // the day the event is listed under
        final boolean allDay;
        final long begin, end; // epoch millis (all-day events: UTC midnights)
        final int color;       // event / calendar colour, 0 if unknown
        Event(String title, LocalDate date, boolean allDay, long begin, long end, int color) {
            this.title = title; this.date = date; this.allDay = allDay; this.begin = begin; this.end = end; this.color = color;
        }
    }

    static boolean allowed(Context c) {
        return c.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED;
    }

    static List<Event> upcoming(Context c) {
        List<Event> out = new ArrayList<>();
        if (!allowed(c)) return out;
        ZoneId zone = ZoneId.systemDefault();
        long now = System.currentTimeMillis();
        LocalDate today = LocalDate.now(zone);
        Uri.Builder b = CalendarContract.Instances.CONTENT_URI.buildUpon();
        ContentUris.appendId(b, now - 24L * 3600_000L);
        ContentUris.appendId(b, now + 120L * 24L * 3600_000L);
        String[] proj = {CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN,
                CalendarContract.Instances.END, CalendarContract.Instances.ALL_DAY,
                "eventColor", "calendar_color"};
        try (Cursor cur = c.getContentResolver().query(b.build(), proj, "visible=1", null, "begin ASC, end ASC")) {
            if (cur == null) return out;
            while (cur.moveToNext() && out.size() < MAX) {
                String title = cur.getString(0);
                long begin = cur.getLong(1), end = cur.getLong(2);
                boolean allDay = cur.getInt(3) == 1;
                int color = cur.getInt(4) != 0 ? cur.getInt(4) : cur.getInt(5);
                LocalDate day;
                if (allDay) { // all-day events are stored as UTC midnights
                    day = Instant.ofEpochMilli(begin).atZone(ZoneOffset.UTC).toLocalDate();
                    LocalDate lastDay = Instant.ofEpochMilli(end - 1).atZone(ZoneOffset.UTC).toLocalDate();
                    if (lastDay.isBefore(today)) continue; // already over
                    if (day.isBefore(today)) day = today;   // running multi-day event: show today
                } else {
                    if (end <= now) continue;               // already over
                    day = Instant.ofEpochMilli(Math.max(begin, now - 1)).atZone(zone).toLocalDate();
                    if (begin < now) day = today;
                }
                if (title == null || title.trim().isEmpty()) title = "(No title)";
                out.add(new Event(title.trim(), day, allDay, begin, end, color));
            }
        } catch (SecurityException | IllegalArgumentException e) {
            return new ArrayList<>();
        }
        return out;
    }
}
