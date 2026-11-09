package com.annoyingclient.hud;

import com.annoyingclient.AnnoyingClient;
import com.annoyingclient.module.Category;
import com.annoyingclient.module.Module;
import com.annoyingclient.module.impl.visual.HudColorsModule;
import com.annoyingclient.render.RenderUtil;
import com.annoyingclient.setting.BooleanSetting;
import com.annoyingclient.setting.ColorSetting;
import com.annoyingclient.setting.KeybindSetting;
import com.annoyingclient.setting.NumberSetting;
import com.annoyingclient.util.ColorUtil;
import net.minecraft.client.gui.GuiGraphics;
import org.joml.Matrix3x2fStack;

/**
 * A module that draws an element on the HUD.
 *
 * <p>Position is stored as an anchor (left/center/right and top/middle/bottom, picked from where the
 * element was dropped) plus an offset from that anchor, so elements stay attached to their corner or
 * edge at every resolution and GUI scale. Subclasses draw at (0, 0) in {@link #renderContent} and
 * report their unscaled size with {@link #setSize}.
 */
public abstract class HudModule extends Module {

    public static final int ANCHOR_START = 0;
    public static final int ANCHOR_CENTER = 1;
    public static final int ANCHOR_END = 2;

    private final NumberSetting anchorX;
    private final NumberSetting anchorY;
    private final NumberSetting offsetX;
    private final NumberSetting offsetY;
    protected final NumberSetting scale;
    protected final BooleanSetting background;
    protected final BooleanSetting customColor;
    protected final ColorSetting color;

    private float width = 40;
    private float height = 12;
    private boolean sized;

    protected HudModule(String name, String description, boolean enabledByDefault,
                        int defAnchorX, int defAnchorY, float defOffsetX, float defOffsetY) {
        super(name, description, Category.HUD, enabledByDefault, KeybindSetting.NONE);
        anchorX = add(new NumberSetting("Anchor X", "", defAnchorX, 0, 2, 1).hide());
        anchorY = add(new NumberSetting("Anchor Y", "", defAnchorY, 0, 2, 1).hide());
        offsetX = add(new NumberSetting("Offset X", "", defOffsetX, -4000, 4000, 0.5).hide());
        offsetY = add(new NumberSetting("Offset Y", "", defOffsetY, -4000, 4000, 0.5).hide());
        scale = add(new NumberSetting("Scale", "Size of this element", 1.0, 0.5, 3.0, 0.05).withSuffix("x"));
        background = add(new BooleanSetting("Background", "Draw a panel behind this element", defaultBackground()));
        customColor = add(new BooleanSetting("Custom Color", "Use a color for this element instead of the HUD color", false));
        color = add(new ColorSetting("Color", "Accent color for this element", 0xFF8B5CF6, false));
        color.visibleWhen(customColor::isEnabled);
    }

    /** Default for the Background setting; elements that draw their own boxes return false. */
    protected boolean defaultBackground() {
        return true;
    }

    /** Draws the element at (0, 0) in its own unscaled space and must call {@link #setSize}. */
    protected abstract void renderContent(GuiGraphics g, float partialTick, boolean editing);

    /** Whether there is anything to draw (e.g. armor HUD with no armor). The editor still shows a placeholder. */
    protected boolean hasContent() {
        return true;
    }

    public final void render(GuiGraphics g, float partialTick, boolean editing) {
        if (!editing && !hasContent()) {
            return;
        }
        float s = scale.getFloat();
        Matrix3x2fStack pose = g.pose();
        pose.pushMatrix();
        pose.translate(getScreenX(g.guiWidth()), getScreenY(g.guiHeight()));
        pose.scale(s, s);
        try {
            renderContent(g, partialTick, editing);
        } catch (RuntimeException e) {
            // A world/player state we did not anticipate must never crash the HUD; log once per module.
            if (!loggedError) {
                loggedError = true;
                AnnoyingClient.LOGGER.error("HUD element {} failed to render", getName(), e);
            }
        }
        pose.popMatrix();
    }

    private boolean loggedError;

