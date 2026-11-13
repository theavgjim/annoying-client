package com.annoyingclient.render;

import net.minecraft.client.gui.GuiGraphics;
import org.joml.Matrix3x2fStack;

/**
 * Small vector icons drawn from rectangles and circles, so they look the same with any font or
 * resource pack (Unicode symbol glyphs are not guaranteed to exist in every setup).
 */
public final class Icons {

    public enum Icon {
        RENDER, HUD, UTILITY, VISUAL, MISC, THEMES, CLIENT, SEARCH, CLOSE, CHECK, INFO, WARNING, PLAY, STOP, CONFIG, EDIT, WARDROBE, COIN, STAR, STAR_FILLED, LINK, FOLDER
    }

    private Icons() {
    }

    /** Draws {@code icon} centered at (cx, cy) inside a square of side {@code size}. */
    public static void draw(GuiGraphics g, Icon icon, float cx, float cy, float size, int color) {
        float s = size;
        float h = s / 2f;
        float t = Math.max(0.9f, s * 0.13f); // stroke width
        switch (icon) {
            case RENDER -> {
                RenderUtil.roundedOutline(g, cx - h, cy - h * 0.72f, s, s * 0.72f, s * 0.36f, t, color);
                RenderUtil.circle(g, cx, cy, s * 0.17f, color);
            }
            case HUD -> {
                RenderUtil.roundedOutline(g, cx - h, cy - h * 0.8f, s, s * 0.8f, s * 0.16f, t, color);
                RenderUtil.rect(g, cx - h + t + 1f, cy - h * 0.8f + t + 1f, s * 0.35f, t, color);
                RenderUtil.rect(g, cx - h + t + 1f, cy - h * 0.8f + t * 2 + 2f, s * 0.22f, t, color);
            }
            case UTILITY -> {
                for (int i = 0; i < 3; i++) {
                    float y = cy - h * 0.7f + i * h * 0.7f;
                    RenderUtil.rect(g, cx - h, y - t / 2f, s, t, color);
                    float knob = i == 1 ? cx + h * 0.35f : cx - h * 0.35f;
                    RenderUtil.circle(g, knob, y, t * 1.35f, color);
                }
            }
            case VISUAL -> {
                rotatedRect(g, cx, cy, s * 0.95f, t, 45, color);
                rotatedRect(g, cx, cy, s * 0.95f, t, -45, color);
                rotatedRect(g, cx, cy, s * 0.42f, s * 0.42f, 45, color);
            }
            case MISC -> {
                for (int i = -1; i <= 1; i++) {
                    RenderUtil.circle(g, cx + i * h * 0.75f, cy, s * 0.12f, color);
                }
            }
            case THEMES -> {
                RenderUtil.roundedOutline(g, cx - h, cy - h, s, s, h, t, color);
                RenderUtil.scissor(g, cx - h, cy - h, h, s);
                RenderUtil.circle(g, cx, cy, h, color);
                RenderUtil.endScissor(g);
            }
            case CLIENT -> {
                RenderUtil.circle(g, cx, cy - h * 0.38f, s * 0.21f, color);
                RenderUtil.roundedRectSides(g, cx - h * 0.78f, cy + h * 0.12f, s * 0.78f, h * 0.82f, h * 0.4f, color, true, true);
            }
            case SEARCH -> {
                float r = s * 0.3f;
                RenderUtil.roundedOutline(g, cx - h * 0.85f, cy - h * 0.85f, r * 2, r * 2, r, t, color);
                rotatedRect(g, cx + h * 0.45f, cy + h * 0.45f, s * 0.38f, t * 1.1f, 45, color);
            }
            case CLOSE -> {
                rotatedRect(g, cx, cy, s, t, 45, color);
                rotatedRect(g, cx, cy, s, t, -45, color);
            }
            case CHECK -> {
                Matrix3x2fStack pose = g.pose();
                pose.pushMatrix();
                pose.translate(cx - s * 0.12f, cy + s * 0.18f);
                pose.rotate((float) Math.toRadians(-50));
                RenderUtil.rect(g, -t / 2f, -t / 2f, s * 0.85f, t, color);
                pose.popMatrix();
                pose.pushMatrix();
                pose.translate(cx - s * 0.12f, cy + s * 0.18f);
                pose.rotate((float) Math.toRadians(-135));
                RenderUtil.rect(g, -t / 2f, -t / 2f, s * 0.42f, t, color);
                pose.popMatrix();
            }
            case INFO -> {
                RenderUtil.circle(g, cx, cy - h * 0.62f, t * 0.8f, color);
                RenderUtil.rect(g, cx - t / 2f, cy - h * 0.25f, t, s * 0.62f, color);
            }
            case WARNING -> {
                RenderUtil.rect(g, cx - t / 2f, cy - h, t, s * 0.62f, color);
                RenderUtil.circle(g, cx, cy + h * 0.72f, t * 0.8f, color);
            }
            case PLAY -> {
                // right-pointing triangle from thin vertical strips
                int strips = 8;
                float w = s * 0.75f;
                for (int i = 0; i < strips; i++) {
                    float frac = 1f - i / (float) strips;
                    float sh = s * frac;
                    RenderUtil.rect(g, cx - w / 2f + i * w / strips, cy - sh / 2f, w / strips + 0.3f, sh, color);
                }
            }
            case STOP -> RenderUtil.roundedRect(g, cx - h * 0.75f, cy - h * 0.75f, s * 0.75f, s * 0.75f, s * 0.15f, color);
            case CONFIG -> {
                for (int i = -1; i <= 1; i++) {
                    RenderUtil.roundedRect(g, cx - h, cy + i * h * 0.6f - t / 2f, s, t, t / 2f, color);
                }
            }
            case WARDROBE -> {
                // a clothes hanger
                RenderUtil.roundedOutline(g, cx - s * 0.12f, cy - h, s * 0.24f, s * 0.24f, s * 0.12f, t * 0.9f, color);
                rotatedRect(g, cx - h * 0.5f, cy + h * 0.1f, s * 0.62f, t, -32, color);
                rotatedRect(g, cx + h * 0.5f, cy + h * 0.1f, s * 0.62f, t, 32, color);
                RenderUtil.rect(g, cx - h, cy + h * 0.45f, s, t, color);
            }
            case COIN -> {
                RenderUtil.circle(g, cx, cy, h, color);
                RenderUtil.circle(g, cx, cy, h * 0.62f, 0x40000000);
                RenderUtil.rect(g, cx - t / 2f, cy - h * 0.4f, t, h * 0.8f, 0x90FFFFFF);
            }
            case STAR, STAR_FILLED -> {
                // Five-pointed star: outline, plus spokes from the center when filled.
                float outer = h, inner = h * 0.42f;
                float[] px = new float[10], py = new float[10];
                for (int k = 0; k < 10; k++) {
                    double ang = Math.toRadians(-90 + k * 36);
                    float r = k % 2 == 0 ? outer : inner;
                    px[k] = cx + (float) Math.cos(ang) * r;
                    py[k] = cy + 0.06f * s + (float) Math.sin(ang) * r;
                }
                if (icon == Icon.STAR_FILLED) {
                    for (int k = 0; k < 40; k++) {
                        int a = k / 4 % 10, b = (a + 1) % 10;
                        float f = (k % 4) / 4f;
                        line(g, cx, cy + 0.06f * s, px[a] + (px[b] - px[a]) * f, py[a] + (py[b] - py[a]) * f, t * 1.4f, color);
                    }
                }
                for (int k = 0; k < 10; k++) {
                    line(g, px[k], py[k], px[(k + 1) % 10], py[(k + 1) % 10], t * 0.9f, color);
                }
            }
            case LINK -> {
                // Arrow out of a box: "opens in your browser".
                RenderUtil.roundedOutline(g, cx - h, cy - h * 0.7f, s * 0.8f, s * 0.8f, s * 0.12f, t, color);
                line(g, cx - h * 0.1f, cy + h * 0.1f, cx + h, cy - h, t, color);
                line(g, cx + h * 0.25f, cy - h, cx + h, cy - h, t, color);
                line(g, cx + h, cy - h, cx + h, cy - h * 0.25f, t, color);
            }
            case FOLDER -> {
                RenderUtil.roundedOutline(g, cx - h, cy - h * 0.55f, s, s * 0.8f, s * 0.1f, t, color);
                RenderUtil.roundedRect(g, cx - h, cy - h * 0.8f, s * 0.42f, s * 0.2f, s * 0.08f, color);
            }
            case EDIT -> {
                rotatedRect(g, cx + s * 0.05f, cy - s * 0.05f, s * 0.95f, t * 2f, -45, color);
                RenderUtil.circle(g, cx - h * 0.72f, cy + h * 0.72f, t * 0.9f, color);
            }
        }
    }

    /** A straight stroke from (x1, y1) to (x2, y2). */
    private static void line(GuiGraphics g, float x1, float y1, float x2, float y2, float thickness, int color) {
        float len = (float) Math.hypot(x2 - x1, y2 - y1);
        if (len < 0.01f) {
            return;
        }
        rotatedRect(g, (x1 + x2) / 2f, (y1 + y2) / 2f, len + thickness * 0.5f, thickness, (float) Math.toDegrees(Math.atan2(y2 - y1, x2 - x1)), color);
    }

    private static void rotatedRect(GuiGraphics g, float cx, float cy, float length, float thickness, float degrees, int color) {
        Matrix3x2fStack pose = g.pose();
        pose.pushMatrix();
        pose.translate(cx, cy);
        pose.rotate((float) Math.toRadians(degrees));
        RenderUtil.rect(g, -length / 2f, -thickness / 2f, length, thickness, color);
        pose.popMatrix();
    }
}
