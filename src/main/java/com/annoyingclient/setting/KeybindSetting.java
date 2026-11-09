package com.annoyingclient.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;

/**
 * A key or mouse button binding.
 *
 * <p>Keyboard keys are stored as their GLFW key code. Mouse buttons are stored as
 * {@code MOUSE_OFFSET - button} (always below -1), so one integer can hold either. {@link #NONE} means unbound.
 */
public class KeybindSetting extends Setting<Integer> {

    public static final int NONE = GLFW.GLFW_KEY_UNKNOWN;
    public static final int MOUSE_OFFSET = -100;

    public KeybindSetting(String name, String description, int defaultKey) {
        super(name, description, defaultKey);
    }

    public static int mouseCode(int button) {
        return MOUSE_OFFSET - button;
    }

    public static boolean isMouse(int code) {
        return code <= MOUSE_OFFSET;
    }

    public static int mouseButton(int code) {
        return MOUSE_OFFSET - code;
    }

    @Override
    protected Integer validate(Integer candidate) {
        int c = candidate;
        if (c == NONE) {
            return NONE;
        }
        if (isMouse(c)) {
            int button = mouseButton(c);
            return button >= 0 && button <= GLFW.GLFW_MOUSE_BUTTON_LAST ? c : NONE;
        }
        return c >= 0 && c <= GLFW.GLFW_KEY_LAST ? c : NONE;
    }

    public int getKey() {
        return value;
    }

    public boolean isBound() {
        return value != NONE;
    }

    public boolean matchesKey(int key) {
        return value != NONE && value == key;
    }

    public boolean matchesMouse(int button) {
        return value != NONE && isMouse(value) && mouseButton(value) == button;
    }

    /** Human-readable name such as "Right Shift" or "Mouse 4", using Minecraft's own key names. */
    public String getDisplayName() {
        return displayName(value);
    }

    public static String displayName(int code) {
        if (code == NONE) {
            return "None";
        }
        if (isMouse(code)) {
            return "Mouse " + (mouseButton(code) + 1);
        }
        return InputConstants.Type.KEYSYM.getOrCreate(code).getDisplayName().getString();
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
        try {
            setValue(element.getAsInt());
            return true;
        } catch (NumberFormatException | UnsupportedOperationException e) {
            return false;
        }
    }
}
