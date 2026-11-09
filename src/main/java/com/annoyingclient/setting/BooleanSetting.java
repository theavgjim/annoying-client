package com.annoyingclient.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

public class BooleanSetting extends Setting<Boolean> {

    public BooleanSetting(String name, String description, boolean defaultValue) {
        super(name, description, defaultValue);
    }

    public boolean isEnabled() {
        return value;
    }

    public void toggle() {
        setValue(!value);
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
        JsonPrimitive p = element.getAsJsonPrimitive();
        if (p.isBoolean()) {
            setValue(p.getAsBoolean());
            return true;
        }
        if (p.isString()) {
            String s = p.getAsString();
            if (s.equalsIgnoreCase("true") || s.equalsIgnoreCase("false")) {
                setValue(Boolean.parseBoolean(s));
                return true;
            }
        }
        return false;
    }
}
