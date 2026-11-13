package com.annoyingclient.gui.component;

import com.annoyingclient.AnnoyingClient;
import com.annoyingclient.modconfig.ConfigFile;
import com.annoyingclient.modconfig.LiveReload;
import com.annoyingclient.modconfig.ModConfigPrefs;
import com.annoyingclient.modconfig.ModConfigRegistry;
import com.annoyingclient.modconfig.ModEntry;
import com.annoyingclient.modconfig.ModIcons;
import com.annoyingclient.render.Icons;
import com.annoyingclient.render.RenderUtil;
import com.annoyingclient.render.TextRenderer;
import com.annoyingclient.setting.BooleanSetting;
import com.annoyingclient.setting.StringSetting;
import com.annoyingclient.util.Animation;
import com.annoyingclient.util.ColorUtil;
import com.annoyingclient.util.MathUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A row in the Mod Configs tab: icon, name, version and authors, a favorite star, and "Settings" for the
 * mod's own screen. Clicking the row opens its details (description, license, links) and editors for
 * its config files. Saved files are reloaded into the running mod where possible (see {@link LiveReload}).
 */
public class ModConfigCard extends UIComponent {

    private static final float HEADER = 34;
    private static final float GAP = 3;
    private static final float LINK_H = 15;
    private static final int LIVE_COLOR = 0xFF4ADE80;
    private static final int RESTART_COLOR = 0xFFFBBF24;

    private final ModEntry mod;
    private final Runnable onFavoriteChanged;
    private final Button settingsButton;
    /** Website / Issues / Source / Open folder. */
    private record Link(Button button, String label, boolean folder) {
    }

    private final List<Link> links = new ArrayList<>();
    private boolean expanded;
    private final Animation expandAnim = new Animation(0f, 15f);
    private final Animation hoverAnim = new Animation(0f, 16f);
    private final List<FileEditor> editors = new ArrayList<>();
    private boolean loaded;
    private List<String> descriptionLines = List.of();
    private float wrappedFor = -1;

    /** Editors for one config file. */
    private final class FileEditor {
        ConfigFile file;
        final List<SettingComponent<?>> rows = new ArrayList<>();
        final Button save;
        final Button undo;
        LiveReload.Mode mode;
        boolean dirty;

        FileEditor(Path path) {
            save = new Button("Save", true, 14, this::saveFile);
            undo = new Button("Undo", false, 14, this::restore);
            load(path);
        }

        void load(Path path) {
            file = ConfigFile.load(path);
            mode = LiveReload.predict(mod, path);
            rows.clear();
            dirty = false;
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
        }

        boolean hasBackup() {
            return Files.exists(ConfigFile.backupOf(file.path()));
        }

        void saveFile() {
            try {
                file.save();
                dirty = false;
                report(LiveReload.apply(mod, file.path()), "Saved " + file.path().getFileName());
            } catch (Exception ex) {
                AnnoyingClient.notifications().error("Not saved", ex.getMessage());
            }
        }

        /** Puts back the file as it was before the first save from this tab. */
        void restore() {
            try {
                ConfigFile.restoreBackup(file.path());
                load(file.path());
                report(LiveReload.apply(mod, file.path()), "Restored " + file.path().getFileName());
            } catch (Exception ex) {
                AnnoyingClient.notifications().error("Not restored", ex.getMessage());
            }
        }

        void report(LiveReload.Mode result, String title) {
            mode = result;
            switch (result) {
                case LIVE -> AnnoyingClient.notifications().success(title, mod.name() + " is using the new settings");
                case RESTART -> AnnoyingClient.notifications().info(title, "This file is only read when the game starts");
                default -> AnnoyingClient.notifications().info(title, mod.hasScreen()
                        ? mod.name() + " reads it on restart. Its Settings button applies changes instantly"
                        : mod.name() + " reads it on restart");
            }
        }

        float height() {
            float h = 30;
            if (file.error() != null || file.entries().isEmpty()) {
                return h + 14;
            }
            for (SettingComponent<?> r : rows) {
                h += r.getHeight() + GAP;
            }
            return h + 4;
        }
    }

    public ModConfigCard(ModEntry mod, Runnable onFavoriteChanged) {
        this.mod = mod;
        this.onFavoriteChanged = onFavoriteChanged;
        this.settingsButton = new Button("Settings", true, 16, this::openScreen);
        for (Map.Entry<String, String> link : mod.links().entrySet()) {
            links.add(new Link(new Button(link.getKey(), false, LINK_H, () -> openLink(link.getValue())), link.getKey(), false));
        }
        if (!mod.configFiles().isEmpty()) {
            links.add(new Link(new Button("Open folder", false, LINK_H, this::openFolder), "Open folder", true));
        }
    }

    private static String shortKey(String key) {
        return key.length() > 42 ? "..." + key.substring(key.length() - 40) : key;
    }

    public ModEntry getMod() {
        return mod;
    }

