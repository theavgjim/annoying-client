package com.annoyingclient.render;

import com.annoyingclient.util.ColorUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fStack;

/**
 * Shape drawing on top of {@link GuiGraphics}.
 *
 * <p>All methods take float coordinates in the caller's current GUI space. Internally each shape is
 * drawn in physical-pixel space (the pose is temporarily scaled down), so rounded corners and
 * animated positions stay crisp and sub-pixel motion is visible at every GUI scale.
 * Nothing here allocates per call.
 */
public final class RenderUtil {

    private static final int QUARTER = Textures.CIRCLE_SIZE / 2;

    /** Multiplies the alpha of everything drawn through RenderUtil and TextRenderer (used for whole-window fades). */
    private static float globalAlpha = 1f;

    private RenderUtil() {
    }

    public static void setGlobalAlpha(float alpha) {
        globalAlpha = Math.max(0f, Math.min(1f, alpha));
    }

    public static float getGlobalAlpha() {
        return globalAlpha;
    }

    /** Applies the global alpha to a color. */
    public static int a(int color) {
        return globalAlpha >= 1f ? color : ColorUtil.fade(color, globalAlpha);
    }

    /** Physical pixels per unit of the current pose, including GUI scale and any pose scaling. */
    private static float pixelScale(GuiGraphics g) {
        Matrix3x2f m = g.pose();
        float poseScale = (float) Math.sqrt(m.m00 * m.m00 + m.m01 * m.m01);
        return Minecraft.getInstance().getWindow().getGuiScale() * (poseScale == 0 ? 1 : poseScale);
    }

    private static float begin(GuiGraphics g) {
        float s = pixelScale(g);
        Matrix3x2fStack pose = g.pose();
        pose.pushMatrix();
        pose.scale(1f / s, 1f / s);
        return s;
    }

    private static void end(GuiGraphics g) {
        g.pose().popMatrix();
    }

    public static void rect(GuiGraphics g, float x, float y, float w, float h, int color) {
        rectRaw(g, x, y, w, h, a(color));
    }

    private static void rectRaw(GuiGraphics g, float x, float y, float w, float h, int color) {
        if (ColorUtil.alpha(color) == 0 || w <= 0 || h <= 0) {
            return;
        }
        float s = begin(g);
        int x1 = Math.round(x * s), y1 = Math.round(y * s);
        int x2 = Math.round((x + w) * s), y2 = Math.round((y + h) * s);
        g.fill(x1, y1, x2, y2, color);
        end(g);
    }

    public static void roundedRect(GuiGraphics g, float x, float y, float w, float h, float radius, int color) {
        color = a(color);
        if (ColorUtil.alpha(color) == 0 || w <= 0 || h <= 0) {
            return;
        }
        if (!Textures.isReady() || radius <= 0) {
            rectRaw(g, x, y, w, h, color);
            return;
        }
        float s = begin(g);
        int x1 = Math.round(x * s), y1 = Math.round(y * s);
        int x2 = Math.round((x + w) * s), y2 = Math.round((y + h) * s);
        int pw = x2 - x1, ph = y2 - y1;
        int r = Math.min(Math.round(radius * s), Math.min(pw, ph) / 2);
        if (r <= 0) {
            g.fill(x1, y1, x2, y2, color);
        } else {
            g.fill(x1 + r, y1, x2 - r, y2, color);
            g.fill(x1, y1 + r, x1 + r, y2 - r, color);
            g.fill(x2 - r, y1 + r, x2, y2 - r, color);
            corner(g, x1, y1, r, 0, 0, color);
            corner(g, x2 - r, y1, r, QUARTER, 0, color);
            corner(g, x1, y2 - r, r, 0, QUARTER, color);
            corner(g, x2 - r, y2 - r, r, QUARTER, QUARTER, color);
        }
        end(g);
    }

