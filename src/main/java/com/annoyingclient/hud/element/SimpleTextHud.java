package com.annoyingclient.hud.element;

import com.annoyingclient.hud.HudModule;
import com.annoyingclient.render.TextRenderer;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Base for "Label: value" elements. Subclasses supply the value; it is only re-formatted when the
 * underlying number changes, so a steady HUD allocates no strings per frame.
 */
public abstract class SimpleTextHud extends HudModule {

    private String cachedValue = "";
    private long cachedKey = Long.MIN_VALUE;

    protected SimpleTextHud(String name, String description, boolean enabledByDefault,
                            int anchorX, int anchorY, float offsetX, float offsetY) {
        super(name, description, enabledByDefault, anchorX, anchorY, offsetX, offsetY);
    }

    protected abstract String label();

    /** A number that identifies the current value; the text is rebuilt only when it changes. */
    protected abstract long valueKey();

    protected abstract String formatValue();

    @Override
    protected void renderContent(GuiGraphics g, float partialTick, boolean editing) {
        long key = valueKey();
        if (key != cachedKey) {
            cachedKey = key;
            cachedValue = formatValue();
        }
        String label = label();
        int labelWidth = TextRenderer.width(label);
        float w = 6 + labelWidth + 4 + TextRenderer.width(cachedValue) + 6;
        float h = 15;
        setSize(w, h);
        drawBackground(g, w, h);
        TextRenderer.draw(g, label, 6, 4, accentColor(0));
        TextRenderer.draw(g, cachedValue, 6 + labelWidth + 4, 4, textColor());
    }
}
