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
import android.graphics.drawable.GradientDrawable;
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
import android.view.WindowInsets;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.text.DateFormat;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Currency;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * The app (2.20): four tabs, after the references. A frosted glass tab bar across the bottom (the pages blur through
 * it), each tab an icon over its name, the open one white and filled; a floating "Refresh" pill just above it. Each page
 * opens as the car app's screen does: the crystal globe in a glass ring with the page's name. Choices are segmented
 * controls ({@link Segmented}). Cities and currencies are picked from lists. (2.20 had a Home tab too; it went in 2.22.)
 */
public class MainActivity extends Activity {
    private static final int CLOCKS = 0, RATES = 1, WIDGETS = 2, STATUS = 3;
    private static final String[] TABS = {"Clocks", "Rates", "Widgets", "Status"};
    private static final int[] TAB_GLYPHS = {Glyph.CLOCK, Glyph.RATES, Glyph.WIDGETS, Glyph.STATUS};
    private static final String[] MODES = {"smooth", "second", "minute"};

    private final ScrollView[] pages = new ScrollView[TABS.length];
    private final LinearLayout[] cols = new LinearLayout[TABS.length];
    private final ImageView[] tabIcon = new ImageView[TABS.length];
    private final TextView[] tabWord = new TextView[TABS.length];
    private int tab = CLOCKS;
    private boolean resumed;

    private FrameLayout room;
    private BackdropFrame behind;
    private GlassBar dock;
    private LinearLayout pill;
    private TextView pillWord;
    private View topShade, bottomFade;
    private StageView stage;

    // clocks
    private final TextView[] cityName = new TextView[8], cityWhen = new TextView[8], cityTime = new TextView[8];
    /** The clock tiles' grid, built again when the number of clocks changes (2.26). */
    private LinearLayout cityGrid;
    // rates
    private LinearLayout ratesList, toList;
    private EditText fee;
    // status
    private LinearLayout accessList, liveList;

    private final Handler ui = new Handler(Looper.getMainLooper());
    /** The light the screen was drawn in: when the song has changed by the time it's back in front, it's redrawn. */
    private String drawnIn;
    private int insetTop, insetBottom;
    private boolean typing;
    private int busy;

    private static final int REQ_ALL = 1;
    private static final int PAD = 18;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        drawnIn = Theme.accent().tag();
        if (b != null) tab = Math.max(0, Math.min(TABS.length - 1, b.getInt("tab", CLOCKS)));
        edgeToEdge();

