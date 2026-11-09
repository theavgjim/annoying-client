package com.annoyingclient.gui.component;

import com.annoyingclient.render.RenderUtil;
import com.annoyingclient.render.TextRenderer;
import com.annoyingclient.setting.KeybindSetting;
import com.annoyingclient.util.ColorUtil;
import com.annoyingclient.util.MathUtil;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.input.KeyEvent;
import org.lwjgl.glfw.GLFW;

/**
 * Click, then press a key (or a middle/side mouse button) to bind. Backspace or Delete unbinds,
 * Escape cancels. Right-click resets to the default bind.
 */
public class KeybindComponent extends SettingComponent<KeybindSetting> {

    private boolean listening;

    public KeybindComponent(KeybindSetting setting, String label) {
        super(setting, label);
    }

    @Override
    public float getHeight() {
        return 18;
    }

    private String boxText() {
        return listening ? "Press a key..." : setting.getDisplayName();
    }

    private float boxW() {
        return Math.max(40, TextRenderer.width(boxText()) + 12);
    }

    @Override
    public void render(GuiGraphics g, float mouseX, float mouseY) {
        float h = updateHover(mouseX, mouseY);
        var t = theme();
        TextRenderer.draw(g, label, x, y + 5, t.text());
        float bw = boxW();
        float bx = x + width - bw;
        int bg = listening
                ? ColorUtil.lerp(t.control(), t.accent(), 0.35f + 0.25f * (float) Math.sin(System.currentTimeMillis() / 160.0))
                : ColorUtil.shade(t.control(), h * 0.07f);
        RenderUtil.roundedRect(g, bx, y + 2, bw, 14, 4, bg);
        String text = boxText();
        int color = listening ? 0xFFFFFFFF : setting.isBound() ? t.text() : t.textDisabled();
        TextRenderer.draw(g, text, bx + bw / 2f - TextRenderer.width(text) / 2f, y + 5, color);
    }

    @Override
    public boolean mouseClicked(float mouseX, float mouseY, int button) {
        float bw = boxW();
        boolean onBox = MathUtil.inside(mouseX, mouseY, x + width - bw, y, bw, 18);
        if (listening) {
            // Middle and side buttons can be bound; left/right click would fire constantly in game.
            if (button >= GLFW.GLFW_MOUSE_BUTTON_MIDDLE) {
                setting.setValue(KeybindSetting.mouseCode(button));
            }
            listening = false;
            return true;
        }
        if (isHovered(mouseX, mouseY)) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && onBox) {
                listening = true;
                return true;
            }
            if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                setting.reset();
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (!listening) {
            return false;
        }
        int key = event.key();
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            listening = false;
        } else if (key == GLFW.GLFW_KEY_BACKSPACE || key == GLFW.GLFW_KEY_DELETE) {
            setting.setValue(KeybindSetting.NONE);
            listening = false;
        } else {
            setting.setValue(key);
            listening = false;
        }
        return true;
    }

    @Override
    public boolean isCapturingInput() {
        return listening;
    }

    @Override
    public void unfocus() {
        listening = false;
    }
}
