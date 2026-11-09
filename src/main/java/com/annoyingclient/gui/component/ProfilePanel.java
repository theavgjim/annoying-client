package com.annoyingclient.gui.component;

import com.annoyingclient.AnnoyingClient;
import com.annoyingclient.config.ConfigManager;
import com.annoyingclient.render.RenderUtil;
import com.annoyingclient.render.TextRenderer;
import com.annoyingclient.util.Animation;
import com.annoyingclient.util.ColorUtil;
import com.annoyingclient.util.MathUtil;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/** Lists configuration profiles with load/delete actions, plus create, save, reset and open-folder buttons. */
public class ProfilePanel extends UIComponent {

    private static final float ROW = 20;
    private static final float GAP = 3;

    private final TextField nameField = new TextField("New profile name...", 32, 16);
    private final Button create;
    private final Button save;
    private final Button reset;
    private final Button folder;
    private List<String> profiles = new ArrayList<>();
    private long lastRefresh;
    private final List<Animation> hovers = new ArrayList<>();
    private String confirmDelete;
    private boolean confirmReset;

    public ProfilePanel() {
        nameField.onSubmit(this::createProfile);
        create = new Button("Create", true, 16, this::createProfile);
        save = new Button("Save Now", false, 16, () -> AnnoyingClient.config().saveManually());
        reset = new Button("Reset Profile", false, 16, () -> {
            if (confirmReset) {
                AnnoyingClient.config().resetActiveProfile();
                confirmReset = false;
            } else {
                confirmReset = true;
            }
        });
        folder = new Button("Open Folder", false, 16, () -> {
            AnnoyingClient client = AnnoyingClient.getInstance();
            if (client != null) {
                Util.getPlatform().openPath(client.getConfigDirectory());
            }
        });
    }

    private void createProfile() {
        if (AnnoyingClient.config().createProfile(nameField.getText())) {
            nameField.setText("");
            nameField.unfocus();
            refresh();
        }
    }

    private void refresh() {
        profiles = AnnoyingClient.config().listProfiles();
        lastRefresh = System.currentTimeMillis();
        while (hovers.size() < profiles.size()) {
            hovers.add(new Animation(0f, 16f));
        }
    }

    @Override
    public float getHeight() {
        return 16 + 6 + 16 + 8 + profiles.size() * (ROW + GAP);
    }

    @Override
    public void render(GuiGraphics g, float mouseX, float mouseY) {
        if (System.currentTimeMillis() - lastRefresh > 1000) {
            refresh();
        }
        var t = theme();
        ConfigManager config = AnnoyingClient.config();
        float bw = 52;
        nameField.setBounds(x, y, width - bw - 4);
        nameField.render(g, mouseX, mouseY);
        create.setBounds(x + width - bw, y, bw);
        create.render(g, mouseX, mouseY);

        float by = y + 22;
        float third = (width - 8) / 3f;
        save.setBounds(x, by, third);
        reset.setLabel(confirmReset ? "Click to confirm" : "Reset Profile");
        reset.setBounds(x + third + 4, by, third);
        folder.setBounds(x + (third + 4) * 2, by, third);
        save.render(g, mouseX, mouseY);
        reset.render(g, mouseX, mouseY);
        folder.render(g, mouseX, mouseY);

        float ry = by + 16 + 8;
        for (int i = 0; i < profiles.size(); i++) {
            String name = profiles.get(i);
            boolean active = name.equals(config.getActiveProfile());
            boolean hovered = MathUtil.inside(mouseX, mouseY, x, ry, width, ROW);
            Animation hv = hovers.get(i);
            hv.setTarget(hovered ? 1f : 0f);
            float h = hv.update();
            RenderUtil.roundedRect(g, x, ry, width, ROW, 4, ColorUtil.lerp(t.surface(), t.surfaceHover(), h));
            if (active) {
                RenderUtil.roundedRect(g, x, ry + 5, 2.5f, ROW - 10, 1.25f, t.accent());
            }
            TextRenderer.draw(g, name, x + 9, ry + 6, active ? t.accent() : t.text());
            if (active) {
                TextRenderer.draw(g, "active", x + 9 + TextRenderer.width(name) + 6, ry + 6, t.textMuted());
            } else {
                String load = "Load";
                String del = name.equals(confirmDelete) ? "Confirm?" : "Delete";
                float delW = TextRenderer.width(del);
                float dx = x + width - 8 - delW;
                float lx = dx - 10 - TextRenderer.width(load);
                boolean overLoad = MathUtil.inside(mouseX, mouseY, lx - 3, ry, TextRenderer.width(load) + 6, ROW);
                boolean overDel = MathUtil.inside(mouseX, mouseY, dx - 3, ry, delW + 6, ROW);
                TextRenderer.draw(g, load, lx, ry + 6, overLoad ? t.accent() : t.textMuted());
                if (!name.equals(ConfigManager.DEFAULT_PROFILE)) {
                    TextRenderer.draw(g, del, dx, ry + 6, overDel ? 0xFFEF4444 : t.textMuted());
                }
            }
            ry += ROW + GAP;
        }
    }

    @Override
    public boolean mouseClicked(float mouseX, float mouseY, int button) {
        boolean handled = nameField.mouseClicked(mouseX, mouseY, button);
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return handled;
        }
        if (!reset.isHovered(mouseX, mouseY)) {
            confirmReset = false;
        }
        if (create.mouseClicked(mouseX, mouseY, button) || save.mouseClicked(mouseX, mouseY, button)
                || reset.mouseClicked(mouseX, mouseY, button) || folder.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        ConfigManager config = AnnoyingClient.config();
        float ry = y + 22 + 16 + 8;
        for (String name : profiles) {
            if (MathUtil.inside(mouseX, mouseY, x, ry, width, ROW) && !name.equals(config.getActiveProfile())) {
                String del = name.equals(confirmDelete) ? "Confirm?" : "Delete";
                float delW = TextRenderer.width(del);
                float dx = x + width - 8 - delW;
                if (mouseX >= dx - 3 && !name.equals(ConfigManager.DEFAULT_PROFILE)) {
                    if (name.equals(confirmDelete)) {
                        config.deleteProfile(name);
                        confirmDelete = null;
                        refresh();
                    } else {
                        confirmDelete = name;
                    }
                } else {
                    confirmDelete = null;
                    config.switchProfile(name);
                    refresh();
                }
                return true;
            }
            ry += ROW + GAP;
        }
        confirmDelete = null;
        return handled;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        return nameField.keyPressed(event);
    }

    @Override
    public boolean charTyped(int codepoint) {
        return nameField.charTyped(codepoint);
    }

    @Override
    public boolean isCapturingInput() {
        return nameField.isCapturingInput();
    }

    @Override
    public void unfocus() {
        nameField.unfocus();
        confirmDelete = null;
        confirmReset = false;
    }
}
