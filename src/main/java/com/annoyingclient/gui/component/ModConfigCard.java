package com.annoyingclient.gui.component;

import com.annoyingclient.AnnoyingClient;
import com.annoyingclient.modconfig.ConfigFile;
import com.annoyingclient.modconfig.ModConfigRegistry;
import com.annoyingclient.modconfig.ModEntry;
import com.annoyingclient.modconfig.ModIcons;
import com.annoyingclient.render.RenderUtil;
import com.annoyingclient.render.TextRenderer;
import com.annoyingclient.setting.BooleanSetting;
import com.annoyingclient.setting.StringSetting;
import com.annoyingclient.util.Animation;
import com.annoyingclient.util.ColorUtil;
import com.annoyingclient.util.MathUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * A row in the Mod Configs tab: mod icon, name, version and author; "Settings" opens the mod's own
 * config screen; the arrow expands editors for the mod's config files.
 */
public class ModConfigCard extends UIComponent {

    private static final float HEADER = 34;
    private static final float GAP = 3;

    private final ModEntry mod;
    private final Button settingsButton;
    private boolean expanded;
    private final Animation expandAnim = new Animation(0f, 15f);
    private final Animation hoverAnim = new Animation(0f, 16f);
    private final List<FileEditor> editors = new ArrayList<>();
    private boolean loaded;

    /** Editors for one config file. */
    private final class FileEditor {
        final ConfigFile file;
        final List<SettingComponent<?>> rows = new ArrayList<>();
        final Button save;
        boolean dirty;

        FileEditor(ConfigFile file) {
            this.file = file;
            for (ConfigFile.Entry e : file.entries()) {
                SettingComponent<?> row;
                if (e.kind == ConfigFile.Kind.BOOLEAN) {
                    BooleanSetting s = new BooleanSetting(e.key, "", Boolean.parseBoolean(e.value));
                    s.onChange(v -> { e.value = String.valueOf(v); dirty = true; });
                    row = new BooleanComponent(s, shortKey(e.key));
                } else {
                    StringSetting s = new StringSetting(e.key, "", e.value, 512);
                    s.onChange(v -> { e.value = v; dirty = true; });
                    row = new StringComponent(s, shortKey(e.key));
                }
                rows.add(row);
            }
            save = new Button("Save", true, 14, this::saveFile);
        }

        void saveFile() {
            try {
                file.save();
                dirty = false;
                AnnoyingClient.notifications().success("Saved " + file.path().getFileName(), "Restart the game if the change doesn't apply right away");
            } catch (Exception ex) {
                AnnoyingClient.notifications().error("Not saved", ex.getMessage());
            }
        }

        float height() {
            float h = 18;
            if (file.error() != null || file.entries().isEmpty()) {
                return h + 14;
            }
            for (SettingComponent<?> r : rows) {
                h += r.getHeight() + GAP;
            }
            return h + 4;
        }
    }

    public ModConfigCard(ModEntry mod) {
        this.mod = mod;
        this.settingsButton = new Button("Settings", true, 16, this::openScreen);
    }

    private static String shortKey(String key) {
        return key.length() > 42 ? "..." + key.substring(key.length() - 40) : key;
    }

    public ModEntry getMod() {
        return mod;
    }

    private void openScreen() {
        Minecraft mc = Minecraft.getInstance();
        Screen screen = ModConfigRegistry.createScreen(mod, mc.screen);
        if (screen == null) {
            AnnoyingClient.notifications().error("Can't open settings", mod.name() + " did not provide a settings screen");
            return;
        }
        mc.setScreen(screen);
    }

    private void ensureLoaded() {
        if (loaded) {
            return;
        }
        loaded = true;
        for (Path p : mod.configFiles()) {
            editors.add(new FileEditor(ConfigFile.load(p)));
        }
    }

    private float bodyHeight() {
        float h = 6;
        for (FileEditor e : editors) {
            h += e.height() + 6;
        }
        return h;
    }

    @Override
    public float getHeight() {
        return HEADER + expandAnim.get() * (expanded || expandAnim.get() > 0.01f ? bodyHeight() : 0);
    }

    @Override
    public void render(GuiGraphics g, float mouseX, float mouseY) {
        var t = theme();
        boolean headerHover = MathUtil.inside(mouseX, mouseY, x, y, width, HEADER);
        hoverAnim.setTarget(headerHover ? 1f : 0f);
        expandAnim.setTarget(expanded ? 1f : 0f);
        float hv = hoverAnim.update();
        float ex = expandAnim.update();
        float total = getHeight();
        RenderUtil.roundedRect(g, x, y, width, total, 5, ColorUtil.lerp(t.surface(), t.surfaceHover(), hv));

        // icon
        Identifier icon = ModIcons.get(mod.container());
        float ix = x + 8, iy = y + 7;
        if (icon != null) {
            g.blit(RenderPipelines.GUI_TEXTURED, icon, (int) ix, (int) iy, 0, 0, 20, 20, 20, 20, ColorUtil.withAlpha(0xFFFFFF, Math.round(255 * RenderUtil.getGlobalAlpha())));
        } else {
            RenderUtil.roundedRect(g, ix, iy, 20, 20, 4, t.control());
            String letter = mod.name().isEmpty() ? "?" : mod.name().substring(0, 1).toUpperCase();
            TextRenderer.draw(g, letter, ix + 10 - TextRenderer.width(letter) / 2f, iy + 6, t.textMuted());
        }

        float textX = x + 36;
        float right = x + width - 10;
        boolean hasFiles = !mod.configFiles().isEmpty();
        if (hasFiles) {
            Widgets.chevron(g, right - 2, y + HEADER / 2f, ex, ColorUtil.lerp(t.textMuted(), t.text(), hv));
            right -= 14;
        }
        if (mod.hasScreen()) {
            settingsButton.setBounds(right - 52, y + (HEADER - 16) / 2f, 52);
            settingsButton.render(g, mouseX, mouseY);
            right -= 58;
        }
        int maxText = (int) (right - textX - 6);
        TextRenderer.drawBold(g, TextRenderer.trim(mod.name(), maxText), textX, y + 7, t.text());
        String sub = mod.version() + (mod.authors().isEmpty() ? "" : "  ·  " + mod.authors());
        if (!mod.hasScreen() && !hasFiles) {
            sub += "  ·  no settings";
        }
        TextRenderer.draw(g, TextRenderer.trim(sub, maxText), textX, y + 19, t.textMuted());

        if (ex > 0.01f) {
            RenderUtil.scissor(g, x, y + HEADER, width, total - HEADER);
            float cy = y + HEADER + 4;
            for (FileEditor ed : editors) {
                drawFile(g, ed, cy, mouseX, mouseY);
                cy += ed.height() + 6;
            }
            RenderUtil.endScissor(g);
        }
    }

