package com.annoyingclient.notification;

import com.annoyingclient.util.Animation;
import com.annoyingclient.util.Easing;

public final class Notification {

    private final NotificationType type;
    private final String title;
    private final String message;
    private final long durationMs;
    private long shownAt = -1;
    /** Width needed for the text; measured on first draw (the font is not available before). */
    float width = -1;
    private boolean closing;

    /** 0 = off-screen, 1 = fully visible. */
    final Animation slide = Animation.timed(0f, 320, Easing.EASE_OUT_BACK);
    /** Vertical stacking position, smoothly re-flowed when notifications above disappear. */
    final Animation offset = new Animation(-1f, 14f);

    public Notification(NotificationType type, String title, String message, long durationMs) {
        this.type = type;
        this.title = title == null ? "" : title;
        this.message = message == null ? "" : message;
        this.durationMs = Math.max(500, durationMs);
    }

    public NotificationType getType() {
        return type;
    }

    public String getTitle() {
        return title;
    }

    public String getMessage() {
        return message;
    }

    void start(long now) {
        if (shownAt < 0) {
            shownAt = now;
            slide.setTarget(1f);
        }
    }

    /** 0..1 of the display time used up; drives the progress bar. */
    float progress(long now) {
        return shownAt < 0 ? 0 : Math.min(1f, (now - shownAt) / (float) durationMs);
    }

    boolean shouldClose(long now) {
        return shownAt >= 0 && now - shownAt >= durationMs;
    }

    void close() {
        if (!closing) {
            closing = true;
            slide.setTarget(0f);
        }
    }

    boolean isClosing() {
        return closing;
    }

    boolean isFinished() {
        return closing && slide.isDone();
    }
}
