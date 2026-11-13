package com.annoyingclient.gui.component;

import com.annoyingclient.AnnoyingClient;
import com.annoyingclient.theme.ThemeManager;
import com.annoyingclient.util.MathUtil;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.input.KeyEvent;

/**
 * Base class for ClickGUI and HUD editor widgets.
 *
 * <p>Components work in the coordinate space of the window that owns them (already scaled and
 * translated); the owner sets the position each frame with {@link #setBounds} and the component
 * reports its (possibly animated) height. Mouse coordinates passed in are in the same space.
 */
public abstract class UIComponent {

    protected float x;
    protected float y;
    protected float width;

    public void setBounds(float x, float y, float width) {
        this.x = x;
        this.y = y;
        this.width = width;
    }

    public abstract float getHeight();

    /** Position in the screen's local coordinates, as last laid out. */
    public float getX() {
        return x;
    }

    public float getY() {
        return y;
    }

    public float getWidth() {
        return width;
    }

    public abstract void render(GuiGraphics g, float mouseX, float mouseY);

    public boolean mouseClicked(float mouseX, float mouseY, int button) {
        return false;
    }

    public void mouseReleased(float mouseX, float mouseY, int button) {
    }

    public void mouseDragged(float mouseX, float mouseY, int button) {
    }

    public boolean keyPressed(KeyEvent event) {
        return false;
    }

    public boolean charTyped(int codepoint) {
        return false;
    }

    /** True while the component wants all keyboard input (focused text field, keybind listening). */
    public boolean isCapturingInput() {
        return false;
    }

    /** Clears focus/listening state (for example when the window closes or another field is clicked). */
    public void unfocus() {
    }

    public boolean isHovered(float mouseX, float mouseY) {
        return MathUtil.inside(mouseX, mouseY, x, y, width, getHeight());
    }

    /** Description to show as a tooltip when hovered, or null. */
    public String getTooltip(float mouseX, float mouseY) {
        return null;
    }

    protected static ThemeManager theme() {
        return AnnoyingClient.theme();
    }
}
