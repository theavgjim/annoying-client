package com.annoyingclient.module.impl.misc;

import com.annoyingclient.module.Category;
import com.annoyingclient.module.Module;
import com.annoyingclient.setting.KeybindSetting;
import com.annoyingclient.setting.NumberSetting;

/** Caps the frame rate while the game window is not focused, saving power and heat. */
public class BackgroundFpsModule extends Module {

    private final NumberSetting limit = add(new NumberSetting("FPS Limit", "Frame rate while the window is unfocused", 15.0, 1.0, 120.0, 1.0));

    public BackgroundFpsModule() {
        super("Background FPS", "Limits FPS when Minecraft is in the background", Category.MISC, false, KeybindSetting.NONE);
    }

    /** Applied by the frame-limit mixin. */
    public int adjust(int vanillaLimit) {
        if (!isEnabled() || mc.isWindowActive()) {
            return vanillaLimit;
        }
        return Math.min(vanillaLimit, limit.getInt());
    }

    @Override
    public String getDisplayInfo() {
        return limit.getInt() + " FPS";
    }
}
