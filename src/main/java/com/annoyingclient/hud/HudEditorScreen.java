package com.annoyingclient.hud;

import com.annoyingclient.AnnoyingClient;
import com.annoyingclient.gui.clickgui.ClickGuiScreen;
import com.annoyingclient.gui.component.Widgets;
import com.annoyingclient.module.impl.utility.HudEditorModule;
import com.annoyingclient.render.RenderUtil;
import com.annoyingclient.render.TextRenderer;
import com.annoyingclient.theme.ThemeManager;
import com.annoyingclient.util.Animation;
import com.annoyingclient.util.ColorUtil;
import com.annoyingclient.util.MathUtil;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Drag-and-drop HUD editor.
 *
 * <ul>
 *   <li>Drag an element to move it (snaps to screen edges, the center and other elements).</li>
 *   <li>Scroll over an element to scale it; right-click to reset it.</li>
 *   <li>The Elements panel turns elements on and off.</li>
 * </ul>
 * Positions are written to the module settings immediately and saved with the profile.
 */
public class HudEditorScreen extends Screen {

    private static final float BAR_H = 22;
    private static final String[] BUTTONS = {"Elements", "Reset All", "ClickGUI", "Done"};

    private final Screen parent;
    private HudModule dragging;
    private float dragOffsetX;
    private float dragOffsetY;
    private HudModule selected;
    private static boolean panelOpen;
    private boolean confirmReset;
    private final Animation panelAnim = new Animation(panelOpen ? 1f : 0f, 16f);
    private final Animation fade = new Animation(0f, 14f);
    private final Animation[] buttonHover = new Animation[BUTTONS.length];
    private final List<Animation> rowHover = new ArrayList<>();
    private final List<Animation> rowToggle = new ArrayList<>();
    private float guideX = Float.NaN;
    private float guideY = Float.NaN;

    public HudEditorScreen(Screen parent) {
        super(Component.literal("Annoying Client HUD Editor"));
        this.parent = parent;
        for (int i = 0; i < buttonHover.length; i++) {
            buttonHover[i] = new Animation(0f, 16f);
        }
        for (HudModule m : AnnoyingClient.hud().getElements()) {
            rowHover.add(new Animation(0f, 16f));
            rowToggle.add(new Animation(m.isEnabled() ? 1f : 0f, 16f));
        }
        fade.setTarget(1f);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    private HudEditorModule settings() {
        return AnnoyingClient.modules().get(HudEditorModule.class);
    }

    private List<HudModule> elements() {
        return AnnoyingClient.hud().getElements();
    }

    private HudModule elementAt(double mx, double my) {
        List<HudModule> list = elements();
        for (int i = list.size() - 1; i >= 0; i--) {
            HudModule m = list.get(i);
            if (m.isEnabled() && MathUtil.inside(mx, my, m.getScreenX(width), m.getScreenY(height), m.getScaledWidth(), m.getScaledHeight())) {
                return m;
            }
        }
        return null;
    }

    // ---- geometry of the toolbar and elements panel ----------------------------------------

    private float barW() {
        return BUTTONS.length * 62 + 6;
    }

    private float barX() {
        return width / 2f - barW() / 2f;
    }

    private float barY() {
        return height - BAR_H - 34;
    }

    private float panelW() {
        return 150;
    }

    private float panelRowH() {
        return 16;
    }

    private float panelH() {
        return 22 + elements().size() * panelRowH() + 4;
    }

    private float panelX() {
        return width / 2f - panelW() / 2f;
    }

    private float panelY() {
        return barY() - 6 - panelH() * panelAnim.get();
    }

    // ---- rendering ---------------------------------------------------------------------------

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        float f = fade.update();
        g.fillGradient(0, 0, width, height, ColorUtil.fade(0x50000000, f), ColorUtil.fade(0x70000000, f));
        HudEditorModule s = settings();
        if (s == null || s.grid()) {
            int gridColor = ColorUtil.fade(0x14FFFFFF, f);
            for (int x = 0; x < width; x += 20) {
                g.fill(x, 0, x + 1, height, gridColor);
            }
            for (int y = 0; y < height; y += 20) {
                g.fill(0, y, width, y + 1, gridColor);
            }
        }
        int center = ColorUtil.fade(0x30FFFFFF, f);
        g.fill(width / 2, 0, width / 2 + 1, height, center);
        g.fill(0, height / 2, width, height / 2 + 1, center);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        ThemeManager t = AnnoyingClient.theme();
        HudModule hovered = dragging != null ? dragging : elementAt(mouseX, mouseY);
        boolean overUi = overToolbarOrPanel(mouseX, mouseY);

        for (HudModule m : elements()) {
            if (!m.isEnabled()) {
                continue;
            }
            m.render(g, partialTick, true);
            float x = m.getScreenX(width);
            float y = m.getScreenY(height);
            float w = m.getScaledWidth();
            float h = m.getScaledHeight();
            boolean active = m == hovered && !overUi;
            int outline = active || m == selected ? t.accent() : ColorUtil.withAlpha(0xFFFFFFFF, 70);
            RenderUtil.roundedOutline(g, x - 1.5f, y - 1.5f, w + 3, h + 3, 3, active ? 1f : 0.7f, outline);
            if (active || m == selected) {
                String label = m.getName() + "  " + String.format(Locale.ROOT, "%.0f%%", m.getScaleSetting().get() * 100);
                float lw = TextRenderer.width(label) + 8;
                float ly = y - 14 < 2 ? y + h + 3 : y - 14;
                float lx = MathUtil.clamp(x, 2, width - lw - 2);
                RenderUtil.roundedRect(g, lx, ly, lw, 11, 3, ColorUtil.withAlpha(t.accent(), 220));
                TextRenderer.draw(g, label, lx + 4, ly + 2, 0xFFFFFFFF, false, false);
            }
        }

        if (dragging != null) {
            if (!Float.isNaN(guideX)) {
                RenderUtil.rect(g, guideX - 0.5f, 0, 1, height, ColorUtil.withAlpha(t.accent(), 200));
            }
            if (!Float.isNaN(guideY)) {
                RenderUtil.rect(g, 0, guideY - 0.5f, width, 1, ColorUtil.withAlpha(t.accent(), 200));
            }
        }

        drawPanel(g, t, mouseX, mouseY);
        drawToolbar(g, t, mouseX, mouseY);

        String hint = "Drag to move  •  Scroll to scale  •  Right-click to reset  •  Esc to exit";
        float hw = TextRenderer.width(hint) + 12;
        RenderUtil.roundedRect(g, width / 2f - hw / 2f, height - 25, hw, 14, 4, 0x90000000);
        TextRenderer.draw(g, hint, width / 2f - hw / 2f + 6, height - 22, ColorUtil.withAlpha(0xFFFFFFFF, 200));
    }

