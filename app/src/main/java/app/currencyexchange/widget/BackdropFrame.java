package app.currencyexchange.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.os.Build;
import android.widget.FrameLayout;

/**
 * What the glass header floats over (2.18): the settings screen's room and its scrolling cards, kept in a frame of their
 * own, beside the header rather than round it, so the header can show them blurred without seeing itself. On Android 12
 * and up the frame records what it draws into a render node ({@link #content}) that the header draws again through a
 * blur ({@link GlassBar}); before that the header takes a small software picture of the frame.
 */
final class BackdropFrame extends FrameLayout {
    /** What the frame last drew (Android 12 and up, hardware drawing only); null until then. */
    android.graphics.RenderNode content;
    BackdropFrame(Context c) {
        super(c);
    }

    @Override
    public void draw(Canvas canvas) {
        if (Build.VERSION.SDK_INT >= 31 && canvas.isHardwareAccelerated()) {
            if (content == null) content = new android.graphics.RenderNode("backdrop");
            content.setPosition(0, 0, getWidth(), getHeight());
            android.graphics.RecordingCanvas rc = content.beginRecording(getWidth(), getHeight());
            try {
                super.draw(rc);
            } finally {
                content.endRecording();
            }
            canvas.drawRenderNode(content);
        } else {
            super.draw(canvas);
        }
    }
}
