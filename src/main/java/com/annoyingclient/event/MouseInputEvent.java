package com.annoyingclient.event;

import org.lwjgl.glfw.GLFW;

/** A raw mouse button event. {@link #isInGame()} tells whether a screen was open. The instance is reused. */
public final class MouseInputEvent {

    private int button;
    private int action;
    private boolean inGame;

    public MouseInputEvent set(int button, int action, boolean inGame) {
        this.button = button;
        this.action = action;
        this.inGame = inGame;
        return this;
    }

    public int getButton() {
        return button;
    }

    public int getAction() {
        return action;
    }

    public boolean isPress() {
        return action == GLFW.GLFW_PRESS;
    }

    public boolean isInGame() {
        return inGame;
    }
}
