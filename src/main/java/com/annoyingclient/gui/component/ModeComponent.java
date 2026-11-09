package com.annoyingclient.gui.component;

import com.annoyingclient.render.RenderUtil;
import com.annoyingclient.render.TextRenderer;
import com.annoyingclient.setting.ModeSetting;
import com.annoyingclient.util.Animation;
import com.annoyingclient.util.ColorUtil;
import com.annoyingclient.util.MathUtil;
import net.minecraft.client.gui.GuiGraphics;
import org.lwjgl.glfw.GLFW;

/** Dropdown: left-click opens the option list; right-click cycles backwards without opening. */
public class ModeComponent extends SettingComponent<ModeSetting> {

    private static final float ROW = 18;
    private static final float OPTION = 13;

    private boolean open;
    private final Animation openAnim = new Animation(0f, 16f);

    public ModeComponent(ModeSetting setting, String label) {
        super(setting, label);
    }

    @Override
    public float getHeight() {
        return ROW + openAnim.get() * (setting.getModes().size() * OPTION + 3);
    }

    private float boxW() {
        int max = 0;
        for (String m : setting.getModes()) {
            max = Math.max(max, TextRenderer.width(m));
        }
        return Math.min(width * 0.55f, max + 22);
    }

    @Override
    public void render(GuiGraphics g, float mouseX, float mouseY) {
        openAnim.setTarget(open ? 1f : 0f);
        float o = openAnim.update();
        var t = theme();
        float bw = boxW();
        float bx = x + width - bw;
        boolean boxHover = MathUtil.inside(mouseX, mouseY, bx, y + 2, bw, ROW - 4);
        hover.setTarget(boxHover ? 1f : 0f);
        float h = hover.update();

        TextRenderer.draw(g, label, x, y + 5, t.text());
        RenderUtil.roundedRect(g, bx, y + 2, bw, ROW - 4, 4, ColorUtil.shade(t.control(), h * 0.07f));
        TextRenderer.draw(g, TextRenderer.trim(setting.getValue(), (int) bw - 18), bx + 6, y + 5, t.text());
        Widgets.chevron(g, bx + bw - 7, y + ROW / 2f, o, t.textMuted());

        if (o > 0.01f) {
            float listY = y + ROW;
            float listH = (getHeight() - ROW);
            RenderUtil.scissor(g, bx, listY, bw, listH);
            RenderUtil.roundedRect(g, bx, listY, bw, setting.getModes().size() * OPTION + 2, 4, t.control());
            for (int i = 0; i < setting.getModes().size(); i++) {
                String mode = setting.getModes().get(i);
                float oy = listY + 1 + i * OPTION;
                boolean selected = setting.is(mode);
                boolean hovered = MathUtil.inside(mouseX, mouseY, bx, oy, bw, OPTION);
                if (hovered || selected) {
                    RenderUtil.roundedRect(g, bx + 2, oy, bw - 4, OPTION, 3,
                            selected ? ColorUtil.withAlpha(t.accent(), 70) : ColorUtil.withAlpha(t.text(), 18));
                }
                TextRenderer.draw(g, mode, bx + 6, oy + 3, selected ? t.accent() : t.text());
            }
            RenderUtil.endScissor(g);
        }
    }

    @Override
    public boolean mouseClicked(float mouseX, float mouseY, int button) {
        float bw = boxW();
        float bx = x + width - bw;
        if (MathUtil.inside(mouseX, mouseY, x, y, width, ROW)) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                open = !open;
            } else if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                setting.cycle(false);
            }
            return true;
        }
        if (open && openAnim.get() > 0.9f) {
            for (int i = 0; i < setting.getModes().size(); i++) {
                float oy = y + ROW + 1 + i * OPTION;
                if (MathUtil.inside(mouseX, mouseY, bx, oy, bw, OPTION)) {
                    setting.setValue(setting.getModes().get(i));
                    open = false;
                    return true;
                }
            }
        }
        if (open) {
            open = false;
        }
        return false;
    }

    @Override
    public void unfocus() {
        open = false;
    }
}
