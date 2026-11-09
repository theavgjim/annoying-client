package com.annoyingclient.gui.component;

import com.annoyingclient.render.Icons;
import com.annoyingclient.render.RenderUtil;
import com.annoyingclient.render.TextRenderer;
import com.annoyingclient.theme.Theme;
import com.annoyingclient.theme.ThemeManager;
import com.annoyingclient.util.Animation;
import com.annoyingclient.util.ColorUtil;
import com.annoyingclient.util.MathUtil;
import net.minecraft.client.gui.GuiGraphics;
import org.lwjgl.glfw.GLFW;

/** Clickable cards for the theme presets, each previewing its colors. */
public class ThemePresetGrid extends UIComponent {

    private static final float CARD_H = 44;
    private static final float GAP = 6;
    private final Animation[] hover = new Animation[ThemeManager.PRESETS.size()];

    public ThemePresetGrid() {
        for (int i = 0; i < hover.length; i++) {
            hover[i] = new Animation(0f, 16f);
        }
    }

    private int columns() {
        return width > 300 ? 3 : 2;
    }

    private float cardW() {
        int c = columns();
        return (width - GAP * (c - 1)) / c;
    }

    @Override
    public float getHeight() {
        int rows = (ThemeManager.PRESETS.size() + columns() - 1) / columns();
        return rows * CARD_H + (rows - 1) * GAP;
    }

    @Override
    public void render(GuiGraphics g, float mouseX, float mouseY) {
        var t = theme();
        float cw = cardW();
        for (int i = 0; i < ThemeManager.PRESETS.size(); i++) {
            Theme theme = ThemeManager.PRESETS.get(i);
            float cx = x + (i % columns()) * (cw + GAP);
            float cy = y + (i / columns()) * (CARD_H + GAP);
            boolean hovered = MathUtil.inside(mouseX, mouseY, cx, cy, cw, CARD_H);
            hover[i].setTarget(hovered ? 1f : 0f);
            float h = hover[i].update();
            boolean selected = t.getPresetName().equals(theme.name());

            RenderUtil.roundedRect(g, cx, cy, cw, CARD_H, 6, ColorUtil.shade(theme.background(), 0.05f + h * 0.04f));
            if (selected) {
                RenderUtil.roundedOutline(g, cx, cy, cw, CARD_H, 6, 1.2f, theme.accent());
            }
            // preview: accent gradient bar + two swatches
            RenderUtil.horizontalGradient(g, cx + 8, cy + 8, cw - 16, 4, theme.accent(), theme.accent2());
            RenderUtil.circle(g, cx + 12, cy + 22, 4, theme.accent());
            RenderUtil.circle(g, cx + 23, cy + 22, 4, theme.accent2());
            RenderUtil.circle(g, cx + 34, cy + 22, 4, ColorUtil.shade(theme.background(), 0.2f));
            TextRenderer.draw(g, theme.name(), cx + 8, cy + 31, theme.text());
            if (selected) {
                Icons.draw(g, Icons.Icon.CHECK, cx + cw - 12, cy + 35, 7, theme.accent());
            }
        }
    }

    @Override
    public boolean mouseClicked(float mouseX, float mouseY, int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return false;
        }
        float cw = cardW();
        for (int i = 0; i < ThemeManager.PRESETS.size(); i++) {
            float cx = x + (i % columns()) * (cw + GAP);
            float cy = y + (i / columns()) * (CARD_H + GAP);
            if (MathUtil.inside(mouseX, mouseY, cx, cy, cw, CARD_H)) {
                theme().applyPreset(ThemeManager.PRESETS.get(i));
                return true;
            }
        }
        return false;
    }

    @Override
    public String getTooltip(float mouseX, float mouseY) {
        return isHovered(mouseX, mouseY) ? "Click a theme to apply it instantly" : null;
    }
}
