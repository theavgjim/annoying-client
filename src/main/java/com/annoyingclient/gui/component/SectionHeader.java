package com.annoyingclient.gui.component;

import com.annoyingclient.render.RenderUtil;
import com.annoyingclient.render.TextRenderer;
import com.annoyingclient.util.ColorUtil;
import net.minecraft.client.gui.GuiGraphics;

/** A small section title with a divider line, used to group content in the Themes and Client tabs. */
public class SectionHeader extends UIComponent {

    private final String title;

    public SectionHeader(String title) {
        this.title = title;
    }

    @Override
    public float getHeight() {
        return 16;
    }

    @Override
    public void render(GuiGraphics g, float mouseX, float mouseY) {
        var t = theme();
        TextRenderer.drawBold(g, title, x, y + 5, t.textMuted());
        float lx = x + TextRenderer.width(title, true) + 6;
        RenderUtil.rect(g, lx, y + 9, x + width - lx, 0.6f, ColorUtil.withAlpha(t.border(), 200));
    }
}