    /** A rounded rectangle with only the left or only the right corners rounded (used for tabs and bars). */
    public static void roundedRectSides(GuiGraphics g, float x, float y, float w, float h, float radius, int color,
                                        boolean roundLeft, boolean roundRight) {
        color = a(color);
        if (ColorUtil.alpha(color) == 0 || w <= 0 || h <= 0) {
            return;
        }
        if (!Textures.isReady()) {
            rectRaw(g, x, y, w, h, color);
            return;
        }
        float s = begin(g);
        int x1 = Math.round(x * s), y1 = Math.round(y * s);
        int x2 = Math.round((x + w) * s), y2 = Math.round((y + h) * s);
        int r = Math.min(Math.round(radius * s), Math.min(x2 - x1, y2 - y1) / 2);
        int left = roundLeft ? r : 0;
        int right = roundRight ? r : 0;
        g.fill(x1 + left, y1, x2 - right, y2, color);
        if (left > 0) {
            g.fill(x1, y1 + r, x1 + r, y2 - r, color);
            corner(g, x1, y1, r, 0, 0, color);
            corner(g, x1, y2 - r, r, 0, QUARTER, color);
        }
        if (right > 0) {
            g.fill(x2 - r, y1 + r, x2, y2 - r, color);
            corner(g, x2 - r, y1, r, QUARTER, 0, color);
            corner(g, x2 - r, y2 - r, r, QUARTER, QUARTER, color);
        }
        end(g);
    }

    public static void circle(GuiGraphics g, float cx, float cy, float radius, int color) {
        circleRaw(g, cx, cy, radius, a(color));
    }

    private static void circleRaw(GuiGraphics g, float cx, float cy, float radius, int color) {
        if (ColorUtil.alpha(color) == 0 || radius <= 0) {
            return;
        }
        if (!Textures.isReady()) {
            rectRaw(g, cx - radius, cy - radius, radius * 2, radius * 2, color);
            return;
        }
        float s = begin(g);
        int x1 = Math.round((cx - radius) * s), y1 = Math.round((cy - radius) * s);
        int d = Math.max(1, Math.round(radius * 2 * s));
        g.blit(RenderPipelines.GUI_TEXTURED, Textures.CIRCLE, x1, y1, 0, 0, d, d,
                Textures.CIRCLE_SIZE, Textures.CIRCLE_SIZE, Textures.CIRCLE_SIZE, Textures.CIRCLE_SIZE, color);
        end(g);
    }

    /** Rounded outline drawn as a ring: an outer rounded rect minus the inner area (drawn as 4 bands, so it works on translucent backgrounds). */
    public static void roundedOutline(GuiGraphics g, float x, float y, float w, float h, float radius, float thickness, int color) {
        color = a(color);
        if (ColorUtil.alpha(color) == 0) {
            return;
        }
        // Straight edges.
        rectRaw(g, x + radius, y, w - radius * 2, thickness, color);
        rectRaw(g, x + radius, y + h - thickness, w - radius * 2, thickness, color);
        rectRaw(g, x, y + radius, thickness, h - radius * 2, color);
        rectRaw(g, x + w - thickness, y + radius, thickness, h - radius * 2, color);
        // Corners: approximate the arc with short segments.
        int segments = 6;
        for (int corner = 0; corner < 4; corner++) {
            float cx = corner % 2 == 0 ? x + radius : x + w - radius;
            float cy = corner < 2 ? y + radius : y + h - radius;
            float base = switch (corner) {
                case 0 -> (float) Math.PI;
                case 1 -> (float) (Math.PI * 1.5);
                case 2 -> (float) (Math.PI * 0.5);
                default -> 0f;
            };
            for (int i = 0; i <= segments; i++) {
                float a = base + (float) (Math.PI / 2) * i / segments;
                float px = cx + (float) Math.cos(a) * (radius - thickness / 2);
                float py = cy + (float) Math.sin(a) * (radius - thickness / 2);
                circleRaw(g, px, py, thickness / 2f + 0.15f, color);
            }
        }
    }