    private void drawToolbar(GuiGraphics g, ThemeManager t, int mouseX, int mouseY) {
        float bx = barX();
        float by = barY();
        RenderUtil.shadow(g, bx, by, barW(), BAR_H, 8, 0x80000000);
        RenderUtil.roundedRect(g, bx, by, barW(), BAR_H, 7, t.background());
        for (int i = 0; i < BUTTONS.length; i++) {
            float x = bx + 4 + i * 62;
            boolean hover = MathUtil.inside(mouseX, mouseY, x, by + 3, 58, BAR_H - 6);
            buttonHover[i].setTarget(hover ? 1f : 0f);
            String label = BUTTONS[i];
            if (i == 1 && confirmReset) {
                label = "Confirm?";
            }
            boolean primary = i == 3 || (i == 0 && panelOpen);
            Widgets.button(g, x, by + 3, 58, BAR_H - 6, label, buttonHover[i].update(), primary);
        }
    }

    private void drawPanel(GuiGraphics g, ThemeManager t, int mouseX, int mouseY) {
        panelAnim.setTarget(panelOpen ? 1f : 0f);
        float p = panelAnim.update();
        if (p < 0.01f) {
            return;
        }
        float px = panelX();
        float py = panelY();
        float h = panelH() * p;
        RenderUtil.setGlobalAlpha(p);
        RenderUtil.shadow(g, px, py, panelW(), h, 8, 0x80000000);
        RenderUtil.roundedRect(g, px, py, panelW(), h, 7, t.background());
        RenderUtil.scissor(g, px, py, panelW(), h);
        TextRenderer.drawBold(g, "HUD Elements", px + 8, py + 7, t.text());
        float ry = py + 22;
        List<HudModule> list = elements();
        for (int i = 0; i < list.size(); i++) {
            HudModule m = list.get(i);
            boolean hover = MathUtil.inside(mouseX, mouseY, px + 4, ry, panelW() - 8, panelRowH());
            rowHover.get(i).setTarget(hover ? 1f : 0f);
            float hv = rowHover.get(i).update();
            if (hv > 0.01f) {
                RenderUtil.roundedRect(g, px + 4, ry, panelW() - 8, panelRowH(), 4, ColorUtil.withAlpha(t.text(), Math.round(16 * hv)));
            }
            TextRenderer.draw(g, m.getName(), px + 9, ry + 4, m.isEnabled() ? t.text() : t.textMuted());
            rowToggle.get(i).setTarget(m.isEnabled() ? 1f : 0f);
            Widgets.toggleSwitch(g, px + panelW() - 10 - Widgets.SWITCH_W, ry + (panelRowH() - Widgets.SWITCH_H) / 2f, rowToggle.get(i).update(), hv);
            ry += panelRowH();
        }
        RenderUtil.endScissor(g);
        RenderUtil.setGlobalAlpha(1f);
    }

    private boolean overToolbarOrPanel(double mx, double my) {
        if (MathUtil.inside(mx, my, barX(), barY(), barW(), BAR_H)) {
            return true;
        }
        return panelOpen && MathUtil.inside(mx, my, panelX(), panelY(), panelW(), panelH());
    }

