package app.currencyexchange.widget;

import android.app.Application;

/** Reads the chosen look before any widget, service or screen draws. */
public class App extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        Look.load(this);
    }
}
