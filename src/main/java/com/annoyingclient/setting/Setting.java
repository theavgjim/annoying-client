package com.annoyingclient.setting;

import com.google.gson.JsonElement;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * A named, typed, validated and serializable value owned by a module or a client system.
 *
 * <p>Every write goes through {@link #validate(Object)}, so a setting can never hold a value outside
 * its allowed range, even if a configuration file was edited by hand. Deserialization never throws:
 * a malformed value is logged by the caller and the setting keeps its current value.
 *
 * @param <T> the value type
 */
public abstract class Setting<T> {

    /** Called after any setting in the client changes; the configuration system uses it to schedule an auto-save. */
    private static Runnable globalChangeHook = () -> { };

    private final String name;
    private final String description;
    protected final T defaultValue;
    protected T value;
    private boolean hidden;
    private BooleanSupplier visibility = () -> true;
    private final List<Consumer<T>> listeners = new ArrayList<>(1);

    protected Setting(String name, String description, T defaultValue) {
        this.name = Objects.requireNonNull(name, "name");
        this.description = description == null ? "" : description;
        this.defaultValue = Objects.requireNonNull(defaultValue, "defaultValue");
        this.value = defaultValue;
    }

    public static void setGlobalChangeHook(Runnable hook) {
        globalChangeHook = hook == null ? () -> { } : hook;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public T getDefaultValue() {
        return defaultValue;
    }

    public T getValue() {
        return value;
    }

    /** Sets the value after validation. Listeners only fire when the stored value actually changes. */
    public void setValue(T newValue) {
        T validated = newValue == null ? defaultValue : validate(newValue);
        if (Objects.equals(validated, value)) {
            return;
        }
        value = validated;
        notifyChanged();
    }

    /** Fires listeners and the auto-save hook; for subclasses whose state lives outside {@link #value}. */
    protected void notifyChanged() {
        for (int i = 0; i < listeners.size(); i++) {
            listeners.get(i).accept(value);
        }
        globalChangeHook.run();
    }

    public void reset() {
        setValue(defaultValue);
    }

    /** Returns a legal value derived from {@code candidate}; never null. */
    protected T validate(T candidate) {
        return candidate;
    }

    /** Hidden settings are saved and loaded but never shown in the ClickGUI (for example HUD positions). */
    public boolean isHidden() {
        return hidden;
    }

    @SuppressWarnings("unchecked")
    public <S extends Setting<T>> S hide() {
        this.hidden = true;
        return (S) this;
    }

    /** Shows this setting in the GUI only while {@code condition} is true (for example a color that only applies in one mode). */
    @SuppressWarnings("unchecked")
    public <S extends Setting<T>> S visibleWhen(BooleanSupplier condition) {
        this.visibility = condition == null ? () -> true : condition;
        return (S) this;
    }

    public boolean isVisible() {
        return !hidden && visibility.getAsBoolean();
    }

    @SuppressWarnings("unchecked")
    public <S extends Setting<T>> S onChange(Consumer<T> listener) {
        listeners.add(listener);
        return (S) this;
    }

    public abstract JsonElement toJson();

    /**
     * Reads a value written by {@link #toJson()}.
     *
     * @return false when the element could not be understood; the current value is then kept
     */
    public abstract boolean fromJson(JsonElement element);
}
