package com.annoyingclient.gui.component;

import com.annoyingclient.render.RenderUtil;
import com.annoyingclient.render.TextRenderer;
import com.annoyingclient.setting.NumberSetting;
import com.annoyingclient.util.Animation;
import com.annoyingclient.util.MathUtil;
import net.minecraft.client.gui.GuiGraphics;
import org.lwjgl.glfw.GLFW;

/** Slider: click or drag to set; right-click resets to the default. */
public class NumberComponent extends SettingComponent<NumberSetting> {

    private boolean dragging;
    private final Animation fill;
    private String cachedText = "";
    private double cachedFor = Double.NaN;

    public NumberComponent(NumberSetting setting, String label) {
        super(setting, label);
        fill = new Animation((float) setting.getProgress(), 20f);
    }

    @Override
    public float getHeight() {
        return 24;
    }

    private float trackX() {
        return x;
    }

    private float trackW() {
        return width;
    }

    @Override
    public void render(GuiGraphics g, float mouseX, float mouseY) {
        float h = updateHover(mouseX, mouseY);
        if (dragging) {
            setting.setProgress((mouseX - trackX()) / trackW());
        }
        if (setting.get() != cachedFor) {
            cachedFor = setting.get();
            cachedText = setting.getDisplayValue();
        }
        var t = theme();
        TextRenderer.draw(g, label, x, y + 2, t.text());
        TextRenderer.draw(g, cachedText, x + width - TextRenderer.width(cachedText), y + 2, t.accent());

        fill.setTarget((float) setting.getProgress());
        float p = fill.update();
        float ty = y + 16;
        RenderUtil.roundedRect(g, trackX(), ty, trackW(), 3, 1.5f, t.control());
        float fw = trackW() * p;
        if (fw > 0.5f) {
            RenderUtil.horizontalGradient(g, trackX() + 1, ty, Math.max(0, fw - 2), 3, t.accent(), t.accent2());
            RenderUtil.circle(g, trackX() + 1.5f, ty + 1.5f, 1.5f, t.accent());
        }
        float knob = 3.5f + (dragging ? 1f : h * 0.6f);
        RenderUtil.circle(g, trackX() + fw, ty + 1.5f, knob + 1f, 0x50000000);
        RenderUtil.circle(g, trackX() + fw, ty + 1.5f, knob, 0xFFFFFFFF);
    }

    @Override
    public boolean mouseClicked(float mouseX, float mouseY, int button) {
        if (!isHovered(mouseX, mouseY)) {
            return false;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            dragging = true;
            setting.setProgress(MathUtil.clamp((mouseX - trackX()) / trackW(), 0f, 1f));
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            setting.reset();
            return true;
        }
        return false;
    }

    @Override
    public void mouseReleased(float mouseX, float mouseY, int button) {
        dragging = false;
    }
}
