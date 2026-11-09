package com.annoyingclient.gui.component;

import com.annoyingclient.render.RenderUtil;
import com.annoyingclient.render.TextRenderer;
import com.annoyingclient.setting.ColorSetting;
import com.annoyingclient.util.Animation;
import com.annoyingclient.util.ColorUtil;
import com.annoyingclient.util.MathUtil;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.input.KeyEvent;
import org.lwjgl.glfw.GLFW;

/**
 * Color editor with an inline HSV picker: saturation/value square, hue bar, optional alpha bar,
 * rainbow toggle and a hex input. Click the swatch to expand; right-click to reset.
 */
public class ColorComponent extends SettingComponent<ColorSetting> {

    private static final float ROW = 18;
    private static final float PICKER_H = 64;
    private static final float FOOTER_H = 18;
    private static final int[] HUES = {0xFFFF0000, 0xFFFFFF00, 0xFF00FF00, 0xFF00FFFF, 0xFF0000FF, 0xFFFF00FF, 0xFFFF0000};

    private boolean open;
    private final Animation openAnim = new Animation(0f, 16f);
    private final Animation rainbowAnim;
    private final float[] hsv = new float[3];
    private int alpha;
    private int syncedValue;
    private int dragging = -1; // 0 = square, 1 = hue, 2 = alpha
    private final TextField hexField;

    public ColorComponent(ColorSetting setting, String label) {
        super(setting, label);
        rainbowAnim = new Animation(setting.isRainbow() ? 1f : 0f, 16f);
        hexField = new TextField("#RRGGBB", setting.allowsAlpha() ? 9 : 7, 14)
                .filter(c -> c == '#' || Character.digit(c, 16) >= 0);
        hexField.onSubmit(this::applyHex);
        sync(true);
    }

    private void sync(boolean force) {
        int v = setting.getValue();
        if (force || v != syncedValue) {
            syncedValue = v;
            float[] tmp = new float[3];
            ColorUtil.rgbToHsv(v, tmp);
            // keep hue when the color is grey, so the hue bar does not jump to red
            if (tmp[1] > 0.001f && tmp[2] > 0.001f) {
                hsv[0] = tmp[0];
            }
            hsv[1] = tmp[1];
            hsv[2] = tmp[2];
            alpha = ColorUtil.alpha(v);
            if (!hexField.isFocused()) {
                hexField.setText(ColorUtil.toHex(v, setting.allowsAlpha()));
            }
        }
    }

    private void push() {
        int rgb = ColorUtil.hsvToRgb(hsv[0], hsv[1], hsv[2]);
        int v = ColorUtil.withAlpha(rgb, setting.allowsAlpha() ? alpha : 255);
        syncedValue = v;
        setting.setValue(v);
        if (!hexField.isFocused()) {
            hexField.setText(ColorUtil.toHex(v, setting.allowsAlpha()));
        }
    }

    private void applyHex() {
        Integer parsed = ColorUtil.parseHex(hexField.getText());
        if (parsed != null) {
            if (!setting.allowsAlpha() || hexField.getText().replace("#", "").length() <= 6) {
                parsed = ColorUtil.withAlpha(parsed, setting.allowsAlpha() ? ColorUtil.alpha(setting.getValue()) : 255);
            }
            setting.setValue(parsed);
            sync(true);
        }
        hexField.setFocused(false);
        hexField.setText(ColorUtil.toHex(setting.getValue(), setting.allowsAlpha()));
    }

    @Override
    public float getHeight() {
        return ROW + openAnim.get() * (PICKER_H + FOOTER_H + 8);
    }

    // picker geometry (relative to current bounds)
    private float sqX() {
        return x;
    }

    private float sqY() {
        return y + ROW + 4;
    }

    private float barW() {
        return 8;
    }

    private float sqW() {
        return width - (barW() + 6) * (setting.allowsAlpha() ? 2 : 1);
    }

    private float hueX() {
        return sqX() + sqW() + 6;
    }

    private float alphaX() {
        return hueX() + barW() + 6;
    }

