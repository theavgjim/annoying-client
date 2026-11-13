package com.annoyingclient.gui.component;

import com.annoyingclient.modconfig.ModConfigPrefs;
import com.annoyingclient.render.RenderUtil;
import com.annoyingclient.render.TextRenderer;
import com.annoyingclient.util.Animation;
import com.annoyingclient.util.ColorUtil;
import com.annoyingclient.util.MathUtil;
import net.minecraft.client.gui.GuiGraphics;
import org.lwjgl.glfw.GLFW;

/** The top row of the Mod Configs tab: filter chips, a "Libraries" switch and a summary of the list. */
public class ModConfigToolbar extends UIComponent {

    private static final float H = 22;
    private static final String[] LABELS = {"All", "With settings", "Favorites"};

    private final Runnable onChange;
    private final Animation libSwitch = new Animation(0f, 14f);
    private String summary = "";

    public ModConfigToolbar(Runnable onChange) {
        this.onChange = onChange;
        libSwitch.snap(ModConfigPrefs.get().showLibraries() ? 1f : 0f);
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    @Override
    public float getHeight() {
        return H;
    }

    private float chipWidth(int i) {
        return TextRenderer.width(LABELS[i]) + 16;
    }

    private float chipX(int i) {
        float cx = x;
        for (int k = 0; k < i; k++) {
            cx += chipWidth(k) + 4;
        }
        return cx;
    }

    private float libX() {
        return x + width - Widgets.SWITCH_W - TextRenderer.width("Libraries") - 6;
    }

    @Override
    public void render(GuiGraphics g, float mouseX, float mouseY) {
        var t = theme();
        ModConfigPrefs prefs = ModConfigPrefs.get();
        for (int i = 0; i < LABELS.length; i++) {
            boolean active = prefs.filter().ordinal() == i;
            float cx = chipX(i), cw = chipWidth(i);
            boolean hover = MathUtil.inside(mouseX, mouseY, cx, y + 2, cw, 17);
            int bg = active ? ColorUtil.withAlpha(t.accent(), 70) : hover ? t.surfaceHover() : t.surface();
            RenderUtil.roundedRect(g, cx, y + 2, cw, 17, 8.5f, bg);
            if (active) {
                RenderUtil.roundedOutline(g, cx, y + 2, cw, 17, 8.5f, 0.8f, t.accent());
            }
            TextRenderer.draw(g, LABELS[i], cx + 8, y + 6, active ? t.text() : t.textMuted());
        }
        // Libraries switch (right) and the summary between.
        libSwitch.setTarget(prefs.showLibraries() ? 1f : 0f);
        float lx = libX();
        TextRenderer.draw(g, "Libraries", lx, y + 6, t.textMuted());
        Widgets.toggleSwitch(g, x + width - Widgets.SWITCH_W, y + 5, libSwitch.update(),
                MathUtil.inside(mouseX, mouseY, lx, y, x + width - lx, H) ? 1f : 0f);
        float sx = chipX(LABELS.length) + 6;
        TextRenderer.draw(g, TextRenderer.trim(summary, (int) (lx - sx - 10)), sx, y + 6, t.textDisabled());
    }

    @Override
    public boolean mouseClicked(float mouseX, float mouseY, int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT || !isHovered(mouseX, mouseY)) {
            return false;
        }
        ModConfigPrefs prefs = ModConfigPrefs.get();
        for (int i = 0; i < LABELS.length; i++) {
            if (MathUtil.inside(mouseX, mouseY, chipX(i), y + 2, chipWidth(i), 17)) {
                prefs.setFilter(ModConfigPrefs.Filter.values()[i]);
                onChange.run();
                return true;
            }
        }
        if (mouseX >= libX()) {
            prefs.setShowLibraries(!prefs.showLibraries());
            onChange.run();
            return true;
        }
        return true;
    }

    @Override
    public String getTooltip(float mouseX, float mouseY) {
        return mouseX >= libX() && isHovered(mouseX, mouseY)
                ? "Show libraries: mods that other mods need (Fabric API, Cloth Config...)" : null;
    }
}
