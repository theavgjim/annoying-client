package com.annoyingclient.module.impl.utility;

import com.annoyingclient.AnnoyingClient;
import com.annoyingclient.config.ConfigManager;
import com.annoyingclient.module.Category;
import com.annoyingclient.module.Module;
import com.annoyingclient.setting.KeybindSetting;

import java.util.List;

/** A keybind that cycles through configuration profiles. Profiles are managed in the ClickGUI's Client tab. */
public class ProfileSwitcherModule extends Module {

    public ProfileSwitcherModule() {
        super("Profile Switcher", "Press the keybind to switch to the next configuration profile", Category.UTILITY, false, KeybindSetting.NONE);
    }

    @Override
    public boolean isToggleable() {
        return false;
    }

    @Override
    protected void onActivate() {
        ConfigManager config = AnnoyingClient.config();
        List<String> profiles = config.listProfiles();
        if (profiles.size() < 2) {
            AnnoyingClient.notifications().info("Only one profile", "Create more in ClickGUI > Client");
            return;
        }
        int index = profiles.indexOf(config.getActiveProfile());
        config.switchProfile(profiles.get((index + 1) % profiles.size()));
    }
}
