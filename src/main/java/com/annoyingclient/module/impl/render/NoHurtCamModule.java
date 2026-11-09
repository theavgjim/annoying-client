package com.annoyingclient.module.impl.render;

import com.annoyingclient.module.Category;
import com.annoyingclient.module.Module;
import com.annoyingclient.setting.KeybindSetting;

/** Removes the camera shake when taking damage (the death animation is kept). */
public class NoHurtCamModule extends Module {

    public NoHurtCamModule() {
        super("No Hurt Cam", "Removes the camera shake when you take damage", Category.RENDER, false, KeybindSetting.NONE);
    }
}
