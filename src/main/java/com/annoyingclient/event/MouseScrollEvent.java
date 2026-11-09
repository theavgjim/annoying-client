package com.annoyingclient.event;

/** Mouse wheel movement while no screen is open. Cancel it to stop the hotbar from scrolling. The instance is reused. */
public final class MouseScrollEvent extends Cancellable {

    private double horizontal;
    private double vertical;

    public MouseScrollEvent set(double horizontal, double vertical) {
        this.horizontal = horizontal;
        this.vertical = vertical;
        resetCancelled();
        return this;
    }

    public double getHorizontal() {
        return horizontal;
    }

    public double getVertical() {
        return vertical;
    }
}