        // The room: the stage behind, the pages over it, both in a frame of their own so the tab bar can show them
        // blurred (BackdropFrame, GlassBar)
        room = new FrameLayout(this);
        behind = new BackdropFrame(this);
        room.addView(behind, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        stage = new StageView(this);
        behind.addView(stage, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        for (int i = 0; i < TABS.length; i++) {
            ScrollView sv = new ScrollView(this);
            sv.setBackgroundColor(0);
            sv.setFillViewport(true);
            sv.setVerticalScrollBarEnabled(false);
            LinearLayout col = vbox();
            sv.addView(col, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            behind.addView(sv, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            pages[i] = sv;
            cols[i] = col;
        }

        buildClocks(cols[CLOCKS]);
        buildRates(cols[RATES]);
        buildWidgets(cols[WIDGETS]);
        buildStatus(cols[STATUS]);

        // under the status bar, the pages fade into the dark; above the tab bar too, where the pill floats
        topShade = new View(this);
        topShade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Scene.alpha(Theme.BG, 0.94f), Scene.alpha(Theme.BG, 0.6f), Scene.alpha(Theme.BG, 0f)}));
        room.addView(topShade, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(24), Gravity.TOP));
        bottomFade = new View(this);
        bottomFade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Scene.alpha(Theme.BG, 0f), Scene.alpha(Theme.BG, 0.55f), Scene.alpha(Theme.BG, 0.8f)}));
        room.addView(bottomFade, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(150), Gravity.BOTTOM));

        // ---- the tab bar: frosted glass across the bottom, each tab an icon over its name ----
        dock = new GlassBar(this, behind, 0, true);
        dock.setOrientation(LinearLayout.HORIZONTAL);
        dock.setGravity(Gravity.CENTER_VERTICAL);
        for (int i = 0; i < TABS.length; i++) {
            final int t = i;
            LinearLayout item = vbox();
            item.setGravity(Gravity.CENTER_HORIZONTAL);
            item.setPadding(0, dp(2), 0, dp(2));
            ImageView icon = new ImageView(this);
            tabIcon[i] = icon;
            item.addView(icon, new LinearLayout.LayoutParams(dp(26), dp(26)));
            TextView word = text(TABS[i].toUpperCase(Locale.getDefault()), 11, Theme.uiBold(this), Segmented.OFF);
            word.setLetterSpacing(0.12f);
            word.setPadding(0, dp(6), 0, 0);
            word.setGravity(Gravity.CENTER_HORIZONTAL);
            tabWord[i] = word;
            item.addView(word);
            item.setClickable(true);
            item.setOnClickListener(v -> selectTab(t, true));
            dock.addView(item, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }
        room.addView(dock, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM));
        for (ScrollView sv : pages) sv.setOnScrollChangeListener((v, x, y, ox, oy) -> dock.refresh());

        // ---- the floating pill: refresh everything ----
        pill = hbox();
        pill.setGravity(Gravity.CENTER_VERTICAL);
        pill.setPadding(dp(24), dp(13), dp(20), dp(13));
        pill.setBackground(Theme.pill(this, 0xFFEEF2F7, false));
        pill.setElevation(dp(10));
        pillWord = plain("Refresh", 13, Theme.uiBold(this), 0xFF0B0D12);
        pillWord.setAllCaps(true); // a button: spaced capitals (2.23)
        pillWord.setLetterSpacing(0.16f);
        pill.addView(pillWord);
        ImageView pillIcon = new ImageView(this);
        pillIcon.setImageDrawable(new Glyph(Glyph.REFRESH, 0xFF0B0D12, false, 2f));
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(dp(19), dp(19));
        ip.leftMargin = dp(10);
        pill.addView(pillIcon, ip);
        pill.setClickable(true);
        pill.setOnClickListener(v -> save());
        room.addView(pill, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL));

        room.setOnApplyWindowInsetsListener((v, in) -> {
            int top, bottom, ime = 0;
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = in.getInsets(WindowInsets.Type.systemBars());
                top = bars.top;
                bottom = bars.bottom;
                ime = in.getInsets(WindowInsets.Type.ime()).bottom;
            } else {
                top = in.getSystemWindowInsetTop();
                bottom = in.getSystemWindowInsetBottom();
                if (bottom > dp(120)) { // the keyboard (adjustResize puts it in the bottom inset)
                    ime = bottom;
                    bottom = in.getStableInsetBottom();
                }
            }
            applyInsets(top, bottom, ime);
            return in;
        });
        applyInsets(0, 0, 0);

        setContentView(room);
        selectTab(tab, false);
        updatePermissionCards();
        updateStatus();

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

    /** The pages run under the status bar and the tab bar under the navigation bar, as in the references. */
    private void edgeToEdge() {
        Window w = getWindow();
        w.setStatusBarColor(0);
        w.setNavigationBarColor(0);
        if (Build.VERSION.SDK_INT >= 29) {
            w.setStatusBarContrastEnforced(false);
            w.setNavigationBarContrastEnforced(false);
        }
        if (Build.VERSION.SDK_INT >= 30) {
            w.setDecorFitsSystemWindows(false);
        } else {
            w.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        }
        w.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
    }

    /** Room for the status bar ({@code top}), the navigation bar ({@code bottom}) and the keyboard ({@code ime}). */
    private void applyInsets(int top, int bottom, int ime) {
        insetTop = top;
        insetBottom = bottom;
        typing = ime > 0;
        int dockH = dp(70) + bottom;
        dock.setPadding(0, dp(10), 0, dp(10) + bottom);
        int below = typing ? ime + dp(24) : dockH + dp(92);
        for (LinearLayout col : cols) col.setPadding(dp(PAD), top + dp(18), dp(PAD), below);
        FrameLayout.LayoutParams pp = (FrameLayout.LayoutParams) pill.getLayoutParams();
        if (pp != null) {
            pp.bottomMargin = dockH + dp(16);
            pill.setLayoutParams(pp);
        }
        ViewGroup.LayoutParams sp = topShade.getLayoutParams();
        sp.height = top + dp(20);
        topShade.setLayoutParams(sp);
        ViewGroup.LayoutParams fp = bottomFade.getLayoutParams();
        fp.height = dockH + dp(96);
        bottomFade.setLayoutParams(fp);
        int show = typing ? View.GONE : View.VISIBLE;
        dock.setVisibility(show);
        pill.setVisibility(show);
        bottomFade.setVisibility(show);
        if (typing) revealFocus();
    }

    /** With the keyboard up, the field being typed in is scrolled into sight. */
    private void revealFocus() {
        final View f = getCurrentFocus();
        if (f == null) return;
        final ScrollView sv = pages[tab];
        sv.post(() -> {
            android.graphics.Rect r = new android.graphics.Rect();
            f.getDrawingRect(r);
            sv.offsetDescendantRectToMyCoords(f, r);
            int visible = sv.getHeight() - cols[tab].getPaddingBottom();
            if (r.bottom - sv.getScrollY() > visible) sv.smoothScrollTo(0, r.bottom - visible + dp(24));
        });
    }

    /** Opens tab {@code t}: its page shows (fading in, when {@code animate}), its icon fills and turns white. */
    private void selectTab(int t, boolean animate) {
        boolean same = t == tab && animate;
        tab = t;
        for (int i = 0; i < TABS.length; i++) {
            boolean on = i == t;
            pages[i].setVisibility(on ? View.VISIBLE : View.GONE);
            tabIcon[i].setImageDrawable(new Glyph(TAB_GLYPHS[i], on ? Segmented.ON : Segmented.OFF, on, 1.7f));
            tabWord[i].setTextColor(on ? Segmented.ON : Segmented.OFF);
        }
        if (same) {
            pages[t].smoothScrollTo(0, 0); // the open tab tapped again: back to its top
        } else if (animate) {
            pages[t].setAlpha(0f);
            pages[t].setTranslationY(dp(6));
            pages[t].animate().alpha(1f).translationY(0).setDuration(200).start();
        }
        dock.refresh();
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt("tab", tab);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        if (tab != CLOCKS) selectTab(CLOCKS, true);
        else super.onBackPressed();
    }

    // ------------------------------------------------------------------ pages

    /** Clocks: the four cities as tiles (tap one to change it), and how their skies move. */
    private void buildClocks(LinearLayout col) {
        header(col, "Clocks", "The cities on your World clocks widget");
        cityGrid = vbox();
        col.addView(cityGrid, matchWrap(0));
        fillClockTiles();
        hint(col, "Tap a city to choose another. Can't find yours? Type its name in the list's search box.");

        section(col, "Clocks on the widget");
        int at = 0;
        String[] counts = new String[ClockView.COUNTS.length];
        for (int k = 0; k < counts.length; k++) {
            counts[k] = String.valueOf(ClockView.COUNTS[k]);
            if (ClockView.COUNTS[k] == Store.clockCount(this)) at = k;
        }
        Segmented count = new Segmented(this, counts, (int[]) null, at, k -> {
            Store.setClockCount(this, ClockView.COUNTS[k]);
            fillClockTiles();
            refreshNow("Updating…");
        });
        col.addView(count, matchWrap(0));
        hint(col, "Up to four clocks sit in one row; six or eight in two even rows, so every clock keeps its shape.");

        section(col, "Sky motion");
        Segmented skies = new Segmented(this, new String[]{"Animated", "Still"}, new int[]{Glyph.SPARKLE, Glyph.IMAGE},
                Store.animateSkies(this) ? 0 : 1, i -> {
                    Store.setAnimateSkies(this, i == 0);
                    WidgetProvider.updateAll(getApplicationContext());
                    LiveService.ensure(getApplicationContext());
                });
        col.addView(skies, matchWrap(0));
        hint(col, "Animated: the stars twinkle, the clouds drift, the trees sway with the wind and now and then a plane "
                + "crosses far away, while the screen is on. It needs the same small notification as the Sound widget and "
                + "uses a little more battery. Still: one picture a minute.");
    }

    /** Rates: the currencies (tap one to change it), how many rows, what they convert to, the bank's fee. */
    private void buildRates(LinearLayout col) {
        header(col, "Rates", "Market and Visa, side by side");
        section(col, "Your currencies");
        ratesList = listCard(col);
        section(col, "Rows on the widget");
        Segmented count = new Segmented(this, new String[]{"1", "2", "3", "4", "5", "6"}, (int[]) null, Store.count(this) - 1, i -> {
            Store.setCount(this, i + 1);
            fillRates();
            refreshNow("Updating rates…");
        });
        col.addView(count, matchWrap(0));
        section(col, "Convert to");
        toList = listCard(col);
        section(col, "Visa fee");
        LinearLayout feeCard = listCard(col);
        LinearLayout row = listRow(feeCard, true);
        LinearLayout words = vbox();
        words.addView(text("Your bank's fee", 16, Theme.uiMedium(this), Theme.TEXT));
        TextView d = text("Added to Visa's rate, as on your statement", 12.5f, Theme.uiMedium(this), Theme.MUTED);
        d.setPadding(0, dp(2), 0, 0);
        words.addView(d);
        row.addView(words, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        fee = input(Store.feeText(this), InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL, true, "0");
        fee.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        fee.setHorizontallyScrolling(false); // a short number, set against the right edge without scrolling
        fee.setMaxLines(1);
        fee.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable s) {
                ui.removeCallbacks(saveFee);
                ui.postDelayed(saveFee, 900);
            }
        });
        row.addView(fee, new LinearLayout.LayoutParams(dp(78), ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView pct = text("%", 16, Theme.uiMedium(this), Theme.MUTED);
        pct.setPadding(dp(8), 0, 0, 0);
        row.addView(pct);
        fillRates();
    }

    /** The bank's fee, kept a moment after it's typed (the widgets show it from the next refresh). */
    private final Runnable saveFee = () -> {
        String in = Util.cleanNum(fee.getText().toString());
        if (!Double.isNaN(Util.parseNum(in)) && !in.equals(Store.feeText(this))) {
            Store.sp(this).edit().putString("feeTxt", in).apply();
            WidgetProvider.updateAll(getApplicationContext());
        }
    };

    /** The currency rows and the one they convert to, each a line in its card. */
    private void fillRates() {
        ratesList.removeAllViews();
        String to = Store.to(this);
        int n = Store.count(this);
        for (int i = 0; i < n; i++) {
            final int idx = i;
            String code = Store.from(this, i);
            double market = Store.rate(this, code.trim().toUpperCase(Locale.ROOT), to.trim().toUpperCase(Locale.ROOT));
            currencyRow(ratesList, i == 0, code, Double.isNaN(market) ? "" : rateText(market) + " " + to,
                    v -> pickCurrency("Currency " + (idx + 1), c -> {
                        Store.sp(this).edit().putString("fx" + idx, c).apply();
                        fillRates();
                        refreshNow("Updating rates…");
                    }));
        }
        toList.removeAllViews();
        currencyRow(toList, true, to, "", v -> pickCurrency("Convert to", c -> {
            Store.sp(this).edit().putString("to", c).apply();
            fillRates();
            refreshNow("Updating rates…");
        }));
    }

    /** A currency's line: its flag, code and name, what it's worth on the right, and a chevron. */
    private void currencyRow(LinearLayout card, boolean first, String code, String value, View.OnClickListener l) {
        String c = code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
        LinearLayout row = listRow(card, first);
        ImageView flag = new ImageView(this);
        flag.setImageBitmap(CurrencyView.flagOrLabel(this, Util.countryOfCurrency(c), c));
        LinearLayout.LayoutParams fp = new LinearLayout.LayoutParams(dp(32), dp(32));
        fp.rightMargin = dp(14);
        row.addView(flag, fp);
        LinearLayout words = vbox();
        words.addView(text(c, 16, Theme.uiMedium(this), Theme.TEXT));
        String name;
        try {
            name = Currency.getInstance(c).getDisplayName(Locale.ENGLISH);
        } catch (Exception e) {
            name = "";
        }
        TextView n = text(name, 12.5f, Theme.uiMedium(this), Theme.MUTED);
        n.setSingleLine(true);
        n.setEllipsize(android.text.TextUtils.TruncateAt.END);
        n.setPadding(0, dp(2), 0, 0);
        words.addView(n);
        row.addView(words, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        if (!value.isEmpty()) {
            TextView v = text(value, 14, Theme.uiMedium(this), Theme.TEXT_SOFT);
            v.setPadding(dp(8), 0, dp(6), 0);
            row.addView(v);
        }
        row.addView(chevron(), new LinearLayout.LayoutParams(dp(18), dp(18)));
        row.setClickable(true);
        row.setOnClickListener(l);
    }

    /** Widgets: a tile for each (tap to add it to the home screen), and how the SBB clock moves. */
    private void buildWidgets(LinearLayout col) {
        header(col, "Widgets", "Tap one to add it to your home screen");
        Object[][] all = {
                {"Clocks", "World clocks on their skies", Glyph.GLOBE, ClockWidgetProvider.class},
                {"Rates", "Market and Visa rates", Glyph.RATES, WidgetProvider.class},
                {"SBB clock", "The Swiss railway clock", Glyph.CLOCK, SbbWidgetProvider.class},
                {"Calendar", "Your week and next events", Glyph.CALENDAR, CalendarWidgetProvider.class},
                {"Sound", "Lights that follow the music", Glyph.LEVELS, SoundWidgetProvider.class},
                {"Music", "Play, pause, skip and seek", Glyph.NOTE, PlayerWidgetProvider.class}};
        LinearLayout row = null;
        for (int i = 0; i < all.length; i++) {
            final Class<?> provider = (Class<?>) all[i][3];
            boolean wide = i == all.length - 1 && all.length % 2 == 1;
            if (i % 2 == 0) {
                row = hbox();
                col.addView(row, matchWrap(i == 0 ? 0 : 10));
            }
            LinearLayout t = tile(false);
            t.setGravity(Gravity.START);
            t.setPadding(dp(16), dp(16), dp(16), dp(16));
            LinearLayout top = hbox();
            FrameLayout disc = new FrameLayout(this);
            disc.setBackground(Theme.pill(this, 0xFF1E222A, true));
            ImageView g = new ImageView(this);
            g.setImageDrawable(new Glyph((Integer) all[i][2], Theme.TEXT, false, 1.6f));
            disc.addView(g, new FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER));
            top.addView(disc, new LinearLayout.LayoutParams(dp(42), dp(42)));
            top.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));
            ImageView plus = new ImageView(this);
            plus.setImageDrawable(new Glyph(Glyph.PLUS, Segmented.OFF, false, 1.8f));
            top.addView(plus, new LinearLayout.LayoutParams(dp(20), dp(20)));
            t.addView(top, matchWrap(0));
            t.addView(new View(this), new LinearLayout.LayoutParams(1, 0, 1f));
            TextView name = text(((String) all[i][0]).toUpperCase(Locale.getDefault()), 16, Theme.uiMedium(this), Theme.TEXT);
            name.setLetterSpacing(0.06f);
            t.addView(name);
            TextView d = text((String) all[i][1], 12.5f, Theme.uiMedium(this), Theme.MUTED);
            d.setPadding(0, dp(3), 0, 0);
            t.addView(d);
            t.setClickable(true);
            t.setOnClickListener(v -> pin(provider));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(wide ? 128 : 150), 1f);
            lp.leftMargin = i % 2 == 0 ? 0 : dp(10);
            row.addView(t, lp);
        }

        section(col, "SBB clock motion");
        int mode = 0;
        for (int i = 0; i < 3; i++) if (MODES[i].equals(Store.sbbMode(this))) mode = i;
        Segmented modes = new Segmented(this, new String[]{"Smooth", "Second", "Minute"},
                new int[]{Glyph.WAVE, Glyph.STOPWATCH, Glyph.CLOCK}, mode, i -> {
                    Store.sp(this).edit().putString("sbbMode", MODES[i]).apply();
                    SbbWidgetProvider.updateAll(getApplicationContext());
                    LiveService.ensure(getApplicationContext());
                });
        col.addView(modes, matchWrap(0));
        hint(col, "Smooth: the second hand runs a lap in 58.5 s and the minute hand swings like the real clock. Second: one "
                + "picture a second. Minute: a still picture each minute, with no notification. The clock always shows the "
                + "time where you are.");
    }

    /** Status: what the widgets are allowed to use, and how the live data is doing. */
    private void buildStatus(LinearLayout col) {
        header(col, "Status", "Access and live data");
        section(col, "Access");
        accessList = listCard(col);
        section(col, "Live data");
        liveList = listCard(col);
        hint(col, "Clocks update every minute and rates and weather about every 30 minutes, with no notification. Only "
                + "moving pictures (the SBB clock's second hand, animated skies, the Sound widget) need a small "
                + "notification, because Android requires one for live animation.");
    }

    // ------------------------------------------------------------------ lifecycle

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
        resumed = true;
        updatePermissionCards();
        Context app = getApplicationContext();
        if (Locator.allowed(this)) {
            boolean moved = Locator.refreshFromLastKnown(app);
            if (Double.isNaN(Store.weatherLatLon(app)[0])) fetchLocation();
            else if (moved || Store.weather(app) == null) refreshNow(null);
        }
        CalendarWidgetProvider.updateAll(app);
        SoundWidgetProvider.refresh(app); // media access may have just been turned on
        SoundWidgetProvider.updateAll(app);
        PlayerWidgetProvider.updateAll(app);
        LiveService.ensure(app); // the app is in front, so Android lets the service start
        WidgetProvider.kick(app); // (re)start the per-minute clock alarms
        statusTicker.run();
        stage.setRunning(true);
        // The song changed while the screen was away: draw it again in the new light.
        ui.post(() -> {
            if (!isFinishing() && !Theme.accent().tag().equals(drawnIn)) recreate();
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        resumed = false;
        ui.removeCallbacks(statusTicker);
        stage.setRunning(false);
        if (fee != null) {
            ui.removeCallbacks(saveFee);
            saveFee.run();
        }
    }

    /** Keeps the pages current while the app is open: the clocks' times, the tiles, the live data. */
    private final Runnable statusTicker = new Runnable() {
        @Override
        public void run() {
            ui.removeCallbacks(this);
            updateStatus();
            updateClockTiles();
            ui.postDelayed(this, 5000);
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
            refreshNow("Finding you…");
        }
        LiveService.ensure(app);
    }

    // ------------------------------------------------------------------ actions

    private void save() {
        if (fee != null) {
            SharedPreferences.Editor e = Store.sp(this).edit();
            String feeIn = Util.cleanNum(fee.getText().toString());
            if (!Double.isNaN(Util.parseNum(feeIn))) e.putString("feeTxt", feeIn);
            e.apply();
        }
        refreshNow("Refreshing…");
    }

    /**
     * Repaint every widget from the cache, then fetch fresh data in the background. {@code msg} shows on the pill
     * while it works (null: quietly).
     */
    private void refreshNow(String msg) {
        final Context app = getApplicationContext();
        WidgetProvider.updateAll(app);
        SbbWidgetProvider.updateAll(app);
        CalendarWidgetProvider.updateAll(app);
        LiveService.ensure(app);
        if (msg != null) {
            busy++;
            pillWord.setText(msg);
        }
        final boolean shown = msg != null;
        new Thread(() -> {
            Fetcher.refreshAll(app, true);
            WidgetProvider.updateAll(app);
            runOnUiThread(() -> {
                if (shown && --busy <= 0) {
                    busy = 0;
                    pillWord.setText("Refresh");
                }
                updateStatus();
                fillRates();
            });
            if (Fetcher.refreshHistory(app)) WidgetProvider.updateAll(app); // the history, after the rates
        }).start();
    }

    private void updatePermissionCards() {
        accessList.removeAllViews();
        boolean loc = Locator.allowed(this);
        accessRow(true, Glyph.PIN, "Location", loc ? "Weather and sunrise follow where you are"
                : "For the weather and sunrise where you are", loc,
                v -> requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, REQ_ALL));
        boolean cal = CalendarData.allowed(this);
        accessRow(false, Glyph.CALENDAR, "Calendar", cal ? "The Calendar widget shows your next events"
                : "For the Calendar widget. Events never leave your phone", cal,
                v -> requestPermissions(new String[]{Manifest.permission.READ_CALENDAR}, REQ_ALL));
        android.os.PowerManager pm = (android.os.PowerManager) getSystemService(Context.POWER_SERVICE);
        boolean bat = pm != null && pm.isIgnoringBatteryOptimizations(getPackageName());
        accessRow(false, Glyph.BATTERY, "Background", bat ? "The clocks keep ticking every minute"
                : "Battery saving may pause the clocks", bat, v -> {
            try {
                startActivity(new android.content.Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        android.net.Uri.parse("package:" + getPackageName())));
            } catch (Exception e) {
                startActivity(new android.content.Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
            }
        });
        boolean media = NowPlaying.hasAccess(this);
        accessRow(false, Glyph.NOTE, "Media", media ? "Sound and Music see what's playing in any app"
                : "For Sound and Music to see what's playing. No notifications are read", media, v -> openMediaAccess());
        boolean levels = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
        String heard = Store.sp(this).getString("soundStatus", "");
        String levelsText;
        if (!levels) {
            levelsText = "So the lights follow the real sound (the phone's own, never the microphone)";
        } else if ("FOLLOWING".equals(heard)) {
            levelsText = "The lights follow the real sound";
        } else if ("PHONE_SILENT".equals(heard) || "UNAVAILABLE".equals(heard)) {
            levelsText = "This phone keeps its sound to itself, so the lights move on their own";
        } else {
            levelsText = "The lights follow the sound once something plays";
        }
        accessRow(false, Glyph.LEVELS, "Sound levels", levelsText, levels,
                v -> requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_ALL));
    }

    /** An access line: its icon, name and what it's for; on the right a check when it's allowed, or an Allow pill. */
    private void accessRow(boolean first, int glyph, String name, String detail, boolean ok, View.OnClickListener allow) {
        LinearLayout row = listRow(accessList, first);
        ImageView g = new ImageView(this);
        g.setImageDrawable(new Glyph(glyph, ok ? Theme.TEXT_SOFT : Theme.AMBER, false, 1.6f));
        LinearLayout.LayoutParams gp = new LinearLayout.LayoutParams(dp(22), dp(22));
        gp.rightMargin = dp(14);
        row.addView(g, gp);
        LinearLayout words = vbox();
        words.addView(text(name, 16, Theme.uiMedium(this), Theme.TEXT));
        TextView d = text(detail, 12.5f, Theme.uiMedium(this), Theme.MUTED);
        d.setPadding(0, dp(2), 0, 0);
        words.addView(d);
        row.addView(words, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        if (ok) {
            ImageView check = new ImageView(this);
            check.setImageDrawable(new Glyph(Glyph.CHECK, Theme.GREEN, false, 2f));
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(dp(20), dp(20));
            cp.leftMargin = dp(10);
            row.addView(check, cp);
        } else {
            TextView b = text("ALLOW", 11.5f, Theme.uiBold(this), Segmented.ON);
            b.setLetterSpacing(0.16f);
            b.setPadding(dp(16), dp(8), dp(16), dp(8));
            b.setBackground(Theme.pressable(Theme.pill(this, 0xFF2A2F39, true), Theme.pill(this, 0xFF363C48, true)));
            b.setClickable(true);
            b.setOnClickListener(allow);
            LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            bp.leftMargin = dp(10);
            row.addView(b, bp);
        }
    }

    /** Notification access, which is how Android lets an app see other apps' media. */
    private void openMediaAccess() {
        android.content.Intent i;
        if (Build.VERSION.SDK_INT >= 30) {
            i = new android.content.Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                    .putExtra(android.provider.Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                            new ComponentName(this, MediaListener.class).flattenToString());
        } else {
            i = new android.content.Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
        }
        try {
            startActivity(i);
        } catch (Exception e) {
            startActivity(new android.content.Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
        }
    }

    /** The live data lines, and Home's big rate and tiles. */
    private void updateStatus() {
        liveList.removeAllViews();
        long t = Store.lastFetch(this);
        String last = t == 0 ? null
                : DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(new Date(t));
        statusRow(true, "Data refresh", last != null, last == null ? "Not refreshed yet" : "Last successful: " + last);

        String mktErr = Store.sp(this).getString("mktErr", "");
        statusRow(false, "Market rate", mktErr.isEmpty() && Store.marketAt(this) > 0,
                Store.marketAt(this) == 0 ? "Waiting for first update"
                        : mktErr.isEmpty() ? "Live mid-market rate from Wise" : mktErr);

        String visaErr = Store.sp(this).getString("visaErr", "");
        statusRow(false, "Visa rate", visaErr.isEmpty(), visaErr.isEmpty() ? "Live rate from Visa" : visaErr);

        String wxErr = Store.sp(this).getString("wxErr", "");
        statusRow(false, "Weather", wxErr.isEmpty() && Store.weather(this) != null,
                !wxErr.isEmpty() ? wxErr : (Store.weather(this) == null ? "Waiting for first update" : "Open-Meteo"));

        SharedPreferences sp = Store.sp(this);
        long ok = sp.getLong("clockOkAt", 0), bad = sp.getLong("clockErrAt", 0);
        java.text.SimpleDateFormat hms = new java.text.SimpleDateFormat("HH:mm:ss", Locale.getDefault());
        if (bad > ok) {
            statusRow(false, "Clock updates", false, "Failed at " + hms.format(new Date(bad)) + " ("
                    + sp.getString("clockErrWho", "") + "): " + sp.getString("clockErrMsg", ""));
        } else {
            long nx = sp.getLong("clockNextAt", 0);
            String next = nx == 0 ? "" : " · next " + hms.format(new Date(nx)) + " (" + sp.getString("clockNextWho", "") + ")";
            statusRow(false, "Clock updates", ok > 0 && System.currentTimeMillis() - ok < 3 * 60_000L,
                    (ok == 0 ? "No update yet" : "Last " + hms.format(new Date(ok)) + " (" + sp.getString("clockOkWho", "")
                            + ")") + next);
        }

        String geoErr = Store.sp(this).getString("geoErr", "");
        statusRow(false, "Cities", geoErr.isEmpty(), geoErr.isEmpty() ? "All cities found" : geoErr);

    }

    /** The clock tiles, two to a row, one for each of the widget's clocks (tap one to change its city). */
    private void fillClockTiles() {
        cityGrid.removeAllViews();
        int n = Store.clockCount(this);
        LinearLayout row = null;
        for (int i = 0; i < n; i++) {
            final int slot = i;
            if (i % 2 == 0) {
                row = hbox();
                cityGrid.addView(row, matchWrap(i == 0 ? 0 : 10));
            }
            LinearLayout t = tile(false);
            t.setGravity(Gravity.START);
            t.setPadding(dp(18), dp(18), dp(18), dp(16));
            cityName[i] = text("", 17, Theme.uiMedium(this), Theme.TEXT);
            cityName[i].setSingleLine(true);
            cityName[i].setEllipsize(android.text.TextUtils.TruncateAt.END);
            t.addView(cityName[i]);
            t.addView(new View(this), new LinearLayout.LayoutParams(1, 0, 1f));
            cityWhen[i] = text("", 12.5f, Theme.uiMedium(this), Theme.MUTED);
            t.addView(cityWhen[i]);
            cityTime[i] = chrome("", 38);
            t.addView(cityTime[i]);
            t.setClickable(true);
            t.setOnClickListener(v -> pickCity(slot));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(150), 1f);
            lp.leftMargin = i % 2 == 0 ? 0 : dp(10);
            row.addView(t, lp);
        }
        if (n % 2 == 1) row.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f)); // an odd one keeps its size
        updateClockTiles();
    }

    /** The clock tiles: each city's name, day and offset, and its time now. */
    private void updateClockTiles() {
        if (cityName[0] == null || cityGrid == null) return;
        int n = Math.min(Store.clockCount(this), cityGrid.getChildCount() * 2);
        ClockView.Clock[] ks = ClockView.clocks(this, System.currentTimeMillis(), n);
        DateTimeFormatter dayF = DateTimeFormatter.ofPattern("EEEE", Locale.getDefault());
        DateTimeFormatter timeF = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault());
        for (int i = 0; i < n; i++) {
            if (cityName[i] == null) continue;
            ZonedDateTime now = ZonedDateTime.now(ks[i].zone);
            cityName[i].setText(Util.capWords(ks[i].city));
            int off = now.getOffset().getTotalSeconds() / 60;
            String utc = "UTC" + (off < 0 ? "−" : "+") + Math.abs(off) / 60 + (Math.abs(off) % 60 == 0 ? ""
                    : String.format(Locale.US, ":%02d", Math.abs(off) % 60));
            cityWhen[i].setText(now.format(dayF) + " · " + utc);
            cityTime[i].setText(now.format(timeF));
        }
    }

    /** A rate as the widget shows it: whole above 100, with decimals below. */
    private static String rateText(double v) {
        if (Double.isNaN(v) || Double.isInfinite(v)) return "--";
        if (v >= 100) return Util.fmt0(v);
        java.text.NumberFormat f = java.text.NumberFormat.getNumberInstance(Locale.US);
        int dec = v >= 10 ? 1 : v >= 1 ? 2 : 4;
        f.setMinimumFractionDigits(dec);
        f.setMaximumFractionDigits(dec);
        return f.format(v);
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

    // ------------------------------------------------------------------ pickers

    private interface Pick<T> { void on(T value); }

    private static final class Item {
        final String title, sub;
        final Object payload;
        Item(String title, String sub, Object payload) { this.title = title; this.sub = sub; this.payload = payload; }
        String hay() { return (title + " " + sub).toLowerCase(Locale.ROOT); }
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
                name = p.title;
                geo = (JSONObject) p.payload;
            }
            Store.putGeo(this, name, geo);
            Store.sp(this).edit().putString("city" + slot, name).apply();
            updateClockTiles();
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
        root.setPadding(dp(16), dp(18), dp(16), dp(10));
        root.setBackground(Theme.card(this, 22, 0xFA010203));
        TextView t = text(title.toUpperCase(Locale.getDefault()), 16, Theme.uiBold(this), Theme.TEXT);
        t.setLetterSpacing(0.14f);
        t.setPadding(dp(4), 0, 0, dp(12));
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
                r.setPadding(dp(10), dp(10), dp(10), dp(10));
                Item it = shown.get(i);
                r.addView(text(it.title, 16, Theme.uiMedium(MainActivity.this), Theme.TEXT));
                if (!it.sub.isEmpty()) r.addView(text(it.sub, 12.5f, Theme.uiMedium(MainActivity.this), Theme.MUTED));
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

    // ------------------------------------------------------------------ UI building blocks

    private int dp(float v) { return Math.round(Theme.dp(this, v)); }

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
        TextView t = new Theme.FringeText(this); // the subtle fringe all text has
        t.setText(s);
        t.setTextSize(sp);
        t.setTypeface(tf);
        t.setTextColor(color);
        return t;
    }

    /** Dark text on something light (the floating pill): no fringe. */
    private TextView plain(String s, float sp, Typeface tf, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp * Theme.FONT_SCALE);
        t.setTypeface(tf);
        t.setTextColor(color);
        return t;
    }

    /** Big figures in polished metal ({@link Theme.Chrome}). */
    private TextView chrome(String s, float sp) {
        TextView t = new Theme.Chrome(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTypeface(Theme.uiMedium(this));
        t.setTextColor(0xFFFFFFFF);
        t.setIncludeFontPadding(false);
        return t;
    }

    /**
     * A page's head, as the car app's: the crystal globe in a glass ring, the page's name beside it in big white
     * letters, and a line under the name in grey.
     */
    private void header(LinearLayout col, String title, String sub) {
        LinearLayout row = hbox();
        row.setGravity(Gravity.CENTER_VERTICAL);
        FrameLayout badge = new FrameLayout(this);
        ImageView logo = new ImageView(this);
        logo.setImageBitmap(Theme.badge(this, dp(50)));
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        badge.addView(logo, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        badge.setForeground(Theme.ring(this));
        row.addView(badge, new LinearLayout.LayoutParams(dp(50), dp(50)));
        LinearLayout words = vbox();
        words.setPadding(dp(14), 0, 0, 0);
        TextView name = text(title.toUpperCase(Locale.getDefault()), 24, Theme.uiBold(this), Theme.TEXT);
        name.setLetterSpacing(0.08f); // titles in spaced capitals (2.23), as the widgets' labels
        words.addView(name);
        TextView s = text(sub, 13.5f, Theme.uiMedium(this), Theme.MUTED);
        s.setPadding(0, dp(1), 0, 0);
        words.addView(s);
        row.addView(words, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        LinearLayout.LayoutParams lp = matchWrap(0);
        lp.bottomMargin = dp(22);
        col.addView(row, lp);
    }

    /** A quiet heading over a group, in small spaced capitals. */
    private void section(LinearLayout col, String s) {
        TextView t = text(s.toUpperCase(Locale.getDefault()), 11.5f, Theme.uiBold(this), Theme.MUTED);
        t.setLetterSpacing(0.2f); // spaced capitals (2.23), as the widgets' labels
        t.setPadding(dp(4), dp(30), 0, dp(12));
        col.addView(t);
    }

    /** A note under a group, small and dim. */
    private void hint(LinearLayout col, String s) {
        TextView t = text(s, 12.5f, Theme.uiMedium(this), Theme.DIM);
        t.setPadding(dp(4), dp(10), dp(4), 0);
        t.setLineSpacing(dp(2), 1f);
        col.addView(t);
    }

    /** A tile, as the reference's: a glass card with its words centred; {@code lifted}, the raised one in a row. */
    private LinearLayout tile(boolean lifted) {
        LinearLayout t = vbox();
        t.setGravity(Gravity.CENTER_HORIZONTAL);
        t.setBackground(Theme.tile(this, 22, lifted));
        t.setPadding(dp(10), dp(18), dp(10), dp(16));
        return t;
    }

    /** A card holding lines (currencies, access, live data), hairlines between them. */
    private LinearLayout listCard(LinearLayout col) {
        LinearLayout c = vbox();
        c.setBackground(Theme.card(this, 22));
        c.setPadding(0, dp(4), 0, dp(4));
        col.addView(c, matchWrap(0));
        return c;
    }

    /** A line in a list card, a hairline over it unless it's the {@code first}. */
    private LinearLayout listRow(LinearLayout card, boolean first) {
        if (!first) {
            View line = new View(this);
            line.setBackgroundColor(Scene.alpha(Stage.ICE, 0.07f));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(0.7f)));
            lp.leftMargin = dp(18);
            lp.rightMargin = dp(18);
            card.addView(line, lp);
        }
        LinearLayout row = hbox();
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(18), dp(14), dp(16), dp(14));
        card.addView(row, matchWrap(0));
        return row;
    }

    private ImageView chevron() {
        ImageView c = new ImageView(this);
        c.setImageDrawable(new Glyph(Glyph.CHEVRON, Segmented.OFF, false, 1.8f));
        return c;
    }

    /** A well in the glass whose edge lights up in the key on focus. */
    private EditText input(String value, int inputType, boolean mono, String hint) {
        final EditText e = new Theme.FringeEdit(this);
        e.setInputType(inputType);
        e.setSingleLine(true);
        e.setText(value);
        e.setHint(hint);
        e.setTextSize(16);
        e.setTypeface(mono ? Theme.monoMedium(this) : Theme.uiMedium(this));
        e.setTextColor(Theme.TEXT);
        e.setHintTextColor(Theme.DIM);
        e.setHighlightColor(Scene.alpha(Theme.accent().key, 0.33f));
        e.setPadding(dp(12), dp(10), dp(12), dp(10));
        e.setBackground(Theme.well(this, 12, false));
        e.setOnFocusChangeListener((v, focused) -> {
            e.setBackground(Theme.well(this, 12, focused));
            if (focused && typing) revealFocus();
        });
        return e;
    }

    /** A live data line: a dot (green: fine, amber: needs a look), its name and what's happening. */
    private void statusRow(boolean first, String name, boolean ok, String detail) {
        LinearLayout row = listRow(liveList, first);
        View dot = new View(this);
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(ok ? Theme.GREEN : Theme.AMBER);
        dot.setBackground(g);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(8), dp(8));
        lp.rightMargin = dp(16);
        lp.leftMargin = dp(7);
        row.addView(dot, lp);
        LinearLayout txt = vbox();
        txt.addView(text(name, 16, Theme.uiMedium(this), Theme.TEXT));
        TextView d = text(detail, 12.5f, Theme.uiMedium(this), Theme.MUTED);
        d.setPadding(0, dp(2), 0, 0);
        txt.addView(d);
        row.addView(txt, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
    }
}
