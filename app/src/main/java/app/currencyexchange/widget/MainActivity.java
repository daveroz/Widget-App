package app.currencyexchange.widget;

import android.Manifest;
import android.app.Activity;
import android.app.Dialog;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Currency;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Settings screen in the Deco look: glass cards over a stage in amber haze. Cities and currencies are picked from lists. */
public class MainActivity extends Activity {
    private TextView batStatus, batButton;
    private final TextView[] cityBtn = new TextView[4];
    private final TextView[] fromBtn = new TextView[6];
    private TextView toBtn;
    private EditText fee;
    private String sbbMode;
    private final TextView[] modeBtn = new TextView[3];
    private static final String[] MODES = {"smooth", "second", "minute"};
    private static final String[] MODE_LABELS = {"Smooth", "Every second", "Minute only"};
    private TextView banner, calStatus, calButton, locStatus, locButton;
    private LinearLayout statusBox;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private StageView stage;

    private static final int REQ_ALL = 1;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);

        // The stage stays put behind the cards while they scroll over it.
        android.widget.FrameLayout room = new android.widget.FrameLayout(this);
        stage = new StageView(this);
        room.addView(stage, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(0);
        sv.setFillViewport(true);
        room.addView(sv, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        LinearLayout col = vbox();
        col.setPadding(dp(16), dp(30), dp(16), dp(28));
        sv.addView(col, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // ---- header: a lit badge + title ----
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView badge = text("FX", 13, Theme.monoBold(this), Theme.LED);
        badge.setGravity(Gravity.CENTER);
        badge.setLetterSpacing(0.12f);
        badge.setBackground(Theme.lit(this, 12, false));
        head.addView(badge, new LinearLayout.LayoutParams(dp(48), dp(48)));
        LinearLayout titles = vbox();
        titles.setPadding(dp(12), 0, 0, 0);
        titles.addView(text("Worldly", 19, Theme.uiBold(this), Theme.TEXT));
        TextView tagline = text("World clocks · rates · SBB clock · calendar", 12, Theme.uiMedium(this), Theme.MUTED);
        Theme.label(this, tagline);
        tagline.setTextSize(10);
        tagline.setLetterSpacing(0.14f);
        tagline.setTextColor(Theme.AMBER_LIGHT);
        titles.addView(tagline);
        head.addView(titles);
        col.addView(head);

        // ---- world clocks: pick from a list ----
        LinearLayout clocks = card(col, "WORLD CLOCKS");
        for (int i = 0; i < 4; i += 2) {
            LinearLayout row = hbox();
            for (int j = 0; j < 2; j++) {
                final int idx = i + j;
                cityBtn[idx] = picker(Store.city(this, idx), v -> pickCity(idx));
                addWeighted(row, cityBtn[idx], j == 0 ? 0 : 8);
            }
            clocks.addView(row, matchWrap(i == 0 ? 0 : 8));
        }
        TextView cityHint = text("Tap a city to choose from the list. Can't find yours? Type its name in the list's search box.",
                12, Theme.uiMedium(this), Theme.DIM);
        cityHint.setPadding(dp(2), dp(8), 0, 0);
        clocks.addView(cityHint);

        // ---- currency rows: pick from a list ----
        LinearLayout rates = card(col, "CURRENCY ROWS");
        final LinearLayout[] rateRows = new LinearLayout[3];
        for (int i = 0; i < 6; i += 2) {
            LinearLayout row = hbox();
            rateRows[i / 2] = row;
            for (int j = 0; j < 2; j++) {
                final int idx = i + j;
                fromBtn[idx] = picker(currencyLabel(Store.from(this, idx)), v -> pickCurrency("Currency " + (idx + 1), code -> {
                    Store.sp(this).edit().putString("fx" + idx, code).apply();
                    fromBtn[idx].setText(currencyLabel(code));
                    refreshNow("Updating rates…");
                }));
                addWeighted(row, fromBtn[idx], j == 0 ? 0 : 8);
            }
            rates.addView(row, matchWrap(i == 0 ? 0 : 8));
        }
        final TextView[] addBtn = new TextView[1];
        final TextView[] removeBtnRef = new TextView[1];
        final Runnable showRows = () -> {
            int n = Store.count(this);
            for (int i = 0; i < 6; i++) fromBtn[i].setVisibility(i < n ? View.VISIBLE : View.INVISIBLE);
            for (int r = 0; r < 3; r++) rateRows[r].setVisibility(r * 2 < n ? View.VISIBLE : View.GONE);
            addBtn[0].setVisibility(n < 6 ? View.VISIBLE : View.INVISIBLE);
            removeBtnRef[0].setVisibility(n > 1 ? View.VISIBLE : View.INVISIBLE);
        };
        addBtn[0] = secondaryButton("+  Add currency", v -> {
            Store.setCount(this, Store.count(this) + 1);
            refreshNow("Updating rates…");
            showRows.run();
        });
        final TextView removeBtn = secondaryButton("\u2013  Remove", v -> {
            Store.setCount(this, Store.count(this) - 1);
            refreshNow("Updating…");
            showRows.run();
        });
        removeBtnRef[0] = removeBtn;
        LinearLayout addRemove = hbox();
        addWeighted(addRemove, addBtn[0], 0);
        addWeighted(addRemove, removeBtn, 8);
        rates.addView(addRemove, matchWrap(8));
        showRows.run();
        LinearLayout conv = hbox();
        toBtn = picker(currencyLabel(Store.to(this)), v -> pickCurrency("Convert to", code -> {
            Store.sp(this).edit().putString("to", code).apply();
            toBtn.setText(currencyLabel(code));
            refreshNow("Updating rates…");
        }));
        fee = input(Store.feeText(this), InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL, true, "Fee");
        addWeighted(conv, labeledView("CONVERT TO", toBtn), 0);
        addWeighted(conv, labeledView("VISA FEE %", fee), 8);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cp.topMargin = dp(12);
        rates.addView(conv, cp);

        // ---- SBB clock: only the animation options ----
        LinearLayout sbb = card(col, "SBB CLOCK ANIMATION");
        sbbMode = Store.sbbMode(this);
        LinearLayout modes = hbox();
        for (int i = 0; i < 3; i++) {
            final String m = MODES[i];
            modeBtn[i] = text(MODE_LABELS[i], 13, Theme.uiSemi(this), Theme.TEXT_SOFT);
            Theme.label(this, modeBtn[i]);
            modeBtn[i].setLetterSpacing(0.12f);
            modeBtn[i].setGravity(Gravity.CENTER);
            modeBtn[i].setPadding(dp(6), dp(14), dp(6), dp(14));
            modeBtn[i].setClickable(true);
            modeBtn[i].setOnClickListener(v -> {
                sbbMode = m;
                Store.sp(this).edit().putString("sbbMode", m).apply();
                paintModes();
                SbbWidgetProvider.updateAll(getApplicationContext());
                LiveService.ensure(getApplicationContext());
            });
            addWeighted(modes, modeBtn[i], i == 0 ? 0 : 8);
        }
        sbb.addView(modes, matchWrap(0));
        paintModes();
        TextView modeHint = text("Smooth: second hand runs a lap in 58.5 s and the minute hand swings like the real clock. "
                + "Every second: one picture a second. Minute only: still picture each minute. The clock always shows the "
                + "time where you are.", 12, Theme.uiMedium(this), Theme.DIM);
        modeHint.setPadding(dp(2), dp(8), 0, 0);
        sbb.addView(modeHint);

        // ---- permissions ----
        LinearLayout perms = card(col, "PERMISSIONS");
        locStatus = text("", 13, Theme.uiSemi(this), Theme.TEXT);
        perms.addView(locStatus);
        locButton = secondaryButton("Allow location", v -> requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, REQ_ALL));
        perms.addView(locButton, matchWrap(8));
        calStatus = text("", 13, Theme.uiSemi(this), Theme.TEXT);
        calStatus.setPadding(0, dp(12), 0, 0);
        perms.addView(calStatus);
        calButton = secondaryButton("Allow calendar", v -> requestPermissions(new String[]{Manifest.permission.READ_CALENDAR}, REQ_ALL));
        perms.addView(calButton, matchWrap(8));
        batStatus = text("", 13, Theme.uiSemi(this), Theme.TEXT);
        batStatus.setPadding(0, dp(12), 0, 0);
        perms.addView(batStatus);
        batButton = secondaryButton("Allow running in background", v -> {
            try {
                startActivity(new android.content.Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        android.net.Uri.parse("package:" + getPackageName())));
            } catch (Exception e) {
                startActivity(new android.content.Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
            }
        });
        perms.addView(batButton, matchWrap(8));
        updatePermissionCards();

        // ---- actions ----
        col.addView(primaryButton("Save & refresh", v -> save()), matchWrap(18));
        col.addView(secondaryButton("Add Currency widget", v -> pin(WidgetProvider.class)), matchWrap(10));
        col.addView(secondaryButton("Add SBB clock widget", v -> pin(SbbWidgetProvider.class)), matchWrap(8));
        col.addView(secondaryButton("Add Calendar widget", v -> pin(CalendarWidgetProvider.class)), matchWrap(8));

        // ---- status ----
        LinearLayout st = card(col, "STATUS");
        banner = text("", 13, Theme.uiSemi(this), Theme.AMBER);
        banner.setVisibility(View.GONE);
        banner.setPadding(0, 0, 0, dp(8));
        st.addView(banner);
        statusBox = vbox();
        st.addView(statusBox);
        TextView foot = text("Clocks update every minute and rates and weather about every 30 minutes, with no notification. "
                + "Only the SBB clock's moving second hand (Smooth / Every second) needs a small notification, because Android "
                + "requires one for live animation; choose Minute only to avoid it.", 12, Theme.uiMedium(this), Theme.DIM);
        foot.setPadding(0, dp(10), 0, 0);
        st.addView(foot);
        updateStatus();

        setContentView(room);

        // First launch: ask for everything the widgets need in one go.
        if (!Store.sp(this).getBoolean("permAsked", false)) {
            Store.sp(this).edit().putBoolean("permAsked", true).apply();
            List<String> need = new ArrayList<>();
            if (!Locator.allowed(this)) need.add(Manifest.permission.ACCESS_COARSE_LOCATION);
            if (!CalendarData.allowed(this)) need.add(Manifest.permission.READ_CALENDAR);
            if (!need.isEmpty()) requestPermissions(need.toArray(new String[0]), REQ_ALL);
            else askBackground();
        } else {
            askBackground(); // installs from before this was asked get asked once too
        }
    }

    /** Once, right after the other permissions: let the clocks run in the background (battery optimisation off). */
    private void askBackground() {
        if (Store.sp(this).getBoolean("batAsked", false)) return;
        android.os.PowerManager pm = (android.os.PowerManager) getSystemService(Context.POWER_SERVICE);
        if (pm == null || pm.isIgnoringBatteryOptimizations(getPackageName())) return;
        Store.sp(this).edit().putBoolean("batAsked", true).apply();
        try {
            startActivity(new android.content.Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    android.net.Uri.parse("package:" + getPackageName())));
        } catch (Exception ignored) { }
    }

    @Override
    protected void onResume() {
        super.onResume();
        updatePermissionCards();
        Context app = getApplicationContext();
        if (Locator.allowed(this)) {
            boolean moved = Locator.refreshFromLastKnown(app);
            if (Double.isNaN(Store.weatherLatLon(app)[0])) fetchLocation();
            else if (moved || Store.weather(app) == null) refreshNow(null);
        }
        CalendarWidgetProvider.updateAll(app);
        LiveService.ensure(app); // the app is in front, so Android lets the service start
        WidgetProvider.kick(app); // (re)start the per-minute clock alarms
        statusTicker.run();
        stage.setRunning(true);
    }

    @Override
    protected void onPause() {
        super.onPause();
        statusUi.removeCallbacks(statusTicker);
        stage.setRunning(false);
    }

    private final android.os.Handler statusUi = new android.os.Handler(android.os.Looper.getMainLooper());
    /** Keeps the Status card current while the app is open, so you can watch the clock updates arrive. */
    private final Runnable statusTicker = new Runnable() {
        @Override
        public void run() {
            statusUi.removeCallbacks(this);
            if (statusBox != null && banner != null) {
                int v = banner.getVisibility();
                updateStatus();
                banner.setVisibility(v); // a running "Updating…" message stays
            }
            statusUi.postDelayed(this, 5000);
        }
    };

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
        updatePermissionCards();
        askBackground();
        Context app = getApplicationContext();
        CalendarWidgetProvider.updateAll(app);
        if (Locator.allowed(this)) {
            if (!Locator.refreshFromLastKnown(app)) fetchLocation();
            refreshNow("Getting your location…");
        }
        LiveService.ensure(app);
    }

    // ------------------------------------------------------------------ actions

    private void save() {
        SharedPreferences.Editor e = Store.sp(this).edit();
        String feeIn = Util.cleanNum(fee.getText().toString());
        if (!Double.isNaN(Util.parseNum(feeIn))) e.putString("feeTxt", feeIn);
        e.putString("sbbMode", sbbMode);
        e.apply();
        refreshNow("Saved. Refreshing…");
    }

    /** Repaint every widget from the cache, then fetch fresh data in the background. msg == null: quiet. */
    private void refreshNow(String msg) {
        final Context app = getApplicationContext();
        WidgetProvider.updateAll(app);
        SbbWidgetProvider.updateAll(app);
        CalendarWidgetProvider.updateAll(app);
        LiveService.ensure(app);
        if (msg != null) {
            banner.setText(msg);
            banner.setVisibility(View.VISIBLE);
        }
        new Thread(() -> {
            Fetcher.refreshAll(app, true);
            WidgetProvider.updateAll(app);
            runOnUiThread(this::updateStatus);
            if (Fetcher.refreshHistory(app)) WidgetProvider.updateAll(app); // trend charts, after the rates
        }).start();
    }

    private void updatePermissionCards() {
        boolean loc = Locator.allowed(this);
        locStatus.setText(loc ? "Location allowed - weather and sunrise/sunset follow where you are."
                : "Needs location to show weather and sunrise/sunset where you are.");
        locStatus.setTextColor(loc ? Theme.TEXT : Theme.AMBER);
        locButton.setVisibility(loc ? View.GONE : View.VISIBLE);
        boolean cal = CalendarData.allowed(this);
        calStatus.setText(cal ? "Calendar allowed - the Calendar widget shows your next events."
                : "Needs calendar access for the Calendar widget. Events never leave your phone.");
        calStatus.setTextColor(cal ? Theme.TEXT : Theme.AMBER);
        calButton.setVisibility(cal ? View.GONE : View.VISIBLE);
        android.os.PowerManager pm = (android.os.PowerManager) getSystemService(Context.POWER_SERVICE);
        boolean bat = pm != null && pm.isIgnoringBatteryOptimizations(getPackageName());
        batStatus.setText(bat ? "Background allowed - the clocks keep ticking every minute."
                : "Battery saving may pause the clocks. Allow Worldly to run in the background.");
        batStatus.setTextColor(bat ? Theme.TEXT : Theme.AMBER);
        batButton.setVisibility(bat ? View.GONE : View.VISIBLE);
    }

    private void updateStatus() {
        banner.setVisibility(View.GONE);
        statusBox.removeAllViews();

        long t = Store.lastFetch(this);
        String last = t == 0 ? null
                : DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(new Date(t));
        statusRow("Data refresh", last != null, last == null ? "Not refreshed yet" : "Last successful: " + last);

        String visaErr = Store.sp(this).getString("visaErr", "");
        statusRow("Visa rate", visaErr.isEmpty(),
                visaErr.isEmpty() ? "Live rate from Visa" : visaErr);

        String wxErr = Store.sp(this).getString("wxErr", "");
        statusRow("Weather", wxErr.isEmpty() && Store.weather(this) != null,
                !wxErr.isEmpty() ? wxErr : (Store.weather(this) == null ? "Waiting for first update" : "Open-Meteo"));

        android.content.SharedPreferences sp = Store.sp(this);
        long ok = sp.getLong("clockOkAt", 0), bad = sp.getLong("clockErrAt", 0);
        java.text.SimpleDateFormat hms = new java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault());
        if (bad > ok) {
            statusRow("Clock updates", false, "Failed at " + hms.format(new Date(bad)) + " (" + sp.getString("clockErrWho", "")
                    + "): " + sp.getString("clockErrMsg", ""));
        } else {
            long nx = sp.getLong("clockNextAt", 0);
            String next = nx == 0 ? "" : " · next " + hms.format(new Date(nx)) + " (" + sp.getString("clockNextWho", "") + ")";
            statusRow("Clock updates", ok > 0 && System.currentTimeMillis() - ok < 3 * 60_000L,
                    (ok == 0 ? "No update yet" : "Last " + hms.format(new Date(ok)) + " (" + sp.getString("clockOkWho", "") + ")") + next);
        }

        String geoErr = Store.sp(this).getString("geoErr", "");
        statusRow("Cities", geoErr.isEmpty(), geoErr.isEmpty() ? "All cities found" : geoErr);
    }

    // ---- location ----

    @SuppressWarnings("deprecation")
    private void fetchLocation() {
        if (!Locator.allowed(this)) return;
        LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                String prov = lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
                        ? LocationManager.NETWORK_PROVIDER : LocationManager.GPS_PROVIDER;
                lm.getCurrentLocation(prov, null, getMainExecutor(), loc -> {
                    if (loc != null) {
                        Locator.save(this, loc);
                        refreshNow(null);
                    }
                });
            } catch (SecurityException | IllegalArgumentException ignored) { }
        }
    }

    // ---- pin widget ----

    private void pin(Class<?> provider) {
        AppWidgetManager awm = getSystemService(AppWidgetManager.class);
        if (awm != null && awm.isRequestPinAppWidgetSupported()) {
            awm.requestPinAppWidget(new ComponentName(this, provider), null, null);
        } else {
            Toast.makeText(this, "Long-press your home screen > Widgets > Worldly.", Toast.LENGTH_LONG).show();
        }
    }

    private void paintModes() {
        for (int i = 0; i < 3; i++) {
            boolean on = MODES[i].equals(sbbMode);
            modeBtn[i].setTextColor(on ? 0xFFFFFFFF : Theme.MUTED);
            modeBtn[i].setBackground(on ? Theme.lit(this, 14, false) : Theme.well(this, 14, false));
        }
    }

    // ------------------------------------------------------------------ pickers

    private interface Pick<T> { void on(T value); }

    private static final class Item {
        final String title, sub;
        final Object payload;
        Item(String title, String sub, Object payload) { this.title = title; this.sub = sub; this.payload = payload; }
        String hay() { return (title + " " + sub).toLowerCase(Locale.ROOT); }
    }

    private String currencyLabel(String code) {
        String c = code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
        try {
            return c + "  ·  " + Currency.getInstance(c).getDisplayName(Locale.ENGLISH);
        } catch (Exception e) {
            return c;
        }
    }

    private void pickCity(final int slot) {
        List<Item> items = new ArrayList<>();
        for (CityData.City c : CityData.all(this)) items.add(new Item(c.name, c.country() + "  ·  " + c.tz, c));
        showPicker("City " + (slot + 1), items, true, p -> {
            JSONObject geo;
            String name;
            if (p.payload instanceof CityData.City) {
                CityData.City c = (CityData.City) p.payload;
                name = c.name;
                geo = c.toGeo();
            } else {
                JSONObject o = (JSONObject) p.payload;
                name = p.title;
                geo = o;
            }
            Store.putGeo(this, name, geo);
            Store.sp(this).edit().putString("city" + slot, name).apply();
            cityBtn[slot].setText(name);
            refreshNow("Updating…");
        });
    }

    private void pickCurrency(String title, final Pick<String> done) {
        List<Item> items = new ArrayList<>();
        List<Currency> all = new ArrayList<>(Currency.getAvailableCurrencies());
        java.util.Collections.sort(all, (a, b) -> a.getCurrencyCode().compareTo(b.getCurrencyCode()));
        for (Currency c : all) {
            String code = c.getCurrencyCode();
            if (code.startsWith("X") || code.length() != 3) continue; // metals, test codes, supranational units
            items.add(new Item(code, c.getDisplayName(Locale.ENGLISH), code));
        }
        showPicker(title, items, false, p -> done.on((String) p.payload));
    }

    /** Searchable list dialog. With {@code remote} the search box can also look cities up online (Open-Meteo, no key). */
    private void showPicker(String title, final List<Item> base, final boolean remote, final Pick<Item> done) {
        final Dialog d = new Dialog(this);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout root = vbox();
        root.setPadding(dp(14), dp(14), dp(14), dp(10));
        root.setBackground(Theme.box(this, 0xF20A0F16, Theme.BORDER_STRONG, 18));
        TextView t = text(title, 11, Theme.uiBold(this), Theme.AMBER_LIGHT);
        Theme.label(this, t);
        t.setPadding(dp(2), 0, 0, dp(8));
        root.addView(t);
        final EditText q = input("", InputType.TYPE_CLASS_TEXT, false, remote ? "Search or type any city" : "Search");
        root.addView(q);
        final ListView lv = new ListView(this);
        lv.setDividerHeight(0);
        lv.setSelector(android.R.color.transparent);
        final List<Item> shown = new ArrayList<>(base);
        final BaseAdapter ad = new BaseAdapter() {
            @Override public int getCount() { return shown.size(); }
            @Override public Object getItem(int i) { return shown.get(i); }
            @Override public long getItemId(int i) { return i; }
            @Override public View getView(int i, View v, ViewGroup parent) {
                LinearLayout r = vbox();
                r.setPadding(dp(10), dp(9), dp(10), dp(9));
                Item it = shown.get(i);
                r.addView(text(it.title, 15, Theme.uiSemi(MainActivity.this), Theme.TEXT));
                if (!it.sub.isEmpty()) r.addView(text(it.sub, 12, Theme.uiMedium(MainActivity.this), Theme.MUTED));
                return r;
            }
        };
        lv.setAdapter(ad);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(380));
        lp.topMargin = dp(8);
        root.addView(lv, lp);
        lv.setOnItemClickListener((parent, view, pos, id) -> {
            d.dismiss();
            done.on(shown.get(pos));
        });

        final Runnable[] pending = new Runnable[1];
        final List<Item> online = new ArrayList<>();
        q.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable s) {
                final String f = s.toString().trim().toLowerCase(Locale.ROOT);
                filter(f);
                if (pending[0] != null) ui.removeCallbacks(pending[0]);
                if (remote && f.length() >= 3) {
                    pending[0] = () -> new Thread(() -> {
                        List<Item> found = geocodeSearch(f);
                        ui.post(() -> {
                            online.clear();
                            online.addAll(found);
                            filter(q.getText().toString().trim().toLowerCase(Locale.ROOT));
                        });
                    }).start();
                    ui.postDelayed(pending[0], 450);
                }
            }

            private void filter(String f) {
                shown.clear();
                for (Item it : base) if (f.isEmpty() || it.hay().contains(f)) shown.add(it);
                for (Item it : online) {
                    boolean dup = false;
                    for (Item s2 : shown) if (s2.title.equalsIgnoreCase(it.title) && s2.sub.equals(it.sub)) { dup = true; break; }
                    if (!dup && (f.isEmpty() || it.hay().contains(f.substring(0, Math.min(3, f.length()))))) shown.add(it);
                }
                ad.notifyDataSetChanged();
            }
        });

        d.setContentView(root);
        if (d.getWindow() != null) {
            d.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0));
            d.getWindow().setLayout(Math.round(getResources().getDisplayMetrics().widthPixels * 0.92f), ViewGroup.LayoutParams.WRAP_CONTENT);
            d.getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
        d.show();
    }

    /** Open-Meteo geocoding: free, no key. Called from a background thread. */
    private List<Item> geocodeSearch(String query) {
        List<Item> out = new ArrayList<>();
        try {
            String url = "https://geocoding-api.open-meteo.com/v1/search?name=" + URLEncoder.encode(query, "UTF-8")
                    + "&count=10&language=en&format=json";
            JSONObject root = new JSONObject(Net.get(url));
            JSONArray rs = root.optJSONArray("results");
            if (rs == null) return out;
            for (int i = 0; i < rs.length(); i++) {
                JSONObject r = rs.getJSONObject(i);
                if (!r.has("timezone")) continue;
                JSONObject g = new JSONObject();
                g.put("lat", r.getDouble("latitude"));
                g.put("lon", r.getDouble("longitude"));
                g.put("tz", r.getString("timezone"));
                g.put("cc", r.optString("country_code", ""));
                String admin = r.optString("admin1", "");
                String sub = r.optString("country", "") + (admin.isEmpty() ? "" : "  ·  " + admin) + "  ·  " + r.getString("timezone");
                out.add(new Item(r.getString("name"), sub, g));
            }
        } catch (Exception ignored) { }
        return out;
    }

    /** Looks like an input but opens a list when tapped. */
    private TextView picker(String value, View.OnClickListener l) {
        TextView t = text(value, 14, Theme.uiMedium(this), Theme.TEXT);
        t.setSingleLine(true);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        t.setPadding(dp(12), dp(12), dp(12), dp(12));
        t.setBackground(Theme.pressable(Theme.well(this, 12, false), Theme.well(this, 12, true)));
        t.setCompoundDrawablePadding(dp(6));
        t.setClickable(true);
        t.setOnClickListener(l);
        return t;
    }

    private LinearLayout labeledView(String label, View v) {
        LinearLayout l = vbox();
        TextView t = text(label, 10, Theme.uiBold(this), Theme.DIM);
        Theme.label(this, t);
        t.setTextSize(10);
        t.setPadding(dp(2), 0, 0, dp(4));
        l.addView(t);
        l.addView(v, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return l;
    }

    // ------------------------------------------------------------------ UI building blocks

    private int dp(int v) { return Math.round(Theme.dp(this, v)); }

    private LinearLayout vbox() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    private LinearLayout hbox() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.HORIZONTAL);
        return l;
    }

    private LinearLayout.LayoutParams matchWrap(int topDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(topDp);
        return lp;
    }

    private TextView text(String s, float sp, Typeface tf, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTypeface(tf);
        t.setTextColor(color);
        return t;
    }

    /** A glass panel over the stage, with its title in spaced capitals in the key light. */
    private LinearLayout card(LinearLayout parent, String title) {
        LinearLayout c = vbox();
        c.setBackground(Theme.glass(this, 16));
        c.setPadding(dp(14), dp(14), dp(14), dp(14));
        TextView t = text(title, 11, Theme.uiBold(this), Theme.AMBER_LIGHT);
        Theme.label(this, t);
        t.setPadding(dp(2), 0, 0, dp(10));
        c.addView(t);
        parent.addView(c, matchWrap(14));
        return c;
    }

    /** A well in the glass whose edge lights up in the key on focus. */
    private EditText input(String value, int inputType, boolean mono, String hint) {
        final EditText e = new EditText(this);
        e.setInputType(inputType);
        e.setSingleLine(true);
        e.setText(value);
        e.setHint(hint);
        e.setTextSize(14);
        e.setTypeface(mono ? Theme.monoMedium(this) : Theme.uiMedium(this));
        e.setTextColor(Theme.TEXT);
        e.setHintTextColor(Theme.DIM);
        e.setHighlightColor(0x55EB9438);
        e.setPadding(dp(12), dp(11), dp(12), dp(11));
        e.setBackground(Theme.well(this, 12, false));
        e.setOnFocusChangeListener((v, focused) -> e.setBackground(Theme.well(this, 12, focused)));
        return e;
    }

    private void addWeighted(LinearLayout row, View v, int leftMarginDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp.leftMargin = dp(leftMarginDp);
        row.addView(v, lp);
    }

    /** The main action is lit: the key on the glass, a glowing edge, white spaced capitals. */
    private TextView primaryButton(String label, View.OnClickListener l) {
        TextView b = text(label, 15, Theme.uiBold(this), 0xFFFFFFFF);
        Theme.label(this, b);
        b.setTextSize(13);
        b.setLetterSpacing(0.18f);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(16), dp(17), dp(16), dp(17));
        b.setBackground(Theme.pressable(Theme.lit(this, 16, false), Theme.lit(this, 16, true)));
        b.setClickable(true);
        b.setOnClickListener(l);
        return b;
    }

    /** Other actions: a hairline outline on the glass that lights up while pressed. */
    private TextView secondaryButton(String label, View.OnClickListener l) {
        TextView b = text(label, 14, Theme.uiSemi(this), Theme.TEXT_SOFT);
        Theme.label(this, b);
        b.setTextSize(12);
        b.setLetterSpacing(0.14f);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(16), dp(14), dp(16), dp(14));
        b.setBackground(Theme.pressable(Theme.box(this, Theme.GLASS, Theme.BORDER_STRONG, 16), Theme.well(this, 16, true)));
        b.setClickable(true);
        b.setOnClickListener(l);
        return b;
    }

    /** Colored dot + name + detail. Green = OK, amber = needs attention. */
    private void statusRow(String name, boolean ok, String detail) {
        LinearLayout row = hbox();
        row.setPadding(0, dp(6), 0, dp(6));
        TextView dot = text("●", 12, Typeface.DEFAULT, ok ? Theme.GREEN : Theme.AMBER);
        dot.setPadding(0, dp(3), dp(10), 0);
        row.addView(dot);
        LinearLayout txt = vbox();
        txt.addView(text(name, 14, Theme.uiSemi(this), Theme.TEXT));
        TextView d = text(detail, 12, Theme.uiMedium(this), Theme.MUTED);
        d.setPadding(0, dp(1), 0, 0);
        txt.addView(d);
        row.addView(txt, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        statusBox.addView(row);
    }
}