    private void drawFile(GuiGraphics g, FileEditor ed, float fy, float mouseX, float mouseY) {
        var t = theme();
        float fx = x + 12, fw = width - 24;
        RenderUtil.rect(g, fx, fy, fw, 0.6f, ColorUtil.withAlpha(t.border(), 160));
        String label = "config/" + ed.file.path().getFileName();
        TextRenderer.draw(g, TextRenderer.trim(label, (int) (fw - 100)), fx, fy + 5, ed.dirty ? t.accent() : t.textMuted());
        if (ed.file.error() == null && !ed.file.entries().isEmpty()) {
            ed.save.setBounds(fx + fw - 40, fy + 2, 40);
            ed.save.render(g, mouseX, mouseY);
            if (ed.file.isJson5()) {
                String note = "comments are not kept";
                TextRenderer.drawScaled(g, note, fx + fw - 46 - TextRenderer.width(note) * 0.75f, fy + 6.5f, 0.75f, t.textDisabled(), false);
            }
        }
        float ry = fy + 18;
        if (ed.file.error() != null) {
            TextRenderer.draw(g, ed.file.error(), fx, ry, 0xFFEF4444);
            return;
        }
        if (ed.file.entries().isEmpty()) {
            TextRenderer.draw(g, "No simple values to edit. Use the mod's Settings button.", fx, ry, t.textMuted());
            return;
        }
        for (SettingComponent<?> row : ed.rows) {
            row.setBounds(fx, ry, fw);
            row.render(g, mouseX, mouseY);
            ry += row.getHeight() + GAP;
        }
    }

    @Override
    public boolean mouseClicked(float mouseX, float mouseY, int button) {
        if (!isHovered(mouseX, mouseY)) {
            unfocus();
            return false;
        }
        if (mouseY < y + HEADER) {
            if (mod.hasScreen() && settingsButton.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
            if (!mod.configFiles().isEmpty() && (button == GLFW.GLFW_MOUSE_BUTTON_LEFT || button == GLFW.GLFW_MOUSE_BUTTON_RIGHT)) {
                ensureLoaded();
                expanded = !expanded;
                if (!expanded) {
                    unfocus();
                }
            }
            return true;
        }
        if (expanded && expandAnim.get() > 0.9f) {
            for (FileEditor ed : editors) {
                if (ed.save.mouseClicked(mouseX, mouseY, button)) {
                    return true;
                }
                for (SettingComponent<?> row : ed.rows) {
                    if (row.mouseClicked(mouseX, mouseY, button)) {
                        return true;
                    } else if (!row.isHovered(mouseX, mouseY)) {
                        row.unfocus();
                    }
                }
            }
        }
        return true;
    }

    @Override
    public void mouseReleased(float mouseX, float mouseY, int button) {
        for (FileEditor ed : editors) {
            for (SettingComponent<?> row : ed.rows) {
                row.mouseReleased(mouseX, mouseY, button);
            }
        }
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        for (FileEditor ed : editors) {
            for (SettingComponent<?> row : ed.rows) {
                if (row.isCapturingInput() && row.keyPressed(event)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean charTyped(int codepoint) {
        for (FileEditor ed : editors) {
            for (SettingComponent<?> row : ed.rows) {
                if (row.isCapturingInput() && row.charTyped(codepoint)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean isCapturingInput() {
        for (FileEditor ed : editors) {
            for (SettingComponent<?> row : ed.rows) {
                if (row.isCapturingInput()) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public void unfocus() {
        for (FileEditor ed : editors) {
            for (SettingComponent<?> row : ed.rows) {
                row.unfocus();
            }
        }
    }

    @Override
    public String getTooltip(float mouseX, float mouseY) {
        if (MathUtil.inside(mouseX, mouseY, x, y, width, HEADER)) {
            return mod.description().isEmpty() ? null : TextRenderer.trim(mod.description().replaceAll("\\s+", " "), 300);
        }
        for (FileEditor ed : editors) {
            for (SettingComponent<?> row : ed.rows) {
                if (row.isHovered(mouseX, mouseY)) {
                    return row.getSetting().getName();
                }
            }
        }
        return null;
    }
}