    @Override
    public void render(GuiGraphics g, float mouseX, float mouseY) {
        if (dragging < 0) {
            sync(false);
        }
        if (dragging >= 0) {
            drag(mouseX, mouseY);
        }
        openAnim.setTarget(open ? 1f : 0f);
        float o = openAnim.update();
        var t = theme();
        float h = updateHover(mouseX, mouseY);

        TextRenderer.draw(g, label, x, y + 5, t.text());
        String hex = setting.isRainbow() ? "Rainbow" : ColorUtil.toHex(setting.getValue(), false);
        float sw = 16;
        float sx = x + width - sw;
        RenderUtil.roundedRect(g, sx - 0.5f, y + 2.5f, sw + 1, 13, 4, ColorUtil.shade(t.control(), 0.1f + h * 0.1f));
        RenderUtil.roundedRect(g, sx + 1, y + 4, sw - 2, 10, 3, ColorUtil.withAlpha(setting.getColor(), 255));
        TextRenderer.draw(g, hex, sx - 5 - TextRenderer.width(hex), y + 5, t.textMuted());

        if (o < 0.01f) {
            return;
        }
        RenderUtil.scissor(g, x - 2, y + ROW, width + 4, getHeight() - ROW);
        float pickerH = PICKER_H;
        // saturation/value square
        int hueColor = ColorUtil.hsvToRgb(hsv[0], 1f, 1f);
        RenderUtil.roundedRect(g, sqX(), sqY(), sqW(), pickerH, 3, hueColor);
        RenderUtil.horizontalGradient(g, sqX(), sqY(), sqW(), pickerH, 0xFFFFFFFF, 0x00FFFFFF);
        RenderUtil.verticalGradient(g, sqX(), sqY(), sqW(), pickerH, 0x00000000, 0xFF000000);
        float mx = sqX() + hsv[1] * sqW();
        float my = sqY() + (1 - hsv[2]) * pickerH;
        RenderUtil.circle(g, mx, my, 3.5f, 0xFFFFFFFF);
        RenderUtil.circle(g, mx, my, 2.5f, ColorUtil.withAlpha(setting.getValue(), 255));

        // hue bar
        float seg = pickerH / 6f;
        for (int i = 0; i < 6; i++) {
            RenderUtil.verticalGradient(g, hueX(), sqY() + seg * i, barW(), seg + 0.5f, HUES[i], HUES[i + 1]);
        }
        float hy = sqY() + hsv[0] * pickerH;
        RenderUtil.roundedRect(g, hueX() - 1, hy - 1.5f, barW() + 2, 3, 1.5f, 0xFFFFFFFF);

        // alpha bar
        if (setting.allowsAlpha()) {
            for (int i = 0; i < (int) (pickerH / 4); i++) {
                RenderUtil.rect(g, alphaX() + (i % 2 == 0 ? 0 : 4), sqY() + i * 4, 4, 4, 0xFF9A9A9A);
                RenderUtil.rect(g, alphaX() + (i % 2 == 0 ? 4 : 0), sqY() + i * 4, 4, 4, 0xFF5A5A5A);
            }
            int opaque = ColorUtil.withAlpha(setting.getValue(), 255);
            RenderUtil.verticalGradient(g, alphaX(), sqY(), barW(), pickerH, opaque, ColorUtil.withAlpha(opaque, 0));
            float ay = sqY() + (1 - alpha / 255f) * pickerH;
            RenderUtil.roundedRect(g, alphaX() - 1, ay - 1.5f, barW() + 2, 3, 1.5f, 0xFFFFFFFF);
        }

        // footer: rainbow switch + hex field
        float fy = sqY() + pickerH + 5;
        rainbowAnim.setTarget(setting.isRainbow() ? 1f : 0f);
        Widgets.toggleSwitch(g, x, fy + 1.5f, rainbowAnim.update(), 0);
        TextRenderer.draw(g, "Rainbow", x + Widgets.SWITCH_W + 4, fy + 3, t.textMuted());
        float fw = Math.min(70, width * 0.45f);
        hexField.setBounds(x + width - fw, fy, fw);
        hexField.render(g, mouseX, mouseY);
        RenderUtil.endScissor(g);
    }

    private void drag(float mouseX, float mouseY) {
        float rel = MathUtil.clamp((mouseY - sqY()) / PICKER_H, 0f, 1f);
        switch (dragging) {
            case 0 -> {
                hsv[1] = MathUtil.clamp((mouseX - sqX()) / sqW(), 0f, 1f);
                hsv[2] = 1f - rel;
            }
            case 1 -> hsv[0] = Math.min(0.9999f, rel);
            case 2 -> alpha = Math.round((1f - rel) * 255);
            default -> {
                return;
            }
        }
        if (setting.isRainbow() && dragging != 2) {
            setting.setRainbow(false);
        }
        push();
    }

    @Override
    public boolean mouseClicked(float mouseX, float mouseY, int button) {
        if (MathUtil.inside(mouseX, mouseY, x, y, width, ROW)) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                open = !open;
                if (!open) {
                    hexField.unfocus();
                }
            } else if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                setting.reset();
                sync(true);
            }
            return true;
        }
        if (!open || openAnim.get() < 0.9f || button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            hexField.mouseClicked(mouseX, mouseY, button);
            return false;
        }
        if (MathUtil.inside(mouseX, mouseY, sqX(), sqY(), sqW(), PICKER_H)) {
            dragging = 0;
        } else if (MathUtil.inside(mouseX, mouseY, hueX() - 1, sqY(), barW() + 2, PICKER_H)) {
            dragging = 1;
        } else if (setting.allowsAlpha() && MathUtil.inside(mouseX, mouseY, alphaX() - 1, sqY(), barW() + 2, PICKER_H)) {
            dragging = 2;
        }
        if (dragging >= 0) {
            drag(mouseX, mouseY);
            hexField.unfocus();
            return true;
        }
        float fy = sqY() + PICKER_H + 5;
        if (MathUtil.inside(mouseX, mouseY, x, fy, Widgets.SWITCH_W + 40, 14)) {
            setting.setRainbow(!setting.isRainbow());
            return true;
        }
        return hexField.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void mouseReleased(float mouseX, float mouseY, int button) {
        dragging = -1;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        boolean wasFocused = hexField.isFocused();
        boolean handled = hexField.keyPressed(event);
        if (wasFocused && !hexField.isFocused()) {
            applyHex();
        }
        return handled;
    }

    @Override
    public boolean charTyped(int codepoint) {
        return hexField.charTyped(codepoint);
    }

    @Override
    public boolean isCapturingInput() {
        return hexField.isCapturingInput();
    }

    @Override
    public void unfocus() {
        if (hexField.isFocused()) {
            applyHex();
        }
    }
}
