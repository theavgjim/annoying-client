package com.annoyingclient.gui.component;

import com.annoyingclient.AnnoyingClient;
import com.annoyingclient.render.RenderUtil;
import com.annoyingclient.render.TextRenderer;
import com.annoyingclient.theme.ThemeManager;
import com.annoyingclient.util.ColorUtil;
import net.minecraft.client.gui.GuiGraphics;

/** Shared drawing for small controls so every component looks consistent. */
public final class Widgets {

    public static final float SWITCH_W = 20;
    public static final float SWITCH_H = 11;

    private Widgets() {
    }

    /** A pill toggle switch; {@code progress} 0 = off, 1 = on (animated by the caller). */
    public static void toggleSwitch(GuiGraphics g, float x, float y, float progress, float hover) {
        ThemeManager t = AnnoyingClient.theme();
        int off = ColorUtil.shade(t.control(), hover * 0.08f);
        int track = ColorUtil.lerp(off, t.accent(), progress);
        RenderUtil.roundedRect(g, x, y, SWITCH_W, SWITCH_H, SWITCH_H / 2f, track);
        float knobR = SWITCH_H / 2f - 1.5f;
        float kx = x + SWITCH_H / 2f + (SWITCH_W - SWITCH_H) * progress;
        RenderUtil.circle(g, kx, y + SWITCH_H / 2f + 0.4f, knobR + 0.6f, 0x40000000);
        RenderUtil.circle(g, kx, y + SWITCH_H / 2f, knobR, ColorUtil.lerp(t.textMuted(), 0xFFFFFFFF, progress));
    }

    /** A rounded button with centered label. */
    public static void button(GuiGraphics g, float x, float y, float w, float h, String label, float hover, boolean primary) {
        ThemeManager t = AnnoyingClient.theme();
        int base = primary ? t.accent() : t.control();
        int bg = ColorUtil.shade(base, hover * (primary ? 0.12f : 0.08f));
        RenderUtil.roundedRect(g, x, y, w, h, Math.min(4, h / 2f), bg);
        int text = primary ? 0xFFFFFFFF : t.text();
        TextRenderer.draw(g, label, x + w / 2f - TextRenderer.width(label) / 2f, y + h / 2f - 4, text);
    }

    /** Small chevron drawn with two short bars; {@code open} 0..1 rotates it from right to down. */
    public static void chevron(GuiGraphics g, float cx, float cy, float open, int color) {
        var pose = g.pose();
        pose.pushMatrix();
        pose.translate(cx, cy);
        pose.rotate((float) (Math.PI / 2 * open));
        pose.translate(-1.5f, 0);
        pose.pushMatrix();
        pose.rotate((float) Math.toRadians(45));
        RenderUtil.rect(g, -2.6f, -0.6f, 3.6f, 1.2f, color);
        pose.popMatrix();
        pose.pushMatrix();
        pose.rotate((float) Math.toRadians(-45));
        RenderUtil.rect(g, -2.6f, -0.6f, 3.6f, 1.2f, color);
        pose.popMatrix();
        pose.popMatrix();
    }
}
