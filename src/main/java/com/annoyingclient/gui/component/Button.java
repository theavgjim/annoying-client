package com.annoyingclient.gui.component;

import com.annoyingclient.util.Animation;
import net.minecraft.client.gui.GuiGraphics;
import org.lwjgl.glfw.GLFW;

public class Button extends UIComponent {

    private String label;
    private final Runnable action;
    private final boolean primary;
    private final float height;
    private final Animation hover = new Animation(0f, 16f);

    public Button(String label, boolean primary, float height, Runnable action) {
        this.label = label;
        this.primary = primary;
        this.height = height;
        this.action = action;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    @Override
    public float getHeight() {
        return height;
    }

    @Override
    public void render(GuiGraphics g, float mouseX, float mouseY) {
        hover.setTarget(isHovered(mouseX, mouseY) ? 1f : 0f);
        Widgets.button(g, x, y, width, height, label, hover.update(), primary);
    }

    @Override
    public boolean mouseClicked(float mouseX, float mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && isHovered(mouseX, mouseY)) {
            action.run();
            return true;
        }
        return false;
    }
}
