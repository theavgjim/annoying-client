package com.annoyingclient.module.impl.utility;

import com.annoyingclient.AnnoyingClient;
import com.annoyingclient.module.Category;
import com.annoyingclient.module.Module;
import com.annoyingclient.notification.NotificationType;
import com.annoyingclient.setting.KeybindSetting;
import com.annoyingclient.theme.Theme;
import com.annoyingclient.theme.ThemeManager;

/** A keybind that cycles through the theme presets. */
public class ThemeSwitcherModule extends Module {

    public ThemeSwitcherModule() {
        super("Theme Switcher", "Press the keybind to switch to the next theme", Category.UTILITY, false, KeybindSetting.NONE);
    }

    @Override
    public boolean isToggleable() {
        return false;
    }

    @Override
    protected void onActivate() {
        ThemeManager theme = AnnoyingClient.theme();
        int index = -1;
        for (int i = 0; i < ThemeManager.PRESETS.size(); i++) {
            if (ThemeManager.PRESETS.get(i).name().equals(theme.getPresetName())) {
                index = i;
            }
        }
        Theme next = ThemeManager.PRESETS.get((index + 1) % ThemeManager.PRESETS.size());
        theme.applyPreset(next);
        AnnoyingClient.notifications().push(NotificationType.INFO, "Theme changed", next.name());
    }
}
