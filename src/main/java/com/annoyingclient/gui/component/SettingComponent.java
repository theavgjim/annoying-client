package com.annoyingclient.gui.component;

import com.annoyingclient.setting.BooleanSetting;
import com.annoyingclient.setting.ColorSetting;
import com.annoyingclient.setting.KeybindSetting;
import com.annoyingclient.setting.ModeSetting;
import com.annoyingclient.setting.NumberSetting;
import com.annoyingclient.setting.Setting;
import com.annoyingclient.setting.StringSetting;
import com.annoyingclient.util.Animation;

/** A row that edits one {@link Setting}. {@link #create} picks the right editor for a setting type. */
public abstract class SettingComponent<S extends Setting<?>> extends UIComponent {

    protected final S setting;
    protected final String label;
    protected final Animation hover = new Animation(0f, 16f);

    protected SettingComponent(S setting, String label) {
        this.setting = setting;
        this.label = label == null ? setting.getName() : label;
    }

    public S getSetting() {
        return setting;
    }

    public boolean isVisible() {
        return setting.isVisible();
    }

    @Override
    public String getTooltip(float mouseX, float mouseY) {
        return setting.getDescription().isEmpty() ? null : setting.getDescription();
    }

    protected float updateHover(float mouseX, float mouseY) {
        hover.setTarget(isHovered(mouseX, mouseY) ? 1f : 0f);
        return hover.update();
    }

    public static SettingComponent<?> create(Setting<?> setting) {
        return create(setting, null);
    }

    /** Builds the editor for {@code setting}; {@code label} overrides the displayed name (may be null). */
    public static SettingComponent<?> create(Setting<?> setting, String label) {
        if (setting instanceof BooleanSetting s) {
            return new BooleanComponent(s, label);
        }
        if (setting instanceof NumberSetting s) {
            return new NumberComponent(s, label);
        }
        if (setting instanceof ModeSetting s) {
            return new ModeComponent(s, label);
        }
        if (setting instanceof ColorSetting s) {
            return new ColorComponent(s, label);
        }
        if (setting instanceof StringSetting s) {
            return new StringComponent(s, label);
        }
        if (setting instanceof KeybindSetting s) {
            return new KeybindComponent(s, label);
        }
        throw new IllegalArgumentException("No GUI component for setting type " + setting.getClass().getName());
    }
}
