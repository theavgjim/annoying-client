package com.annoyingclient.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import java.util.List;

/** One choice out of a fixed list of named modes. */
public class ModeSetting extends Setting<String> {

    private final List<String> modes;

    public ModeSetting(String name, String description, String defaultValue, String... modes) {
        super(name, description, defaultValue);
        if (modes.length == 0) {
            throw new IllegalArgumentException("ModeSetting " + name + " needs at least one mode");
        }
        this.modes = List.of(modes);
        if (!this.modes.contains(defaultValue)) {
            throw new IllegalArgumentException("Default mode " + defaultValue + " is not one of " + this.modes);
        }
    }

    @Override
    protected String validate(String candidate) {
        for (String mode : modes) {
            if (mode.equalsIgnoreCase(candidate)) {
                return mode;
            }
        }
        return defaultValue;
    }

    public List<String> getModes() {
        return modes;
    }

    public boolean is(String mode) {
        return value.equals(mode);
    }

    public int getIndex() {
        return modes.indexOf(value);
    }

    public void cycle(boolean forward) {
        int size = modes.size();
        int next = (getIndex() + (forward ? 1 : -1) + size) % size;
        setValue(modes.get(next));
    }

    @Override
    public JsonElement toJson() {
        return new JsonPrimitive(value);
    }

    @Override
    public boolean fromJson(JsonElement element) {
        if (element == null || !element.isJsonPrimitive()) {
            return false;
        }
        String s = element.getAsString();
        for (String mode : modes) {
            if (mode.equalsIgnoreCase(s)) {
                setValue(mode);
                return true;
            }
        }
        return false;
    }
}