    // ---- input -------------------------------------------------------------------------------

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x();
        double my = event.y();
        int button = event.button();
        // toolbar
        for (int i = 0; i < BUTTONS.length; i++) {
            float x = barX() + 4 + i * 62;
            if (MathUtil.inside(mx, my, x, barY() + 3, 58, BAR_H - 6) && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                if (i != 1) {
                    confirmReset = false;
                }
                switch (i) {
                    case 0 -> panelOpen = !panelOpen;
                    case 1 -> {
                        if (confirmReset) {
                            AnnoyingClient.hud().resetAllPositions();
                            AnnoyingClient.notifications().info("HUD reset", "All elements moved to their default positions");
                            confirmReset = false;
                        } else {
                            confirmReset = true;
                        }
                    }
                    case 2 -> {
                        if (minecraft != null) {
                            minecraft.setScreen(parent instanceof ClickGuiScreen ? parent : new ClickGuiScreen(parent));
                        }
                    }
                    default -> onClose();
                }
                return true;
            }
        }
        confirmReset = false;
        // elements panel
        if (panelOpen && MathUtil.inside(mx, my, panelX(), panelY(), panelW(), panelH())) {
            float ry = panelY() + 22;
            for (HudModule m : elements()) {
                if (MathUtil.inside(mx, my, panelX() + 4, ry, panelW() - 8, panelRowH())) {
                    m.toggle();
                    return true;
                }
                ry += panelRowH();
            }
            return true;
        }
        HudModule target = elementAt(mx, my);
        if (target == null) {
            selected = null;
            return true;
        }
        selected = target;
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            target.resetPosition();
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            dragging = target;
            dragOffsetX = (float) mx - target.getScreenX(width);
            dragOffsetY = (float) my - target.getScreenY(height);
        }
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (dragging == null) {
            return false;
        }
        float x = (float) event.x() - dragOffsetX;
        float y = (float) event.y() - dragOffsetY;
        float w = dragging.getScaledWidth();
        float h = dragging.getScaledHeight();
        guideX = Float.NaN;
        guideY = Float.NaN;
        HudEditorModule s = settings();
        boolean snap = (s == null || s.snapping()) && !event.hasShiftDown();
        if (snap) {
            float dist = s == null ? 4 : s.snapDistance();
            float[] snappedX = snapAxis(x, w, width, dist, true);
            float[] snappedY = snapAxis(y, h, height, dist, false);
            x = snappedX[0];
            guideX = snappedX[1];
            y = snappedY[0];
            guideY = snappedY[1];
        }
        dragging.setScreenPosition(x, y, width, height);
        return true;
    }

    /** Returns {snapped position, guide line coordinate or NaN}. */
    private float[] snapAxis(float pos, float size, int screen, float dist, boolean horizontal) {
        float best = pos;
        float guide = Float.NaN;
        float bestDelta = dist + 1;
        // screen edges and center
        float[][] candidates = {
                {0, 0}, {screen - size, screen}, {screen / 2f - size / 2f, screen / 2f}};
        for (float[] c : candidates) {
            float d = Math.abs(pos - c[0]);
            if (d < bestDelta && d <= dist) {
                bestDelta = d;
                best = c[0];
                guide = c[1];
            }
        }
        // other elements' edges
        for (HudModule other : elements()) {
            if (other == dragging || !other.isEnabled()) {
                continue;
            }
            float o = horizontal ? other.getScreenX(width) : other.getScreenY(height);
            float os = horizontal ? other.getScaledWidth() : other.getScaledHeight();
            float[] edges = {o, o + os - size, o - size, o + os};
            float[] lines = {o, o + os, o, o + os};
            for (int i = 0; i < edges.length; i++) {
                float d = Math.abs(pos - edges[i]);
                if (d < bestDelta && d <= dist) {
                    bestDelta = d;
                    best = edges[i];
                    guide = lines[i];
                }
            }
        }
        return new float[]{best, guide};
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        dragging = null;
        guideX = Float.NaN;
        guideY = Float.NaN;
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        HudModule target = elementAt(mouseX, mouseY);
        if (target == null || vertical == 0) {
            return false;
        }
        float oldX = target.getScreenX(width) + target.getScaledWidth() / 2f;
        float oldY = target.getScreenY(height) + target.getScaledHeight() / 2f;
        var scale = target.getScaleSetting();
        scale.setValue(scale.get() + (vertical > 0 ? 0.05 : -0.05));
        // keep the element centered where it was
        target.setScreenPosition(oldX - target.getScaledWidth() / 2f, oldY - target.getScaledHeight() / 2f, width, height);
        selected = target;
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (selected != null && selected.isEnabled()) {
            int step = event.hasShiftDown() ? 10 : 1;
            float x = selected.getScreenX(width);
            float y = selected.getScreenY(height);
            switch (event.key()) {
                case GLFW.GLFW_KEY_LEFT -> x -= step;
                case GLFW.GLFW_KEY_RIGHT -> x += step;
                case GLFW.GLFW_KEY_UP -> y -= step;
                case GLFW.GLFW_KEY_DOWN -> y += step;
                default -> {
                    return super.keyPressed(event);
                }
            }
            selected.setScreenPosition(x, y, width, height);
            return true;
        }
        return super.keyPressed(event);
    }
}
