package com.annoyingclient.event;

/** Posted at the end of every client tick (20 per second). A single shared instance is reused. */
public final class ClientTickEvent {

    public static final ClientTickEvent INSTANCE = new ClientTickEvent();

    private ClientTickEvent() {
    }
}
