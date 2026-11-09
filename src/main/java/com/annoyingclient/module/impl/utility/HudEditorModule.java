package com.annoyingclient.module.impl.utility;

import com.annoyingclient.hud.HudEditorScreen;
import com.annoyingclient.module.Category;
import com.annoyingclient.module.Module;
import com.annoyingclient.setting.BooleanSetting;
import com.annoyingclient.setting.KeybindSetting;
import com.annoyingclient.setting.NumberSetting;

/** Opens the drag-and-drop HUD editor. */
public class HudEditorModule extends Module {

    private final BooleanSetting snapping = add(new BooleanSetting("Snapping", "Snap elements to screen edges, the center and each other", true));
    private final NumberSetting snapDistance = add(new NumberSetting("Snap Distance", "How close an edge must be to snap", 4.0, 1.0, 12.0, 1.0));
    private final BooleanSetting grid = add(new BooleanSetting("Show Grid", "Draw a grid while editing", true));

    public HudEditorModule() {
        super("HUD Editor", "Move, scale and toggle HUD elements", Category.UTILITY, false, KeybindSetting.NONE);
        snapDistance.visibleWhen(snapping::isEnabled);
    }

    @Override
    public boolean isToggleable() {
        return false;
    }

    @Override
    protected void onActivate() {
        mc.setScreen(new HudEditorScreen(mc.screen));
    }

    public boolean snapping() {
        return snapping.isEnabled();
    }

    public float snapDistance() {
        return snapDistance.getFloat();
    }

    public boolean grid() {
        return grid.isEnabled();
    }
}
