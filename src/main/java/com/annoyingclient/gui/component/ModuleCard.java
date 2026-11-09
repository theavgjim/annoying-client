package com.annoyingclient.gui.component;

import com.annoyingclient.module.Module;
import com.annoyingclient.render.RenderUtil;
import com.annoyingclient.render.TextRenderer;
import com.annoyingclient.setting.Setting;
import com.annoyingclient.util.Animation;
import com.annoyingclient.util.ColorUtil;
import com.annoyingclient.util.MathUtil;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.input.KeyEvent;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * A module row: left-click toggles (or runs) the module, right-click or the chevron expands its
 * settings. Settings are generated from the module's setting list, so new settings appear here
 * automatically; settings whose visibility condition is false are hidden on the fly.
 */
public class ModuleCard extends UIComponent {

    private static final float HEADER = 30;
    private static final float PAD = 8;
    private static final float GAP = 3;

    private final Module module;
    private final List<SettingComponent<?>> settings = new ArrayList<>();
    private final Button runButton;
    private boolean expanded;
    private final Animation expandAnim = new Animation(0f, 15f);
    private final Animation hoverAnim = new Animation(0f, 16f);
    private final Animation toggleAnim;
    private float settingsHeight;

    public ModuleCard(Module module) {
        this.module = module;
        for (Setting<?> s : module.getSettings()) {
            if (!s.isHidden()) {
                settings.add(SettingComponent.create(s));
            }
        }
        toggleAnim = new Animation(module.isEnabled() ? 1f : 0f, 15f);
        runButton = new Button("Open", true, 14, () -> module.setEnabled(true));
    }

    public Module getModule() {
        return module;
    }

    @Override
    public float getHeight() {
        return HEADER + expandAnim.get() * (settingsHeight + PAD);
    }

    private float measureSettings() {
        float h = 0;
        for (SettingComponent<?> c : settings) {
            if (c.isVisible()) {
                h += c.getHeight() + GAP;
            }
        }
        return Math.max(0, h - GAP);
    }

    @Override
    public void render(GuiGraphics g, float mouseX, float mouseY) {
        var t = theme();
        boolean headerHover = MathUtil.inside(mouseX, mouseY, x, y, width, HEADER);
        hoverAnim.setTarget(headerHover ? 1f : 0f);
        toggleAnim.setTarget(module.isEnabled() ? 1f : 0f);
        expandAnim.setTarget(expanded ? 1f : 0f);
        float hv = hoverAnim.update();
        float on = toggleAnim.update();
        float ex = expandAnim.update();
        settingsHeight = measureSettings();
        float total = getHeight();

        int bg = ColorUtil.lerp(t.surface(), t.surfaceHover(), hv);
        RenderUtil.roundedRect(g, x, y, width, total, 5, bg);
        if (on > 0.01f) {
            // accent glow strip on the left for enabled modules
            RenderUtil.roundedRect(g, x, y + 7, 2.5f * on + 0.01f, HEADER - 14, 1.25f, ColorUtil.fade(t.accent(), on));
        }

        float textX = x + 10;
        TextRenderer.drawBold(g, module.getName(), textX, y + 6, t.text());
        float rightReserve = module.isToggleable() ? Widgets.SWITCH_W + 30 : 60;
        TextRenderer.draw(g, TextRenderer.trim(module.getDescription(), (int) (width - 20 - rightReserve)), textX, y + 17, t.textMuted());

        if (module.getKeybind().isBound()) {
            String key = module.getKeybind().getDisplayName();
            float kw = TextRenderer.width(key) * 0.75f + 6;
            float kx = textX + TextRenderer.width(module.getName(), true) + 5;
            RenderUtil.roundedRect(g, kx, y + 5.5f, kw, 9, 2.5f, t.control());
            TextRenderer.drawScaled(g, key, kx + 3, y + 7.5f, 0.75f, t.textMuted(), false);
        }

        float right = x + width - 10;
        if (!settings.isEmpty()) {
            Widgets.chevron(g, right - 2, y + HEADER / 2f, ex, ColorUtil.lerp(t.textMuted(), t.text(), hv));
            right -= 14;
        }
        if (module.isToggleable()) {
            Widgets.toggleSwitch(g, right - Widgets.SWITCH_W, y + (HEADER - Widgets.SWITCH_H) / 2f, on, hv);
        } else {
            runButton.setBounds(right - 40, y + (HEADER - 14) / 2f, 40);
            runButton.render(g, mouseX, mouseY);
        }

        if (ex > 0.01f) {
            float sy = y + HEADER;
            RenderUtil.rect(g, x + 8, sy - 1, width - 16, 0.6f, ColorUtil.withAlpha(t.border(), Math.round(160 * ex)));
            RenderUtil.scissor(g, x, sy, width, total - HEADER);
            float cy = sy + PAD / 2f + 1;
            for (SettingComponent<?> c : settings) {
                if (!c.isVisible()) {
                    continue;
                }
                c.setBounds(x + 12, cy, width - 24);
                c.render(g, mouseX, mouseY);
                cy += c.getHeight() + GAP;
            }
            RenderUtil.endScissor(g);
        }
    }

