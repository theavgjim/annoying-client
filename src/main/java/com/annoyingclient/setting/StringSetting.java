package com.annoyingclient.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

public class StringSetting extends Setting<String> {

    private final int maxLength;

    public StringSetting(String name, String description, String defaultValue, int maxLength) {
        super(name, description, defaultValue);
        this.maxLength = Math.max(1, maxLength);
        this.value = validate(defaultValue);
    }

    @Override
    protected String validate(String candidate) {
        StringBuilder clean = new StringBuilder(Math.min(candidate.length(), maxLength));
        for (int i = 0; i < candidate.length() && clean.length() < maxLength; i++) {
            char c = candidate.charAt(i);
            // Strip control characters and the section sign used for legacy formatting codes.
            if (c >= 32 && c != 127 && c != '§') {
                clean.append(c);
            }
        }
        return clean.toString();
    }

    public int getMaxLength() {
        return maxLength;
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
        setValue(element.getAsString());
        return true;
    }
}
