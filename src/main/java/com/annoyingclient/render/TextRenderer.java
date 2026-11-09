package com.annoyingclient.render;

import com.annoyingclient.AnnoyingClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Matrix3x2fStack;

/**
 * Text drawing for Annoying Client.
 *
 * <p>Two fonts are available: Minecraft's own font, or the bundled Outfit typeface (SIL OFL), which
 * is registered as a regular Minecraft font resource ({@code assets/annoyingclient/font/ui.json}).
 * Characters Outfit lacks fall back to Minecraft's default font. Which one is used, and whether text
 * gets a drop shadow, is controlled by the theme.
 */
public final class TextRenderer {

    private static final Style CUSTOM = Style.EMPTY.withFont(
            new FontDescription.Resource(Identifier.fromNamespaceAndPath(AnnoyingClient.MOD_ID, "ui")));
    private static final Style CUSTOM_BOLD = Style.EMPTY.withFont(
            new FontDescription.Resource(Identifier.fromNamespaceAndPath(AnnoyingClient.MOD_ID, "ui_bold")));
    private static final Style VANILLA_BOLD = Style.EMPTY.withBold(true);

    private static boolean customFont = true;
    private static boolean shadows = true;

    private TextRenderer() {
    }

    public static void setCustomFont(boolean enabled) {
        customFont = enabled;
    }

    public static boolean isCustomFont() {
        return customFont;
    }

    public static void setShadows(boolean enabled) {
        shadows = enabled;
    }

    private static Font font() {
        return Minecraft.getInstance().font;
    }

    private static FormattedCharSequence seq(String text, boolean bold) {
        if (customFont) {
            return FormattedCharSequence.forward(text, bold ? CUSTOM_BOLD : CUSTOM);
        }
        return FormattedCharSequence.forward(text, bold ? VANILLA_BOLD : Style.EMPTY);
    }

    public static int width(String text) {
        return width(text, false);
    }

    public static int width(String text, boolean bold) {
        if (!customFont && !bold) {
            return font().width(text);
        }
        return font().width(seq(text, bold));
    }

    public static int height() {
        return font().lineHeight;
    }

    public static void draw(GuiGraphics g, String text, float x, float y, int color) {
        draw(g, text, x, y, color, false, shadows);
    }

    public static void drawBold(GuiGraphics g, String text, float x, float y, int color) {
        draw(g, text, x, y, color, true, shadows);
    }

    public static void draw(GuiGraphics g, String text, float x, float y, int color, boolean bold, boolean shadow) {
        color = RenderUtil.a(color);
        if ((color >>> 24) < 4 || text.isEmpty()) {
            return; // the text renderer skips nearly transparent text anyway
        }
        int ix = (int) x, iy = (int) y;
        if (ix == x && iy == y) {
            g.drawString(font(), seq(text, bold), ix, iy, color, shadow);
            return;
        }
        Matrix3x2fStack pose = g.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        g.drawString(font(), seq(text, bold), 0, 0, color, shadow);
        pose.popMatrix();
    }

    public static void drawCentered(GuiGraphics g, String text, float centerX, float y, int color) {
        draw(g, text, centerX - width(text) / 2f, y, color);
    }

    /** Draws text scaled around its top-left corner. */
    public static void drawScaled(GuiGraphics g, String text, float x, float y, float scale, int color, boolean bold) {
        Matrix3x2fStack pose = g.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(scale, scale);
        draw(g, text, 0, 0, color, bold, shadows);
        pose.popMatrix();
    }

    /** Shortens {@code text} with an ellipsis so it fits in {@code maxWidth}. */
    public static String trim(String text, int maxWidth) {
        if (width(text) <= maxWidth) {
            return text;
        }
        String ellipsis = "...";
        int available = maxWidth - width(ellipsis);
        int end = text.length();
        while (end > 0 && width(text.substring(0, end)) > available) {
            end--;
        }
        return text.substring(0, end) + ellipsis;
    }
}