    @Override
    public boolean mouseClicked(float mouseX, float mouseY, int button) {
        if (!isHovered(mouseX, mouseY)) {
            for (SettingComponent<?> c : settings) {
                c.unfocus();
            }
            return false;
        }
        if (mouseY < y + HEADER) {
            if (!module.isToggleable() && runButton.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
            boolean onChevron = !settings.isEmpty() && mouseX > x + width - 20;
            if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT || (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && onChevron)) {
                if (!settings.isEmpty()) {
                    expanded = !expanded;
                    if (!expanded) {
                        for (SettingComponent<?> c : settings) {
                            c.unfocus();
                        }
                    }
                }
                return true;
            }
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                if (module.isToggleable()) {
                    module.toggle();
                } else {
                    module.setEnabled(true);
                }
                return true;
            }
            return false;
        }
        if (expanded && expandAnim.get() > 0.9f) {
            boolean handled = false;
            for (SettingComponent<?> c : settings) {
                if (c.isVisible() && c.mouseClicked(mouseX, mouseY, button)) {
                    handled = true;
                } else if (!c.isHovered(mouseX, mouseY)) {
                    c.unfocus();
                }
            }
            return handled;
        }
        return true;
    }

    @Override
    public void mouseReleased(float mouseX, float mouseY, int button) {
        for (SettingComponent<?> c : settings) {
            c.mouseReleased(mouseX, mouseY, button);
        }
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (!expanded) {
            return false;
        }
        for (SettingComponent<?> c : settings) {
            if (c.isCapturingInput() && c.keyPressed(event)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean charTyped(int codepoint) {
        if (!expanded) {
            return false;
        }
        for (SettingComponent<?> c : settings) {
            if (c.isCapturingInput() && c.charTyped(codepoint)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean isCapturingInput() {
        if (!expanded) {
            return false;
        }
        for (SettingComponent<?> c : settings) {
            if (c.isCapturingInput()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void unfocus() {
        for (SettingComponent<?> c : settings) {
            c.unfocus();
        }
    }

    @Override
    public String getTooltip(float mouseX, float mouseY) {
        if (expanded) {
            for (SettingComponent<?> c : settings) {
                if (c.isVisible() && c.isHovered(mouseX, mouseY)) {
                    return c.getTooltip(mouseX, mouseY);
                }
            }
        }
        if (mouseY < y + HEADER && isHovered(mouseX, mouseY)) {
            return settings.isEmpty() ? null : "Left-click to " + (module.isToggleable() ? "toggle" : "open")
                    + ", right-click for settings";
        }
        return null;
    }
}
