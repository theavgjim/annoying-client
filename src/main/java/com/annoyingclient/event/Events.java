package com.annoyingclient.event;

/** Reusable event instances, so posting input events allocates nothing. Only used on the render thread. */
public final class Events {

    public static final KeyInputEvent KEY = new KeyInputEvent();
    public static final MouseInputEvent MOUSE = new MouseInputEvent();
    public static final MouseScrollEvent SCROLL = new MouseScrollEvent();

    private Events() {
    }
}