    public boolean isFavorite() {
        return ModConfigPrefs.get().isFavorite(mod.id());
    }

    /** Text the search box matches: name, id, authors and description. */
    public boolean matches(String query) {
        String q = query.toLowerCase(java.util.Locale.ROOT);
        return mod.name().toLowerCase(java.util.Locale.ROOT).contains(q) || mod.id().toLowerCase(java.util.Locale.ROOT).contains(q)
                || mod.authors().toLowerCase(java.util.Locale.ROOT).contains(q)
                || mod.description().toLowerCase(java.util.Locale.ROOT).contains(q);
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

    private void openLink(String url) {
        Minecraft mc = Minecraft.getInstance();
        ConfirmLinkScreen.confirmLinkNow(mc.screen, url);
    }

    private void openFolder() {
        Path first = mod.configFiles().getFirst();
        Util.getPlatform().openPath(first.getParent());
    }

    private void ensureLoaded() {
        if (loaded) {
            return;
        }
        loaded = true;
        for (Path p : mod.configFiles()) {
            editors.add(new FileEditor(p));
        }
    }

    // ---- layout -------------------------------------------------------------------------------

    private List<String> description() {
        float w = width - 24;
        if (w != wrappedFor) {
            wrappedFor = w;
            descriptionLines = wrap(mod.description().replaceAll("\\s+", " ").trim(), (int) w, 4);
        }
        return descriptionLines;
    }

    private static List<String> wrap(String text, int maxWidth, int maxLines) {
        List<String> out = new ArrayList<>();
        if (text.isEmpty()) {
            return out;
        }
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            String next = line.isEmpty() ? word : line + " " + word;
            if (TextRenderer.width(next) > maxWidth && !line.isEmpty()) {
                out.add(line.toString());
                line = new StringBuilder(word);
                if (out.size() == maxLines) {
                    out.set(maxLines - 1, TextRenderer.trim(out.get(maxLines - 1) + " " + word + "...", maxWidth));
                    return out;
                }
            } else {
                line = new StringBuilder(next);
            }
        }
        out.add(TextRenderer.trim(line.toString(), maxWidth));
        return out;
    }

    private float detailsHeight() {
        float h = 6 + description().size() * 11 + 14;
        if (!links.isEmpty()) {
            h += LINK_H + 6;
        }
        return h;
    }

    private float bodyHeight() {
        float h = detailsHeight();
        for (FileEditor e : editors) {
            h += e.height() + 6;
        }
        if (editors.isEmpty() && !mod.hasScreen()) {
            h += 14;
        }
        return h;
    }

    @Override
    public float getHeight() {
        return HEADER + expandAnim.get() * (expanded || expandAnim.get() > 0.01f ? bodyHeight() : 0);
    }

    private float starX() {
        return x + width - 32 - (mod.hasScreen() ? 58 : 0);
    }

    // ---- rendering ----------------------------------------------------------------------------

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
        Widgets.chevron(g, right - 2, y + HEADER / 2f, ex, ColorUtil.lerp(t.textMuted(), t.text(), hv));
        right -= 14;
        if (mod.hasScreen()) {
            settingsButton.setBounds(right - 52, y + (HEADER - 16) / 2f, 52);
            settingsButton.render(g, mouseX, mouseY);
            right -= 58;
        }
        boolean fav = isFavorite();
        boolean starHover = MathUtil.inside(mouseX, mouseY, starX() - 2, y + 8, 18, 18);
        if (fav || headerHover) {
            Icons.draw(g, fav ? Icons.Icon.STAR_FILLED : Icons.Icon.STAR, starX() + 7, y + HEADER / 2f, 11,
                    fav ? 0xFFF5BE3C : starHover ? t.text() : t.textDisabled());
        }
        right -= 18;

        int maxText = (int) (right - textX - 6);
        String name = TextRenderer.trim(mod.name(), maxText - (mod.library() ? 48 : 0));
        TextRenderer.drawBold(g, name, textX, y + 7, t.text());
        if (mod.library()) {
            float bx = textX + TextRenderer.width(name, true) + 5;
            RenderUtil.roundedRect(g, bx, y + 6, TextRenderer.width("Library") * 0.75f + 8, 10, 5, t.control());
            TextRenderer.drawScaled(g, "Library", bx + 4, y + 8.5f, 0.75f, t.textMuted(), false);
        }
        String sub = mod.version() + (mod.authors().isEmpty() ? "" : "  ·  " + mod.authors());
        if (!mod.hasSettings()) {
            sub += "  ·  no settings";
        }
        TextRenderer.draw(g, TextRenderer.trim(sub, maxText), textX, y + 19, t.textMuted());