    /** A soft drop shadow around the rectangle (x, y, w, h). */
    public static void shadow(GuiGraphics g, float x, float y, float w, float h, float spread, int color) {
        color = a(color);
        if (ColorUtil.alpha(color) == 0 || !Textures.isReady() || spread <= 0) {
            return;
        }
        float s = begin(g);
        int c = Math.max(1, Math.round(spread * 2 * s));
        int x1 = Math.round((x - spread) * s), y1 = Math.round((y - spread) * s);
        int x2 = Math.round((x + w + spread) * s), y2 = Math.round((y + h + spread) * s);
        int midW = Math.max(0, x2 - x1 - c * 2);
        int midH = Math.max(0, y2 - y1 - c * 2);
        int t = Textures.SHADOW_SIZE;
        int k = Textures.SHADOW_CORNER;
        int mid = t - k * 2;
        var id = Textures.SHADOW;
        var p = RenderPipelines.GUI_TEXTURED;
        // corners
        g.blit(p, id, x1, y1, 0, 0, c, c, k, k, t, t, color);
        g.blit(p, id, x2 - c, y1, t - k, 0, c, c, k, k, t, t, color);
        g.blit(p, id, x1, y2 - c, 0, t - k, c, c, k, k, t, t, color);
        g.blit(p, id, x2 - c, y2 - c, t - k, t - k, c, c, k, k, t, t, color);
        // edges
        if (midW > 0) {
            g.blit(p, id, x1 + c, y1, k, 0, midW, c, mid, k, t, t, color);
            g.blit(p, id, x1 + c, y2 - c, k, t - k, midW, c, mid, k, t, t, color);
        }
        if (midH > 0) {
            g.blit(p, id, x1, y1 + c, 0, k, c, midH, k, mid, t, t, color);
            g.blit(p, id, x2 - c, y1 + c, t - k, k, c, midH, k, mid, t, t, color);
        }
        if (midW > 0 && midH > 0) {
            g.fill(x1 + c, y1 + c, x2 - c, y2 - c, color);
        }
        end(g);
    }

    public static void verticalGradient(GuiGraphics g, float x, float y, float w, float h, int top, int bottom) {
        top = a(top);
        bottom = a(bottom);
        float s = begin(g);
        g.fillGradient(Math.round(x * s), Math.round(y * s), Math.round((x + w) * s), Math.round((y + h) * s), top, bottom);
        end(g);
    }

    /** Left-to-right gradient, drawn as a rotated vertical gradient (one quad, no per-column fills). */
    public static void horizontalGradient(GuiGraphics g, float x, float y, float w, float h, int left, int right) {
        left = a(left);
        right = a(right);
        if (w <= 0 || h <= 0) {
            return;
        }
        float s = begin(g);
        int px = Math.round(x * s), py = Math.round(y * s);
        int pw = Math.round((x + w) * s) - px, ph = Math.round((y + h) * s) - py;
        Matrix3x2fStack pose = g.pose();
        pose.pushMatrix();
        pose.translate(px, py + ph);
        pose.rotate((float) (-Math.PI / 2));
        g.fillGradient(0, 0, ph, pw, left, right);
        pose.popMatrix();
        end(g);
    }

    /** Scissor in the caller's GUI space; GuiGraphics applies the current pose to it. */
    public static void scissor(GuiGraphics g, float x, float y, float w, float h) {
        g.enableScissor((int) Math.floor(x), (int) Math.floor(y), (int) Math.ceil(x + w), (int) Math.ceil(y + h));
    }

    public static void endScissor(GuiGraphics g) {
        g.disableScissor();
    }

    private static void corner(GuiGraphics g, int x, int y, int r, int u, int v, int color) {
        g.blit(RenderPipelines.GUI_TEXTURED, Textures.CIRCLE, x, y, u, v, r, r, QUARTER, QUARTER,
                Textures.CIRCLE_SIZE, Textures.CIRCLE_SIZE, color);
    }
}
