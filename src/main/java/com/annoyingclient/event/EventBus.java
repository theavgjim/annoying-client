package com.annoyingclient.event;

import com.annoyingclient.AnnoyingClient;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * A small typed publish/subscribe bus.
 *
 * <p>Events are posted from Fabric API callbacks and from Annoying Client mixins; client systems
 * subscribe by event class. A listener that throws is logged and skipped, so one broken module
 * cannot break input handling or rendering for the whole client.
 */
public final class EventBus {

    private final Map<Class<?>, List<Consumer<Object>>> listeners = new ConcurrentHashMap<>();

    @SuppressWarnings("unchecked")
    public <E> void subscribe(Class<E> type, Consumer<? super E> listener) {
        listeners.computeIfAbsent(type, t -> new CopyOnWriteArrayList<>()).add((Consumer<Object>) listener);
    }

    public <E> void unsubscribe(Class<E> type, Consumer<? super E> listener) {
        List<Consumer<Object>> list = listeners.get(type);
        if (list != null) {
            list.remove(listener);
        }
    }

    /** Delivers {@code event} to every listener of its exact class and returns it (handy for cancellable events). */
    public <E> E post(E event) {
        List<Consumer<Object>> list = listeners.get(event.getClass());
        if (list == null) {
            return event;
        }
        for (Consumer<Object> listener : list) {
            try {
                listener.accept(event);
            } catch (RuntimeException e) {
                AnnoyingClient.LOGGER.error("Listener for {} failed", event.getClass().getSimpleName(), e);
            }
        }
        return event;
    }
}
