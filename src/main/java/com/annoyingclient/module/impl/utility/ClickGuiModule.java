package com.annoyingclient.module.impl.utility;

import com.annoyingclient.gui.clickgui.ClickGuiScreen;
import com.annoyingclient.module.Category;
import com.annoyingclient.module.Module;
import com.annoyingclient.setting.BooleanSetting;
import org.lwjgl.glfw.GLFW;

/** Opens the ClickGUI. Bound to Right Shift by default. */
public class ClickGuiModule extends Module {

    private final BooleanSetting pauseGame = add(new BooleanSetting("Pause Singleplayer", "Pause a singleplayer world while the ClickGUI is open", false));
    private final BooleanSetting rememberTab = add(new BooleanSetting("Remember Tab", "Reopen on the last category", true));

    public ClickGuiModule() {
        super("ClickGUI", "Opens the Annoying Client menu", Category.UTILITY, false, GLFW.GLFW_KEY_RIGHT_SHIFT);
    }

    @Override
    public boolean isToggleable() {
        return false;
    }

    @Override
    protected void onActivate() {
        if (mc.screen instanceof ClickGuiScreen) {
            return;
        }
        mc.setScreen(new ClickGuiScreen(mc.screen));
    }

    public boolean pausesGame() {
        return pauseGame.isEnabled();
    }

    public boolean remembersTab() {
        return rememberTab.isEnabled();
    }
}
