package app.currencyexchange.widget;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Converter that opens over the home screen when a currency row of the widget is tapped: type an amount and it
 * shows the market and Visa values, from the same cached rates as the widget, then from the live market rate it
 * fetches as it opens. The swap button converts the other
 * way (from the target currency), the chips switch between the configured currencies. Taps outside close it.
 * Since 2.23 it's drawn as the app is: its head with the crystal globe, the currencies as a segmented control, the
 * swap as an icon disc, the results as lines of a list card with their figures in polished silver.
 */
public class ConverterActivity extends Activity {
    static final String EXTRA_ROW = "row";

    /** Opens the converter for widget row {@code row}. */
    static PendingIntent intentFor(Context ctx, int row) {
        Intent i = new Intent(ctx, ConverterActivity.class).putExtra(EXTRA_ROW, row)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        return PendingIntent.getActivity(ctx, 100 + row, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private int row;
    private boolean reverse; // false: from-currency -> target, true: target -> from-currency
    private Segmented chips;
    private LinearLayout amountBox;
    private ImageView inFlag;
    private TextView inCode, mktVal, mktCode, visaVal, visaCode, rateLine, note;
    private EditText amount;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        row = Math.max(0, Math.min(Store.count(this) - 1,
                getIntent().getIntExtra(EXTRA_ROW, Store.sp(this).getInt("convRow", 0))));
        reverse = Store.sp(this).getBoolean("convRev", false);
        setContentView(build());
        int w = Math.min(Math.round(getResources().getDisplayMetrics().widthPixels * 0.92f), dp(420));
        getWindow().setLayout(w, ViewGroup.LayoutParams.WRAP_CONTENT);
        refreshPair();
        amount.requestFocus();
        amount.selectAll();
    }

    /** Each time it opens: the live market rates (at most once a minute), so a conversion is of the rate right now. */
    @Override
    protected void onResume() {
        super.onResume();
        final Context app = getApplicationContext();
        new Thread(() -> {
            if (!Fetcher.refreshMarket(app, false)) return;
            runOnUiThread(() -> {
                if (!isFinishing() && !isDestroyed()) refreshValues();
            });
            WidgetProvider.updateAll(app); // the widget shows the same, newer rate
        }, "converter-rates").start();
    }

    @Override
    protected void onPause() {
        super.onPause();
        Store.sp(this).edit().putString("convAmt", amount.getText().toString())
                .putInt("convRow", row).putBoolean("convRev", reverse).apply();
    }

    // ------------------------------------------------------------------ layout

    private View build() {
        LinearLayout card = vbox();
        card.setBackground(room());
        card.setPadding(dp(18), dp(18), dp(18), dp(18));

        // its head, as the app's pages (2.23): the crystal globe in its glass ring, CONVERTER, and the rate under it
        LinearLayout head = hbox();
        head.setGravity(Gravity.CENTER_VERTICAL);
        FrameLayout badge = new FrameLayout(this);
        ImageView logo = new ImageView(this);
        logo.setImageBitmap(Theme.badge(this, dp(40)));
        badge.addView(logo, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        badge.setForeground(Theme.ring(this));
        head.addView(badge, new LinearLayout.LayoutParams(dp(40), dp(40)));
        LinearLayout words = vbox();
        words.setPadding(dp(12), 0, 0, 0);
        TextView title = text("CONVERTER", 18, Theme.uiBold(this), Theme.TEXT);
        title.setLetterSpacing(0.08f);
        words.addView(title);
        rateLine = text("", 12, Theme.uiMedium(this), Theme.MUTED);
        rateLine.setPadding(0, dp(1), 0, 0);
        words.addView(rateLine);
        head.addView(words, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        card.addView(head);

        // the configured currencies, as the app's segmented control: a flag and a code each, the chosen one raised
        int n = Store.count(this);
        if (n >= 2) {
            String[] codes = new String[n];
            android.graphics.Bitmap[] flags = n <= 4 ? new android.graphics.Bitmap[n] : null; // flags while they fit
            for (int i = 0; i < n; i++) {
                codes[i] = from(i);
                if (flags != null) flags[i] = CurrencyView.flagOrLabel(this, Util.countryOfCurrency(codes[i]), codes[i]);
            }
            chips = new Segmented(this, codes, flags, row, i -> {
                row = i;
                refreshPair();
            });
            card.addView(chips, matchWrap(18));
        }

        // amount: flag + code of the currency typed in, then the number, in a well lit in the light while typing
        amountBox = hbox();
        amountBox.setGravity(Gravity.CENTER_VERTICAL);
        amountBox.setPadding(dp(16), dp(10), dp(16), dp(10));
        amountBox.setBackground(Theme.well(this, 16, true));
        inFlag = new ImageView(this);
        amountBox.addView(inFlag, new LinearLayout.LayoutParams(dp(30), dp(30)));
        inCode = text("", 15, Theme.uiBold(this), Theme.TEXT);
        inCode.setLetterSpacing(0.08f);
        inCode.setPadding(dp(10), 0, dp(8), 0);
        amountBox.addView(inCode);
        amount = new Theme.FringeEdit(this);
        amount.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        amount.setKeyListener(android.text.method.DigitsKeyListener.getInstance("0123456789.,"));
        amount.setImeOptions(EditorInfo.IME_ACTION_DONE);
        amount.setSingleLine(true);
        amount.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        amount.setTextSize(30);
        amount.setTypeface(Theme.uiMedium(this));
        amount.setTextColor(Theme.TEXT);
        amount.setHint("0");
        amount.setHintTextColor(Theme.DIM);
        amount.setHighlightColor(Scene.alpha(Theme.accent().key, 0.33f));
        amount.setBackground(null);
        amount.setPadding(0, dp(4), 0, dp(4));
        amount.setText(Store.sp(this).getString("convAmt", "100"));
        amount.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int c, int d) { }
            @Override public void onTextChanged(CharSequence s, int a, int c, int d) { }
            @Override public void afterTextChanged(Editable s) { refreshValues(); }
        });
        amount.setOnEditorActionListener((v, id, ev) -> {
            if (id == EditorInfo.IME_ACTION_DONE) hideKeyboard();
            return false;
        });
        amountBox.addView(amount, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        card.addView(amountBox, matchWrap(14));

        // swap direction: the app's icon disc, its arrows in white
        FrameLayout swap = new FrameLayout(this);
        swap.setBackground(Theme.disc(this));
        ImageView arrows = new ImageView(this);
        arrows.setImageDrawable(new Glyph(Glyph.SWAP, Theme.TEXT, false, 1.7f));
        swap.addView(arrows, new FrameLayout.LayoutParams(dp(20), dp(20), Gravity.CENTER));
        swap.setContentDescription("Swap direction");
        swap.setClickable(true);
        swap.setOnClickListener(v -> {
            reverse = !reverse;
            refreshPair();
        });
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(dp(40), dp(40));
        sl.gravity = Gravity.CENTER_HORIZONTAL;
        sl.topMargin = dp(10);
        sl.bottomMargin = dp(10);
        card.addView(swap, sl);

        // results: market and Visa, as lines of the app's list cards, their figures in polished silver
        LinearLayout res = vbox();
        GradientDrawable well = new GradientDrawable();
        well.setColor(0xFF10131A);
        well.setCornerRadius(dp(16));
        well.setStroke(Math.max(1, dp(1)), Scene.alpha(Stage.ICE, 0.05f));
        res.setBackground(well);
        res.setPadding(dp(16), dp(4), dp(16), dp(4));
        TextView mktLbl = text("MARKET", 11.5f, Theme.uiBold(this), Theme.MUTED);
        mktLbl.setLetterSpacing(0.2f);
        mktVal = chrome(26);
        mktCode = text("", 13, Theme.uiBold(this), Theme.MUTED);
        res.addView(resultRow(mktLbl, mktVal, mktCode));
        View div = new View(this);
        div.setBackgroundColor(Scene.alpha(Stage.ICE, 0.07f));
        res.addView(div, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(1))));
        ImageView visa = new ImageView(this);
        visa.setImageBitmap(CurrencyView.visaLogo(this));
        visa.setAdjustViewBounds(true);
        visa.setScaleType(ImageView.ScaleType.FIT_START);
        visa.setLayoutParams(new LinearLayout.LayoutParams(dp(36), dp(20)));
        visaVal = chrome(26);
        visaCode = text("", 13, Theme.uiBold(this), Theme.MUTED);
        res.addView(resultRow(visa, visaVal, visaCode));
        card.addView(res, matchWrap(0));

