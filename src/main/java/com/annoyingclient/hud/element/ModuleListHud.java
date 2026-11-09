package com.annoyingclient.hud.element;

import com.annoyingclient.AnnoyingClient;
import com.annoyingclient.hud.HudModule;
import com.annoyingclient.module.Module;
import com.annoyingclient.render.RenderUtil;
import com.annoyingclient.render.TextRenderer;
import com.annoyingclient.setting.BooleanSetting;
import com.annoyingclient.setting.ModeSetting;
import com.annoyingclient.util.ColorUtil;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.List;

/**
 * Lists enabled modules, longest first, sliding them in and out as they are toggled.
 * Aligns to the right edge automatically when placed on the right side of the screen.
 */
public class ModuleListHud extends HudModule {

    private final ModeSetting sorting = add(new ModeSetting("Sorting", "Order of the list", "Length", "Length", "Alphabetical"));
    private final BooleanSetting showInfo = add(new BooleanSetting("Show Info", "Show extra info such as the zoom level", true));
    private final ModeSetting bar = add(new ModeSetting("Accent Bar", "Colored bar beside each entry", "Side", "Side", "None"));

    private final List<Module> visible = new ArrayList<>();
    private final List<Module> previewModules = new ArrayList<>();

    public ModuleListHud() {
        super("Module List", "Lists your enabled modules on the HUD", true, ANCHOR_END, ANCHOR_START, 2, 2);
    }

    private int entryWidth(Module m) {
        String info = showInfo.isEnabled() ? m.getDisplayInfo() : "";
        return TextRenderer.width(m.getName()) + (info.isEmpty() ? 0 : 4 + TextRenderer.width(info));
    }

    @Override
    protected void renderContent(GuiGraphics g, float partialTick, boolean editing) {
        visible.clear();
        for (Module m : AnnoyingClient.modules().getModules()) {
            boolean shown = m.isEnabled() && m.isVisibleInModuleList();
            m.getListAnimation().setTarget(shown ? 1f : 0f);
            float a = m.getListAnimation().update();
            if (a > 0.01f) {
                visible.add(m);
            }
        }
        List<Module> list = visible;
        if (list.isEmpty() && editing) {
            previewModules.clear();
            for (Module m : AnnoyingClient.modules().getModules()) {
                if (m.isToggleable() && previewModules.size() < 4 && m != this) {
                    previewModules.add(m);
                }
            }
            list = previewModules;
        }
        sort(list);

        float lineHeight = 12;
        float maxWidth = 30;
        for (Module m : list) {
            maxWidth = Math.max(maxWidth, entryWidth(m) + 8 + (bar.is("Side") ? 2 : 0));
        }
        boolean right = isRightSide();
        float y = 0;
        int index = 0;
        boolean preview = list == previewModules;
        for (Module m : list) {
            float a = preview ? 1f : m.getListAnimation().get();
            float eased = a * a * (3 - 2 * a);
            float entryW = entryWidth(m) + 8 + (bar.is("Side") ? 2 : 0);
            float slide = (1f - eased) * (entryW + 4);
            float x = right ? maxWidth - entryW + slide : -slide;
            float h = lineHeight * eased;
            int accent = accentColor(index * 180L);
            if (background.isEnabled()) {
                var colors = hudColors();
                float opacity = colors != null ? colors.backgroundOpacity() : 0.55f;
                RenderUtil.rect(g, x, y, entryW, h, ColorUtil.fade(ColorUtil.withAlpha(AnnoyingClient.theme().background(), Math.round(opacity * 255)), eased));
            }
            if (bar.is("Side")) {
                RenderUtil.rect(g, right ? x + entryW - 2 : x, y, 2, h, ColorUtil.fade(accent, eased));
            }
            float textX = x + (right ? 4 : (bar.is("Side") ? 6 : 4));
            float textY = y + (h - 8) / 2f;
            TextRenderer.draw(g, m.getName(), textX, textY, ColorUtil.fade(accent, eased));
            String info = showInfo.isEnabled() ? m.getDisplayInfo() : "";
            if (!info.isEmpty()) {
                TextRenderer.draw(g, info, textX + TextRenderer.width(m.getName()) + 4, textY, ColorUtil.fade(mutedColor(), eased));
            }
            y += h;
            index++;
        }
        setSize(maxWidth, Math.max(lineHeight, y));
    }

    @Override
    protected void drawBackground(GuiGraphics g, float w, float h) {
        // Each entry draws its own background strip.
    }

    private void sort(List<Module> list) {
        // Insertion sort on a short list: stable, no allocation.
        boolean byLength = sorting.is("Length");
        for (int i = 1; i < list.size(); i++) {
            Module key = list.get(i);
            int j = i - 1;
            while (j >= 0 && compare(list.get(j), key, byLength) > 0) {
                list.set(j + 1, list.get(j));
                j--;
            }
            list.set(j + 1, key);
        }
    }

    private int compare(Module a, Module b, boolean byLength) {
        if (byLength) {
            int diff = entryWidth(b) - entryWidth(a);
            if (diff != 0) {
                return diff;
            }
        }
        return a.getName().compareToIgnoreCase(b.getName());
    }
}