    protected final void setSize(float w, float h) {
        width = Math.max(1, w);
        height = Math.max(1, h);
        sized = true;
    }

    public float getScaledWidth() {
        return width * scale.getFloat();
    }

    public float getScaledHeight() {
        return height * scale.getFloat();
    }

    public boolean isSized() {
        return sized;
    }

    public float getScreenX(int screenWidth) {
        float w = getScaledWidth();
        float off = offsetX.getFloat();
        float x = switch (anchorX.getInt()) {
            case ANCHOR_CENTER -> screenWidth / 2f - w / 2f + off;
            case ANCHOR_END -> screenWidth - w - off;
            default -> off;
        };
        return Math.max(0, Math.min(screenWidth - w, x));
    }

    public float getScreenY(int screenHeight) {
        float h = getScaledHeight();
        float off = offsetY.getFloat();
        float y = switch (anchorY.getInt()) {
            case ANCHOR_CENTER -> screenHeight / 2f - h / 2f + off;
            case ANCHOR_END -> screenHeight - h - off;
            default -> off;
        };
        return Math.max(0, Math.min(screenHeight - h, y));
    }

    /** Stores a new top-left position, choosing the anchor from which third of the screen the element is in. */
    public void setScreenPosition(float x, float y, int screenWidth, int screenHeight) {
        float w = getScaledWidth();
        float h = getScaledHeight();
        x = Math.max(0, Math.min(screenWidth - w, x));
        y = Math.max(0, Math.min(screenHeight - h, y));
        float cx = x + w / 2f;
        float cy = y + h / 2f;
        int ax = cx < screenWidth / 3f ? ANCHOR_START : cx > screenWidth * 2f / 3f ? ANCHOR_END : ANCHOR_CENTER;
        int ay = cy < screenHeight / 3f ? ANCHOR_START : cy > screenHeight * 2f / 3f ? ANCHOR_END : ANCHOR_CENTER;
        anchorX.setValue((double) ax);
        anchorY.setValue((double) ay);
        offsetX.setValue((double) switch (ax) {
            case ANCHOR_CENTER -> x - (screenWidth / 2f - w / 2f);
            case ANCHOR_END -> screenWidth - w - x;
            default -> x;
        });
        offsetY.setValue((double) switch (ay) {
            case ANCHOR_CENTER -> y - (screenHeight / 2f - h / 2f);
            case ANCHOR_END -> screenHeight - h - y;
            default -> y;
        });
    }

    public void resetPosition() {
        anchorX.reset();
        anchorY.reset();
        offsetX.reset();
        offsetY.reset();
        scale.reset();
    }

    public NumberSetting getScaleSetting() {
        return scale;
    }

    /** True when the element sits on the right side of the screen, so text can align to the right edge. */
    protected boolean isRightSide() {
        return anchorX.getInt() == ANCHOR_END;
    }

    // ---- shared styling ----------------------------------------------------------------------

    protected static HudColorsModule hudColors() {
        return AnnoyingClient.hudColors();
    }

    /** Accent color for labels/bars. {@code offset} lets lists form a moving gradient. */
    protected int accentColor(long offset) {
        if (customColor.isEnabled()) {
            return color.getColor(offset);
        }
        HudColorsModule colors = hudColors();
        return colors != null ? colors.color(offset) : AnnoyingClient.theme().accent(offset);
    }

    protected int textColor() {
        return AnnoyingClient.theme().text();
    }

    protected int mutedColor() {
        return AnnoyingClient.theme().textMuted();
    }

    protected void drawBackground(GuiGraphics g, float w, float h) {
        if (!background.isEnabled()) {
            return;
        }
        HudColorsModule colors = hudColors();
        float opacity = colors != null ? colors.backgroundOpacity() : 0.55f;
        float radius = colors == null || colors.roundedCorners() ? 3.5f : 0f;
        int bg = ColorUtil.withAlpha(AnnoyingClient.theme().background(), Math.round(opacity * 255));
        RenderUtil.roundedRect(g, 0, 0, w, h, radius, bg);
    }
}