        if (ex > 0.01f) {
            RenderUtil.scissor(g, x, y + HEADER, width, total - HEADER);
            float cy = y + HEADER + 2;
            cy = drawDetails(g, cy, mouseX, mouseY);
            for (FileEditor ed : editors) {
                drawFile(g, ed, cy, mouseX, mouseY);
                cy += ed.height() + 6;
            }
            if (editors.isEmpty() && !mod.hasScreen()) {
                TextRenderer.draw(g, "This mod has nothing to configure.", x + 12, cy, t.textDisabled());
            }
            RenderUtil.endScissor(g);
        }
    }

    private float drawDetails(GuiGraphics g, float dy, float mouseX, float mouseY) {
        var t = theme();
        float dx = x + 12;
        dy += 4;
        for (String line : description()) {
            TextRenderer.draw(g, line, dx, dy, t.text());
            dy += 11;
        }
        StringBuilder meta = new StringBuilder("ID " + mod.id());
        if (!mod.license().isEmpty()) {
            meta.append("  ·  ").append(mod.license()).append(" license");
        }
        if (mod.library()) {
            meta.append("  ·  needed by other mods");
        }
        TextRenderer.drawScaled(g, TextRenderer.trim(meta.toString(), (int) ((width - 24) / 0.85f)), dx, dy + 1, 0.85f, t.textDisabled(), false);
        dy += 14;
        if (!links.isEmpty()) {
            float bx = dx;
            for (Link link : links) {
                float bw = TextRenderer.width(link.label()) + 26;
                link.button().setLabel(link.label() + "    "); // room for the icon
                link.button().setBounds(bx, dy, bw);
                link.button().render(g, mouseX, mouseY);
                Icons.draw(g, link.folder() ? Icons.Icon.FOLDER : Icons.Icon.LINK, bx + bw - 10, dy + LINK_H / 2f, 7, t.textMuted());
                bx += bw + 4;
            }
            dy += LINK_H + 6;
        }
        return dy;
    }

    private void drawFile(GuiGraphics g, FileEditor ed, float fy, float mouseX, float mouseY) {
        var t = theme();
        float fx = x + 12, fw = width - 24;
        RenderUtil.rect(g, fx, fy, fw, 0.6f, ColorUtil.withAlpha(t.border(), 160));
        String label = "config/" + ed.file.path().getFileName();
        float buttons = 0;
        if (ed.file.error() == null && !ed.file.entries().isEmpty()) {
            ed.save.setBounds(fx + fw - 40, fy + 4, 40);
            ed.save.render(g, mouseX, mouseY);
            buttons = 44;
            if (ed.hasBackup()) {
                ed.undo.setBounds(fx + fw - 84, fy + 4, 40);
                ed.undo.render(g, mouseX, mouseY);
                buttons = 88;
            }
        }
        TextRenderer.draw(g, TextRenderer.trim(label + (ed.dirty ? "  (unsaved)" : ""), (int) (fw - buttons - 6)), fx, fy + 5,
                ed.dirty ? t.accent() : t.text());
        int modeColor = switch (ed.mode) {
            case LIVE -> LIVE_COLOR;
            case RESTART -> RESTART_COLOR;
            default -> t.textDisabled();
        };
        String status = ed.mode.label + (ed.file.isJson5() ? "  ·  comments are not kept" : "");
        RenderUtil.circle(g, fx + 2.5f, fy + 20.5f, 2.2f, modeColor);
        TextRenderer.drawScaled(g, TextRenderer.trim(status, (int) ((fw - buttons - 14) / 0.85f)), fx + 8, fy + 17.5f, 0.85f, modeColor, false);
        float ry = fy + 30;
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

    // ---- input --------------------------------------------------------------------------------

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
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && MathUtil.inside(mouseX, mouseY, starX() - 2, y + 8, 18, 18)) {
                ModConfigPrefs.get().toggleFavorite(mod.id());
                onFavoriteChanged.run();
                return true;
            }
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT || button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                ensureLoaded();
                expanded = !expanded;
                if (!expanded) {
                    unfocus();
                }
            }
            return true;
        }
        if (expanded && expandAnim.get() > 0.9f) {
            for (Link link : links) {
                if (link.button().mouseClicked(mouseX, mouseY, button)) {
                    return true;
                }
            }
            for (FileEditor ed : editors) {
                if (ed.save.mouseClicked(mouseX, mouseY, button) || ed.hasBackup() && ed.undo.mouseClicked(mouseX, mouseY, button)) {
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
        if (MathUtil.inside(mouseX, mouseY, starX() - 2, y + 8, 18, 18)) {
            return isFavorite() ? "Remove from favorites" : "Add to favorites (shown first)";
        }
        if (!expanded && MathUtil.inside(mouseX, mouseY, x, y, width, HEADER)) {
            return mod.description().isEmpty() ? null : TextRenderer.trim(mod.description().replaceAll("\\s+", " "), 300);
        }
        for (FileEditor ed : editors) {
            if (ed.hasBackup() && ed.undo.isHovered(mouseX, mouseY)) {
                return "Put back the file as it was before you first saved it here";
            }
            for (SettingComponent<?> row : ed.rows) {
                if (row.isHovered(mouseX, mouseY)) {
                    return row.getSetting().getName();
                }
            }
        }
        return null;
    }
}
