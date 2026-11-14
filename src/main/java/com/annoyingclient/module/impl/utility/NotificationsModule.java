package com.annoyingclient.module.impl.utility;

import com.annoyingclient.AnnoyingClient;
import com.annoyingclient.module.Category;
import com.annoyingclient.module.Module;
import com.annoyingclient.setting.BooleanSetting;
import com.annoyingclient.setting.KeybindSetting;
import com.annoyingclient.setting.ModeSetting;
import com.annoyingclient.setting.NumberSetting;

/** Controls the notification system. Error notifications are always shown, even when this is disabled. */
public class NotificationsModule extends Module {

    private final BooleanSetting moduleToggles = add(new BooleanSetting("Module Toggles", "Notify when a module is enabled or disabled", true));
    private final NumberSetting duration = add(new NumberSetting("Duration", "How long each notification stays", 2.5, 1.0, 8.0, 0.5).withSuffix("s"));
    private final ModeSetting position = add(new ModeSetting("Position", "Screen corner", "Bottom Right", "Bottom Right", "Top Right"));

    public NotificationsModule() {
        super("Notifications", "Animated notifications for modules, profiles and screenshots", Category.UTILITY, true, KeybindSetting.NONE);
        moduleToggles.onChange(v -> apply());
        duration.onChange(v -> apply());
        position.onChange(v -> apply());
    }

    public void apply() {
        AnnoyingClient.notifications().configure(isEnabled(), moduleToggles.isEnabled(),
                Math.round(duration.get() * 1000), position.is("Bottom Right"));
    }

    @Override
    protected void onEnable() {
        apply();
    }

    @Override
    protected void onDisable() {
        apply();
    }

}
