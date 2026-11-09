package com.annoyingclient.module.impl.render;

import com.annoyingclient.module.Category;
import com.annoyingclient.module.Module;
import com.annoyingclient.setting.KeybindSetting;
import com.annoyingclient.setting.NumberSetting;

/** Lowers the first-person fire overlay so it covers less of the screen. */
public class LowFireModule extends Module {

    private final NumberSetting height = add(new NumberSetting("Lower By", "How far to move the fire overlay down", 0.3, 0.0, 0.6, 0.05));

    public LowFireModule() {
        super("Low Fire", "Lowers the burning overlay so you can see", Category.RENDER, false, KeybindSetting.NONE);
    }

    public float getOffset() {
        return height.getFloat();
    }
}
