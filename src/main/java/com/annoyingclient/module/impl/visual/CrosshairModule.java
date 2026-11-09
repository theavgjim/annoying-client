package com.annoyingclient.module.impl.visual;

import com.annoyingclient.module.Category;
import com.annoyingclient.module.Module;
import com.annoyingclient.render.RenderUtil;
import com.annoyingclient.setting.BooleanSetting;
import com.annoyingclient.setting.ColorSetting;
import com.annoyingclient.setting.KeybindSetting;
import com.annoyingclient.setting.ModeSetting;
import com.annoyingclient.setting.NumberSetting;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Replaces the vanilla crosshair sprite with a configurable one. Vanilla still decides when a
 * crosshair is shown (first person, spectator rules, F3 axes) and still draws the attack indicator.
 */
public class CrosshairModule extends Module {

    private final ModeSetting style = add(new ModeSetting("Style", "Crosshair shape", "Cross", "Cross", "Dot", "Circle", "T-Shape", "Square"));
    private final ColorSetting color = add(new ColorSetting("Color", "Crosshair color", 0xFFFFFFFF, true));
    private final NumberSetting size = add(new NumberSetting("Size", "Arm length", 5.0, 1.0, 15.0, 0.5));
    private final NumberSetting gap = add(new NumberSetting("Gap", "Space between the center and the arms", 2.0, 0.0, 10.0, 0.5));
    private final NumberSetting thickness = add(new NumberSetting("Thickness", "Line thickness", 1.0, 0.5, 4.0, 0.5));
    private final BooleanSetting centerDot = add(new BooleanSetting("Center Dot", "Draw a dot in the middle", false));
    private final BooleanSetting outline = add(new BooleanSetting("Outline", "Draw a dark outline for contrast", true));
    private final ColorSetting outlineColor = add(new ColorSetting("Outline Color", "Outline color", 0xB0000000, true));

    public CrosshairModule() {
        super("Custom Crosshair", "Replace the crosshair with a customizable one", Category.VISUAL, false, KeybindSetting.NONE);
        outlineColor.visibleWhen(outline::isEnabled);
        gap.visibleWhen(() -> !style.is("Dot"));
        size.visibleWhen(() -> !style.is("Dot"));
    }

    public void render(GuiGraphics g) {
        float cx = g.guiWidth() / 2f;
        float cy = g.guiHeight() / 2f;
        int c = color.getColor();
        if (outline.isEnabled()) {
            drawShape(g, cx, cy, outlineColor.getColor(), 0.5f);
        }
        drawShape(g, cx, cy, c, 0f);
    }

    private void drawShape(GuiGraphics g, float cx, float cy, int c, float grow) {
        float t = thickness.getFloat() + grow * 2;
        float half = t / 2f;
        float len = size.getFloat() + grow * 2;
        float gp = gap.getFloat() - grow;
        switch (style.getValue()) {
            case "Dot" -> RenderUtil.circle(g, cx, cy, Math.max(1f, t) + grow, c);
            case "Circle" -> {
                float r = gp + len / 2f;
                int segments = 24;
                for (int i = 0; i < segments; i++) {
                    double a = Math.PI * 2 * i / segments;
                    RenderUtil.circle(g, cx + (float) Math.cos(a) * r, cy + (float) Math.sin(a) * r, half + 0.15f, c);
                }
            }
            case "Square" -> {
                float s = gp + len / 2f;
                RenderUtil.rect(g, cx - s - half, cy - s - half, s * 2 + t, t, c);
                RenderUtil.rect(g, cx - s - half, cy + s - half, s * 2 + t, t, c);
                RenderUtil.rect(g, cx - s - half, cy - s + half, t, s * 2 - t, c);
                RenderUtil.rect(g, cx + s - half, cy - s + half, t, s * 2 - t, c);
            }
            default -> {
                boolean tShape = style.is("T-Shape");
                if (!tShape) {
                    RenderUtil.rect(g, cx - half, cy - gp - len, t, len, c);              // up
                }
                RenderUtil.rect(g, cx - half, cy + gp, t, len, c);                      // down
                RenderUtil.rect(g, cx - gp - len, cy - half, len, t, c);                // left
                RenderUtil.rect(g, cx + gp, cy - half, len, t, c);                      // right
            }
        }
        if (centerDot.isEnabled() && !style.is("Dot")) {
            RenderUtil.rect(g, cx - half, cy - half, t, t, c);
        }
    }

    @Override
    public String getDisplayInfo() {
        return style.getValue();
    }
}
