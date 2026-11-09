package com.annoyingclient.gui.component;

import com.annoyingclient.render.TextRenderer;
import com.annoyingclient.setting.BooleanSetting;
import com.annoyingclient.util.Animation;
import net.minecraft.client.gui.GuiGraphics;
import org.lwjgl.glfw.GLFW;

public class BooleanComponent extends SettingComponent<BooleanSetting> {

    private final Animation toggle;

    public BooleanComponent(BooleanSetting setting, String label) {
        super(setting, label);
        toggle = new Animation(setting.isEnabled() ? 1f : 0f, 16f);
    }

    @Override
    public float getHeight() {
        return 18;
    }

    @Override
    public void render(GuiGraphics g, float mouseX, float mouseY) {
        float h = updateHover(mouseX, mouseY);
        toggle.setTarget(setting.isEnabled() ? 1f : 0f);
        TextRenderer.draw(g, label, x, y + 5, theme().text());
        Widgets.toggleSwitch(g, x + width - Widgets.SWITCH_W, y + (18 - Widgets.SWITCH_H) / 2f, toggle.update(), h);
    }

    @Override
    public boolean mouseClicked(float mouseX, float mouseY, int button) {
        if (isHovered(mouseX, mouseY) && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            setting.toggle();
            return true;
        }
        return false;
    }
}
