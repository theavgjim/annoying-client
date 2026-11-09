package com.annoyingclient.event;

/** Base class for events whose default game behaviour a listener can cancel. */
public abstract class Cancellable {

    private boolean cancelled;

    public boolean isCancelled() {
        return cancelled;
    }

    public void cancel() {
        cancelled = true;
    }

    protected void resetCancelled() {
        cancelled = false;
    }
}
