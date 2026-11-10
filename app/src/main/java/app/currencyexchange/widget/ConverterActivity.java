package app.currencyexchange.widget;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
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
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Converter that opens over the home screen when a currency row of the widget is tapped: type an amount and it
 * shows the market and Visa values, from the same cached rates as the widget. The swap button converts the other
 * way (from the target currency), the chips switch between the configured currencies. Taps outside close it.
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
    private LinearLayout chips, amountBox;
    private ImageView inFlag;
    private TextView inCode, mktVal, mktCode, visaVal, visaCode, rateLine;
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

    @Override
    protected void onPause() {
        super.onPause();
        Store.sp(this).edit().putString("convAmt", amount.getText().toString())
                .putInt("convRow", row).putBoolean("convRev", reverse).apply();
    }

    // ------------------------------------------------------------------ layout

    private View build() {
        LinearLayout card = vbox();
        card.setBackground(Theme.box(this, Theme.PANEL, Theme.BORDER_SOFT, 16));
        card.setPadding(dp(16), dp(14), dp(16), dp(14));

        TextView title = text("CONVERTER", 11, Theme.uiBold(this), Theme.MUTED);
        title.setLetterSpacing(0.1f);
        title.setPadding(dp(2), 0, 0, dp(10));
        card.addView(title);

        // one chip per configured currency row
        chips = hbox();
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        hs.addView(chips);
        card.addView(hs);

        // amount: flag + code of the currency typed in, then the number
        amountBox = hbox();
        amountBox.setGravity(Gravity.CENTER_VERTICAL);
        amountBox.setPadding(dp(12), dp(6), dp(12), dp(6));
        amountBox.setBackground(Theme.box(this, Theme.SURFACE, Theme.AMBER, 12));
        inFlag = new ImageView(this);
        amountBox.addView(inFlag, new LinearLayout.LayoutParams(dp(28), dp(28)));
        inCode = text("", 17, Theme.uiMedium(this), Theme.TEXT);
        inCode.setPadding(dp(8), 0, dp(8), 0);
        amountBox.addView(inCode);
        amount = new EditText(this);
        amount.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        amount.setKeyListener(android.text.method.DigitsKeyListener.getInstance("0123456789.,"));
        amount.setImeOptions(EditorInfo.IME_ACTION_DONE);
        amount.setSingleLine(true);
        amount.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        amount.setTextSize(26);
        amount.setTypeface(Theme.monoMedium(this));
        amount.setTextColor(Theme.TEXT);
        amount.setHint("0");
        amount.setHintTextColor(Theme.DIM);
        amount.setHighlightColor(0x55DF8B35);
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
        card.addView(amountBox, matchWrap(12));

        // swap direction
        ImageView swap = new ImageView(this);
        swap.setImageResource(R.drawable.ic_swap);
        swap.setPadding(dp(8), dp(8), dp(8), dp(8));
        swap.setBackground(Theme.pressable(Theme.box(this, Theme.SURFACE, Theme.BORDER, 18),
                Theme.box(this, Theme.SURFACE_HI, Theme.AMBER, 18)));
        swap.setContentDescription("Swap direction");
        swap.setClickable(true);
        swap.setOnClickListener(v -> {
            reverse = !reverse;
            refreshPair();
        });
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(dp(36), dp(36));
        sl.gravity = Gravity.CENTER_HORIZONTAL;
        sl.topMargin = dp(8);
        sl.bottomMargin = dp(8);
        card.addView(swap, sl);

        // results: market and Visa, like the widget row
        LinearLayout res = vbox();
        res.setBackground(Theme.box(this, Theme.SURFACE, Theme.BORDER, 12));
        res.setPadding(dp(14), dp(10), dp(14), dp(10));
        TextView mktLbl = text("Market", 14, Theme.uiMedium(this), Theme.MUTED);
        mktVal = text("", 22, Theme.monoMedium(this), Theme.TEXT);
        mktCode = text("", 14, Theme.uiMedium(this), Theme.MUTED);
        res.addView(resultRow(mktLbl, mktVal, mktCode));
        View div = new View(this);
        div.setBackgroundColor(Theme.BORDER_SOFT);
        LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(1)));
        dl.topMargin = dp(8);
        dl.bottomMargin = dp(8);
        res.addView(div, dl);
        ImageView visa = new ImageView(this);
        visa.setImageBitmap(CurrencyView.visaLogo(this));
        visa.setAdjustViewBounds(true);
        visa.setScaleType(ImageView.ScaleType.FIT_START);
        visa.setLayoutParams(new LinearLayout.LayoutParams(dp(36), dp(24)));
        visaVal = text("", 22, Theme.monoMedium(this), Theme.TEXT_SOFT);
        visaCode = text("", 14, Theme.uiMedium(this), Theme.MUTED);
        res.addView(resultRow(visa, visaVal, visaCode));
        card.addView(res, matchWrap(0));

        rateLine = text("", 11, Theme.uiMedium(this), Theme.DIM);
        rateLine.setPadding(dp(2), dp(10), dp(2), 0);
        card.addView(rateLine);
        return card;
    }

    private LinearLayout resultRow(View label, TextView value, TextView code) {
        LinearLayout r = hbox();
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.addView(label);
        View gap = new View(this);
        r.addView(gap, new LinearLayout.LayoutParams(0, 1, 1f));
        r.addView(value);
        code.setPadding(dp(6), dp(4), 0, 0);
        code.setMinWidth(dp(34));
        r.addView(code);
        return r;
    }

    private void buildChips() {
        chips.removeAllViews();
        int n = Store.count(this);
        if (n < 2) return;
        for (int i = 0; i < n; i++) {
            final int idx = i;
            String code = from(i);
            LinearLayout c = hbox();
            c.setGravity(Gravity.CENTER_VERTICAL);
            c.setPadding(dp(8), dp(6), dp(12), dp(6));
            boolean on = i == row;
            c.setBackground(Theme.box(this, on ? Theme.SURFACE_HI : Theme.SURFACE, on ? Theme.AMBER : Theme.BORDER, 16));
            ImageView f = new ImageView(this);
            f.setImageBitmap(CurrencyView.flagOrLabel(this, Util.countryOfCurrency(code), code));
            c.addView(f, new LinearLayout.LayoutParams(dp(18), dp(18)));
            TextView t = text(code, 13, Theme.uiMedium(this), on ? Theme.TEXT : Theme.TEXT_SOFT);
            t.setPadding(dp(6), 0, 0, 0);
            c.addView(t);
            c.setClickable(true);
            c.setOnClickListener(v -> {
                row = idx;
                refreshPair();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = dp(6);
            lp.bottomMargin = dp(2);
            chips.addView(c, lp);
        }
    }

    // ------------------------------------------------------------------ values

    private String from(int i) { return Store.from(this, i).trim().toUpperCase(Locale.ROOT); }
    private String to() { return Store.to(this).trim().toUpperCase(Locale.ROOT); }

    /** New currency pair or direction: chips, flag and codes, then the numbers. */
    private void refreshPair() {
        buildChips();
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
        visaVal.setTextColor(estimate ? Theme.MUTED : Theme.TEXT_SOFT);
        if (Double.isNaN(market)) {
            rateLine.setText("No rate yet - open Worldly to refresh");
        } else {
            long at = Store.lastFetch(this);
            String when = at <= 0 ? "" : "  ·  updated " + Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault())
                    .format(DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault()));
            rateLine.setText("1 " + from + " = " + Util.fmtMoney(market) + " " + to + when
                    + (estimate ? "  ·  Visa estimated (market + fee)" : ""));
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
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTypeface(tf);
        t.setTextColor(color);
        return t;
    }
}
