package com.annoyingclient.hud.element;

import com.annoyingclient.AnnoyingClient;
import com.annoyingclient.hud.HudModule;
import com.annoyingclient.render.RenderUtil;
import com.annoyingclient.render.TextRenderer;
import com.annoyingclient.setting.BooleanSetting;
import com.annoyingclient.util.Animation;
import com.annoyingclient.util.ColorUtil;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphics;

/** WASD, mouse buttons (with CPS) and space bar with animated press highlights. */
public class KeystrokesHud extends HudModule {

    private static final float KEY = 20;
    private static final float GAP = 2;

    private final BooleanSetting showMouse = add(new BooleanSetting("Mouse Buttons", "Show left and right mouse buttons", true));
    private final BooleanSetting showCps = add(new BooleanSetting("Show CPS", "Show clicks per second on the mouse buttons", true));
    private final BooleanSetting showSpace = add(new BooleanSetting("Space Bar", "Show the jump key", true));

    private final Animation[] press = new Animation[7];
    private final String[] cpsText = {"0 CPS", "0 CPS"};
    private final int[] cpsValue = {-1, -1};

    public KeystrokesHud() {
        super("Keystrokes", "Shows your movement keys and mouse clicks", false, ANCHOR_START, ANCHOR_CENTER, 4, 0);
        for (int i = 0; i < press.length; i++) {
            press[i] = new Animation(0f, 18f);
        }
    }

    @Override
    protected boolean defaultBackground() {
        return false; // each key has its own background
    }

    @Override
    protected void renderContent(GuiGraphics g, float partialTick, boolean editing) {
        var o = mc.options;
        float w = KEY * 3 + GAP * 2;
        float y = 0;
        key(g, 0, o.keyUp, "W", KEY + GAP, y, KEY, KEY);
        y += KEY + GAP;
        key(g, 1, o.keyLeft, "A", 0, y, KEY, KEY);
        key(g, 2, o.keyDown, "S", KEY + GAP, y, KEY, KEY);
        key(g, 3, o.keyRight, "D", (KEY + GAP) * 2, y, KEY, KEY);
        y += KEY + GAP;
        if (showMouse.isEnabled()) {
            float mw = (w - GAP) / 2f;
            boolean cps = showCps.isEnabled();
            float mh = cps ? KEY + 4 : KEY;
            mouse(g, 4, o.keyAttack, "LMB", 0, y, mw, mh, cps ? cps(0, AnnoyingClient.cps().getLeft()) : null);
            mouse(g, 5, o.keyUse, "RMB", mw + GAP, y, mw, mh, cps ? cps(1, AnnoyingClient.cps().getRight()) : null);
            y += mh + GAP;
        }
        if (showSpace.isEnabled()) {
            float sh = 11;
            float p = press[6].update();
            press[6].setTarget(o.keyJump.isDown() ? 1f : 0f);
            drawKeyBox(g, 0, y, w, sh, p);
            RenderUtil.roundedRect(g, w / 2f - 12, y + sh / 2f - 0.75f, 24, 1.5f, 0.75f,
                    ColorUtil.lerp(textColor(), 0xFF000000, p * 0.85f));
            y += sh;
        }
        setSize(w, y);
    }

    private String cps(int index, int value) {
        if (cpsValue[index] != value) {
            cpsValue[index] = value;
            cpsText[index] = value + " CPS";
        }
        return cpsText[index];
    }

    private void key(GuiGraphics g, int index, KeyMapping mapping, String label, float x, float y, float w, float h) {
        press[index].setTarget(mapping.isDown() ? 1f : 0f);
        float p = press[index].update();
        drawKeyBox(g, x, y, w, h, p);
        int color = ColorUtil.lerp(textColor(), 0xFF101010, p * 0.85f);
        TextRenderer.draw(g, label, x + w / 2f - TextRenderer.width(label) / 2f, y + h / 2f - 4, color);
    }

    private void mouse(GuiGraphics g, int index, KeyMapping mapping, String label, float x, float y, float w, float h, String sub) {
        press[index].setTarget(mapping.isDown() ? 1f : 0f);
        float p = press[index].update();
        drawKeyBox(g, x, y, w, h, p);
        int color = ColorUtil.lerp(textColor(), 0xFF101010, p * 0.85f);
        float ty = sub == null ? y + h / 2f - 4 : y + 3;
        TextRenderer.draw(g, label, x + w / 2f - TextRenderer.width(label) / 2f, ty, color);
        if (sub != null) {
            TextRenderer.drawScaled(g, sub, x + w / 2f - TextRenderer.width(sub) * 0.35f, y + 13.5f, 0.7f,
                    ColorUtil.lerp(mutedColor(), 0xFF202020, p), false);
        }
    }

    private void drawKeyBox(GuiGraphics g, float x, float y, float w, float h, float pressed) {
        var colors = hudColors();
        float opacity = colors != null ? colors.backgroundOpacity() : 0.55f;
        int idle = ColorUtil.withAlpha(AnnoyingClient.theme().background(), Math.round(opacity * 255));
        int active = accentColor((long) (x * 10));
        RenderUtil.roundedRect(g, x, y, w, h, 3, ColorUtil.lerp(idle, active, pressed));
    }
}
