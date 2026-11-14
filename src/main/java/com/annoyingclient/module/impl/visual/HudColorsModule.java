package com.annoyingclient.module.impl.visual;

import com.annoyingclient.AnnoyingClient;
import com.annoyingclient.module.Category;
import com.annoyingclient.module.Module;
import com.annoyingclient.setting.BooleanSetting;
import com.annoyingclient.setting.ColorSetting;
import com.annoyingclient.setting.KeybindSetting;
import com.annoyingclient.setting.ModeSetting;
import com.annoyingclient.setting.NumberSetting;
import com.annoyingclient.util.ColorUtil;

/** Global color style for HUD elements. When disabled, HUD elements use the theme accent. */
public class HudColorsModule extends Module {

    private final ModeSetting mode = add(new ModeSetting("Mode", "How HUD accents are colored", "Wave", "Theme", "Static", "Wave", "Rainbow"));
    private final ColorSetting staticColor = add(new ColorSetting("Static Color", "Color used in Static mode", 0xFF8B5CF6, false));
    private final NumberSetting speed = add(new NumberSetting("Speed", "Speed of the Wave and Rainbow modes", 1.0, 0.1, 4.0, 0.1).withSuffix("x"));
    private final NumberSetting bgOpacity = add(new NumberSetting("Background Opacity", "Opacity of HUD element panels", 0.55, 0.0, 1.0, 0.05));
    private final BooleanSetting rounded = add(new BooleanSetting("Rounded Corners", "Round HUD element panels", true));

    public HudColorsModule() {
        super("HUD Colors", "Customize the colors of every HUD element", Category.VISUAL, true, KeybindSetting.NONE);
        staticColor.visibleWhen(() -> mode.is("Static"));
        speed.visibleWhen(() -> mode.is("Wave") || mode.is("Rainbow"));
    }

    /** The HUD accent color, with a phase {@code offset} so lists can form a moving gradient. */
    public int color(long offset) {
        if (!isEnabled()) {
            return AnnoyingClient.theme().accent(offset);
        }
        return switch (mode.getValue()) {
            case "Static" -> staticColor.getColor(offset);
            case "Rainbow" -> ColorUtil.rainbow(offset, speed.getFloat(), 0.6f, 1f);
            case "Wave" -> ColorUtil.wave(AnnoyingClient.theme().accent(), AnnoyingClient.theme().accent2(), offset, speed.getFloat());
            default -> AnnoyingClient.theme().accent(offset);
        };
    }

    public float backgroundOpacity() {
        return isEnabled() ? bgOpacity.getFloat() : 0.55f;
    }

    public boolean roundedCorners() {
        return !isEnabled() || rounded.isEnabled();
    }

    @Override
    public String getDisplayInfo() {
        return mode.getValue();
    }

}
