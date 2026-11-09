package com.annoyingclient.setting;

import com.annoyingclient.util.ColorUtil;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

/**
 * An ARGB color with an optional rainbow (hue-cycling) mode.
 * Stored in configuration as {@code {"color": "#AARRGGBB", "rainbow": false}}.
 */
public class ColorSetting extends Setting<Integer> {

    private final boolean allowAlpha;
    private boolean rainbow;

    public ColorSetting(String name, String description, int defaultArgb, boolean allowAlpha) {
        super(name, description, defaultArgb);
        this.allowAlpha = allowAlpha;
        this.value = validate(defaultArgb);
    }

    @Override
    protected Integer validate(Integer candidate) {
        return allowAlpha ? candidate : (candidate | 0xFF000000);
    }

    public boolean allowsAlpha() {
        return allowAlpha;
    }

    public boolean isRainbow() {
        return rainbow;
    }

    public void setRainbow(boolean rainbow) {
        if (this.rainbow != rainbow) {
            this.rainbow = rainbow;
            notifyChanged();
        }
    }

    /** The color to draw with right now: the stored color, or a hue-cycled color with the stored alpha. */
    public int getColor() {
        return rainbow ? ColorUtil.withAlpha(ColorUtil.rainbow(0, 1f, 0.75f, 1f), ColorUtil.alpha(value)) : value;
    }

    /** Like {@link #getColor()} but with a hue offset, so several items can form a moving rainbow. */
    public int getColor(long offsetMillis) {
        return rainbow ? ColorUtil.withAlpha(ColorUtil.rainbow(offsetMillis, 1f, 0.75f, 1f), ColorUtil.alpha(value)) : value;
    }

    @Override
    public void reset() {
        rainbow = false;
        super.reset();
    }

    @Override
    public JsonElement toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("color", ColorUtil.toHex(value, true));
        o.addProperty("rainbow", rainbow);
        return o;
    }

    @Override
    public boolean fromJson(JsonElement element) {
        if (element == null) {
            return false;
        }
        try {
            if (element.isJsonObject()) {
                JsonObject o = element.getAsJsonObject();
                Integer parsed = o.has("color") ? parse(o.get("color")) : null;
                if (parsed == null) {
                    return false;
                }
                rainbow = o.has("rainbow") && o.get("rainbow").isJsonPrimitive() && o.get("rainbow").getAsBoolean();
                setValue(parsed);
                return true;
            }
            Integer parsed = parse(element);
            if (parsed != null) {
                setValue(parsed);
                return true;
            }
        } catch (RuntimeException ignored) {
            // fall through: malformed color
        }
        return false;
    }

    private static Integer parse(JsonElement element) {
        if (!element.isJsonPrimitive()) {
            return null;
        }
        JsonPrimitive p = element.getAsJsonPrimitive();
        if (p.isNumber()) {
            return (int) p.getAsLong();
        }
        return ColorUtil.parseHex(p.getAsString());
    }
}
