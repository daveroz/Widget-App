package app.currencyexchange.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.view.View;

/**
 * The room behind the app's pages. Since 2.21 it's simply black, all over: no washes of the song's light, no haze, no
 * specks, no grain (until then it was a near black lit by two slow, faint washes and the crystal logo's deep space).
 */
final class StageView extends View {
    StageView(Context context) {
        super(context);
    }

    /** Kept for the screen's lifecycle; a black room has nothing to animate. */
    void setRunning(boolean run) { }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawColor(Theme.BG);
    }
}
