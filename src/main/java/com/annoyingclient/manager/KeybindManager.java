package com.annoyingclient.manager;

import com.annoyingclient.event.EventBus;
import com.annoyingclient.event.KeyInputEvent;
import com.annoyingclient.event.MouseInputEvent;
import com.annoyingclient.module.Module;
import com.annoyingclient.module.impl.utility.ClickGuiModule;
import com.annoyingclient.setting.KeybindSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;

/**
 * Toggles modules from their {@link KeybindSetting}.
 *
 * <p>Binds fire only in game with no screen open (so typing in chat never toggles anything), with one
 * exception: the ClickGUI bind also works on the title screen.
 */
public final class KeybindManager {

    private final ModuleManager modules;

    public KeybindManager(ModuleManager modules, EventBus bus) {
        this.modules = modules;
        bus.subscribe(KeyInputEvent.class, this::onKey);
        bus.subscribe(MouseInputEvent.class, this::onMouse);
    }

    private void onKey(KeyInputEvent event) {
        if (!event.isPress()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        boolean titleScreen = mc.screen instanceof TitleScreen;
        if (mc.screen != null && !titleScreen) {
            return;
        }
        for (Module module : modules.getModules()) {
            if (titleScreen && !(module instanceof ClickGuiModule)) {
                continue;
            }
            if (module.getKeybind().matchesKey(event.getKey())) {
                module.toggle();
            }
        }
    }

    private void onMouse(MouseInputEvent event) {
        if (!event.isPress() || !event.isInGame()) {
            return;
        }
        for (Module module : modules.getModules()) {
            if (module.getKeybind().matchesMouse(event.getButton())) {
                module.toggle();
            }
        }
    }
}
