package com.annoyingclient.event;

import org.lwjgl.glfw.GLFW;

/**
 * A raw keyboard event, posted while no screen is open (and on the title screen).
 * The instance is reused, so listeners must not keep a reference to it.
 */
public final class KeyInputEvent {

    private int key;
    private int scancode;
    private int action;
    private int modifiers;

    public KeyInputEvent set(int key, int scancode, int action, int modifiers) {
        this.key = key;
        this.scancode = scancode;
        this.action = action;
        this.modifiers = modifiers;
        return this;
    }

    public int getKey() {
        return key;
    }

    public int getScancode() {
        return scancode;
    }

    public int getAction() {
        return action;
    }

    public int getModifiers() {
        return modifiers;
    }

    public boolean isPress() {
        return action == GLFW.GLFW_PRESS;
    }

    public boolean isRelease() {
        return action == GLFW.GLFW_RELEASE;
    }
}