        note = text("", 12, Theme.uiMedium(this), Theme.DIM);
        note.setPadding(dp(4), dp(10), dp(4), 0);
        note.setVisibility(View.GONE);
        card.addView(note);
        return card;
    }

    /**
     * The card floats over the home screen, so it brings its own light: the widgets' black panel, nearly opaque so the
     * numbers read over any wallpaper: the app's card (see {@link Stage}), with the app's corners (2.23).
     */
    private Drawable room() {
        return Theme.card(this, 22, 0xF7010203);
    }

    private LinearLayout resultRow(View label, TextView value, TextView code) {
        LinearLayout r = hbox();
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(0, dp(12), 0, dp(12));
        r.addView(label);
        View gap = new View(this);
        r.addView(gap, new LinearLayout.LayoutParams(0, 1, 1f));
        r.addView(value);
        code.setPadding(dp(8), dp(4), 0, 0);
        code.setMinWidth(dp(38));
        code.setLetterSpacing(0.08f);
        r.addView(code);
        return r;
    }

    /** Big figures in polished metal, as the app's ({@link Theme.Chrome}). */
    private TextView chrome(float sp) {
        TextView t = new Theme.Chrome(this);
        t.setTextSize(sp);
        t.setTypeface(Theme.uiMedium(this));
        t.setTextColor(0xFFFFFFFF);
        t.setIncludeFontPadding(false);
        t.setMaxLines(1);
        return t;
    }

    // ------------------------------------------------------------------ values

    private String from(int i) { return Store.from(this, i).trim().toUpperCase(Locale.ROOT); }
    private String to() { return Store.to(this).trim().toUpperCase(Locale.ROOT); }

    /** New currency pair or direction: chips, flag and codes, then the numbers. */
    private void refreshPair() {
        if (chips != null && chips.selected() != row) chips.select(row, false);
        String in = reverse ? to() : from(row), out = reverse ? from(row) : to();
        inFlag.setImageBitmap(CurrencyView.flagOrLabel(this, Util.countryOfCurrency(in), in));
        inCode.setText(in);
        mktCode.setText(out);
        visaCode.setText(out);
        refreshValues();
    }

    private void refreshValues() {
        String from = from(row), to = to();
        double market = Store.rate(this, from, to);
        double visa = Util.alignToMarket(Store.visa(this, from, to), market);
        boolean estimate = Double.isNaN(visa) && !Double.isNaN(market);
        if (estimate) visa = market * (1.0 + Store.fee(this) / 100.0); // Visa did not answer: market + fee, as on the widget
        double amt = Util.parseAmount(amount.getText().toString());
        mktVal.setText(Util.fmtMoney(convert(amt, market)));
        visaVal.setText((estimate ? "~" : "") + Util.fmtMoney(convert(amt, visa)));
        note.setText("Visa didn't answer: estimated as the market rate plus your bank's fee");
        note.setVisibility(estimate ? View.VISIBLE : View.GONE);
        if (Double.isNaN(market)) {
            rateLine.setText("No rate yet · open Worldly to refresh");
        } else {
            long at = Store.marketAt(this) > 0 ? Store.marketAt(this) : Store.lastFetch(this);
            String when = at <= 0 ? "" : "  ·  " + Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault())
                    .format(DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault()));
            rateLine.setText("1 " + from + " = " + Util.fmtMoney(market) + " " + to + when);
        }
    }

    private double convert(double amt, double rate) {
        if (Double.isNaN(amt) || Double.isNaN(rate) || rate <= 0) return Double.NaN;
        return reverse ? amt / rate : amt * rate;
    }

    private void hideKeyboard() {
        InputMethodManager im = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (im != null) im.hideSoftInputFromWindow(amount.getWindowToken(), 0);
    }

    // ------------------------------------------------------------------ small builders (as in MainActivity)

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
        TextView t = new Theme.FringeText(this); // the subtle fringe all text has
        t.setText(s);
        t.setTextSize(sp);
        t.setTypeface(tf);
        t.setTextColor(color);
        return t;
    }
}
