package com.annoyingclient.gui.clickgui;

import com.annoyingclient.AnnoyingClient;
import com.annoyingclient.gui.component.ModConfigCard;
import com.annoyingclient.gui.component.ModuleCard;
import com.annoyingclient.modconfig.ModConfigRegistry;
import com.annoyingclient.modconfig.ModEntry;
import com.annoyingclient.gui.component.ProfilePanel;
import com.annoyingclient.gui.component.SectionHeader;
import com.annoyingclient.gui.component.SettingComponent;
import com.annoyingclient.gui.component.TextField;
import com.annoyingclient.gui.component.ThemePresetGrid;
import com.annoyingclient.gui.component.UIComponent;
import com.annoyingclient.gui.component.WardrobePanel;
import com.annoyingclient.hud.HudEditorScreen;
import com.annoyingclient.module.Category;
import com.annoyingclient.module.Module;
import com.annoyingclient.module.impl.utility.ClickGuiModule;
import com.annoyingclient.render.Icons;
import com.annoyingclient.render.RenderUtil;
import com.annoyingclient.render.TextRenderer;
import com.annoyingclient.setting.Setting;
import com.annoyingclient.theme.ThemeManager;
import com.annoyingclient.util.Animation;
import com.annoyingclient.util.ColorUtil;
import com.annoyingclient.util.Easing;
import com.annoyingclient.util.MathUtil;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.joml.Matrix3x2fStack;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * The Annoying Client ClickGUI: a single draggable window with a category sidebar, search,
 * expandable module cards, a theme tab and a client tab (profiles and keybinds).
 *
 * <p>The window is laid out in a fixed logical size and scaled by the theme's GUI Scale (clamped to
 * fit the screen), so it looks the same at every resolution and Minecraft GUI scale.
 */
public class ClickGuiScreen extends Screen {

    private static final float W = 470;
    private static final float H = 320;
    private static final float SIDEBAR = 122;
    private static final float HEADER = 40;
    private static final float PAD = 10;
    private static final float TAB_H = 22;
    private static final float CONTENT_GAP = 5;

    private static int lastTab;
    private static float savedX = Float.NaN;
    private static float savedY = Float.NaN;

    private record Tab(String name, Icons.Icon icon, String description, Category category) {
    }

    private final Screen parent;
    private final List<Tab> tabs = new ArrayList<>();
    private final int modConfigTab;
    private final int wardrobeTab;
    private final int themeTab;
    private final int clientTab;
    private int tab;

    private final Map<Module, ModuleCard> cards = new IdentityHashMap<>();
    private final List<UIComponent> themeContent = new ArrayList<>();
    private List<ModConfigCard> modConfigCards;
    private com.annoyingclient.gui.component.ModConfigToolbar modConfigToolbar;
    private int modConfigShown;
    /** Set by components that change the list while it is being iterated (clicks); applied next frame. */
    private boolean recollect;
    private WardrobePanel wardrobe;

    /** The Wardrobe tab's panel once it has been opened, else null. */
    public WardrobePanel wardrobe() {
        return wardrobe;
    }
    private final List<UIComponent> clientContent = new ArrayList<>();
    private final List<UIComponent> visibleContent = new ArrayList<>();
    private final List<Animation> tabHover = new ArrayList<>();
    private final TextField search = new TextField("Search modules...", 32, 18).icon(Icons.Icon.SEARCH);

    private final Animation open = Animation.timed(0f, 260, Easing.EASE_OUT_QUINT);
    private final Animation content = Animation.timed(1f, 220, Easing.EASE_OUT_CUBIC);
    private final Animation indicator = new Animation(0f, 18f);
    private final Animation scroll = new Animation(0f, 16f);
    private final Animation hudButtonHover = new Animation(0f, 16f);
    private final Animation closeHover = new Animation(0f, 16f);
    private float scrollTarget;
    private float contentHeight;
    private boolean closing;
    /** The key press that opens the GUI is also delivered to it by vanilla; ignore the toggle key briefly. */
    private long openedAt;

    private float winX;
    private float winY;
    private float scale = 1f;
    private boolean draggingWindow;
    private boolean positioned;
    private float dragOffsetX;
    private float dragOffsetY;
    private float localMouseX;
    private float localMouseY;
    private String lastSearch = "";

    public ClickGuiScreen(Screen parent) {
        super(Component.literal(AnnoyingClient.NAME));
        this.parent = parent;
        for (Category c : Category.values()) {
            tabs.add(new Tab(c.getDisplayName(), iconFor(c), c.getDescription(), c));
        }
        modConfigTab = tabs.size();
        tabs.add(new Tab("Mod Configs", Icons.Icon.CONFIG, "Settings for every installed mod", null));
        wardrobeTab = tabs.size();
        tabs.add(new Tab("Wardrobe", Icons.Icon.WARDROBE, "Capes, trails and coins", null));
        themeTab = tabs.size();
        tabs.add(new Tab("Themes", Icons.Icon.THEMES, "Presets, colors, fonts and animation", null));
        clientTab = tabs.size();
        tabs.add(new Tab("Client", Icons.Icon.CLIENT, "Profiles and keybinds", null));
        for (int i = 0; i < tabs.size(); i++) {
            tabHover.add(new Animation(0f, 16f));
        }

        for (Module m : AnnoyingClient.modules().getModules()) {
            cards.put(m, new ModuleCard(m));
        }

        themeContent.add(new SectionHeader("Presets"));
        themeContent.add(new ThemePresetGrid());
        themeContent.add(new SectionHeader("Customize"));
        for (Setting<?> s : AnnoyingClient.theme().getSettings()) {
            if (!s.getName().equals("Theme")) {
                themeContent.add(SettingComponent.create(s));
            }
        }

        clientContent.add(new SectionHeader("Profiles"));
        clientContent.add(new ProfilePanel());
        clientContent.add(new SectionHeader("Keybinds"));
        for (Module m : AnnoyingClient.modules().getModules()) {
            clientContent.add(SettingComponent.create(m.getKeybind(), m.getName()));
        }

        ClickGuiModule gui = AnnoyingClient.modules().get(ClickGuiModule.class);
        tab = gui != null && gui.remembersTab() ? Math.min(lastTab, tabs.size() - 1) : 0;
        indicator.snap(tab);
        search.onChange(text -> scrollTarget = 0);
    }

    private static Icons.Icon iconFor(Category category) {
        return switch (category) {
            case RENDER -> Icons.Icon.RENDER;
            case HUD -> Icons.Icon.HUD;
            case UTILITY -> Icons.Icon.UTILITY;
            case VISUAL -> Icons.Icon.VISUAL;
            case MISC -> Icons.Icon.MISC;
        };
    }

    // ---- lifecycle ---------------------------------------------------------------------------

    @Override
    public void added() {
        openedAt = System.currentTimeMillis();
        closing = false;
        open.snap(0f);
        open.setTarget(1f);
    }

    @Override
    public boolean isPauseScreen() {
        ClickGuiModule gui = AnnoyingClient.modules().get(ClickGuiModule.class);
        return gui != null && gui.pausesGame();
    }

    @Override
    public void onClose() {
        if (closing) {
            return;
        }
        closing = true;
        unfocusAll();
        open.setTarget(0f);
    }

    private void finishClose() {
        savedX = winX;
        savedY = winY;
        lastTab = tab;
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    private void unfocusAll() {
        search.unfocus();
        for (UIComponent c : visibleContent) {
            c.unfocus();
        }
    }

    // ---- layout helpers ----------------------------------------------------------------------

    private void updateLayout() {
        float s = AnnoyingClient.theme().guiScale();
        float maxScale = Math.min((width - 16) / W, (height - 16) / H);
        scale = Math.max(0.3f, Math.min(s, maxScale));
        float w = W * scale;
        float h = H * scale;
        if (Float.isNaN(savedX) || Float.isNaN(savedY)) {
            savedX = (width - w) / 2f;
            savedY = (height - h) / 2f;
        }
        if (!positioned) {
            positioned = true;
            winX = savedX;
            winY = savedY;
        }
        winX = MathUtil.clamp(winX, 0, Math.max(0, width - w));
        winY = MathUtil.clamp(winY, 0, Math.max(0, height - h));
    }

    private float openScale() {
        return 0.9f + 0.1f * open.get();
    }

    private float toLocalX(double mouseX) {
        float cx = winX + W * scale / 2f;
        return (float) ((mouseX - cx) / (scale * openScale()) + W / 2f);
    }

    private float toLocalY(double mouseY) {
        float cy = winY + H * scale / 2f;
        return (float) ((mouseY - cy) / (scale * openScale()) + H / 2f);
    }

    private float contentX() {
        return SIDEBAR + PAD;
    }

    private float contentY() {
        return HEADER + 6;
    }

    private float contentW() {
        return W - SIDEBAR - PAD * 2 - 4;
    }

    private float contentH() {
        return H - HEADER - 6 - PAD;
    }

    /** Typing searches modules everywhere except the Mod Configs tab, where it filters mods. */
    private boolean searchingModules() {
        return searching() && tab != modConfigTab;
    }

    private boolean searching() {
        return !search.getText().isBlank();
    }

    private void switchTab(int index) {
        if (index == tab && !searching()) {
            return;
        }
        unfocusAll();
        tab = index;
        lastTab = index;
        if (searching()) {
            search.setText("");
        }
        scrollTarget = 0;
        scroll.snap(0);
        content.snap(0f);
        content.setTarget(1f);
    }

    private List<ModConfigCard> modConfigCards() {
        if (modConfigCards == null) {
            modConfigCards = new ArrayList<>();
            for (ModEntry m : ModConfigRegistry.mods()) {
                modConfigCards.add(new ModConfigCard(m, this::requestRecollect));
            }
        }
        return modConfigCards;
    }

    /** The Mod Configs list: toolbar, then favorites first, filtered by the toolbar and the search box. */
    private void collectModConfigs() {
        if (modConfigToolbar == null) {
            modConfigToolbar = new com.annoyingclient.gui.component.ModConfigToolbar(this::requestRecollect);
        }
        visibleContent.add(modConfigToolbar);
        var prefs = com.annoyingclient.modconfig.ModConfigPrefs.get();
        String q = search.getText().trim();
        List<ModConfigCard> shown = new ArrayList<>();
        int total = 0;
        for (ModConfigCard c : modConfigCards()) {
            ModEntry m = c.getMod();
            if (m.library() && !prefs.showLibraries() && !c.isFavorite()) {
                continue;
            }
            total++;
            boolean pass = switch (prefs.filter()) {
                case ALL -> true;
                case SETTINGS -> m.hasSettings();
                case FAVORITES -> c.isFavorite();
            };
            if (pass && (q.isEmpty() || c.matches(q))) {
                shown.add(c);
            }
        }
        // Stable sort keeps the registry order (with settings first, then by name) inside each group.
        shown.sort((a, b) -> Boolean.compare(b.isFavorite(), a.isFavorite()));
        modConfigToolbar.setSummary(total + " mods");
        visibleContent.addAll(shown);
        modConfigShown = shown.size();
    }

    private void requestRecollect() {
        recollect = true;
    }

    /** The Mod Configs card for a mod if it is currently listed, else null (used by the game test). */
    public ModConfigCard visibleModConfigCard(String modId) {
        for (UIComponent c : visibleContent) {
            if (c instanceof ModConfigCard card && card.getMod().id().equals(modId)) {
                return card;
            }
        }
        return null;
    }

    /** Visible Mod Configs cards, top to bottom (used by the game test). */
    public List<String> visibleModConfigIds() {
        List<String> out = new ArrayList<>();
        for (UIComponent c : visibleContent) {
            if (c instanceof ModConfigCard card) {
                out.add(card.getMod().id());
            }
        }
        return out;
    }

    public com.annoyingclient.gui.component.ModConfigToolbar modConfigToolbar() {
        return modConfigToolbar;
    }

    private void collectContent() {
        visibleContent.clear();
        search.setPlaceholder(tab == modConfigTab ? "Search mods..." : "Search modules...");
        if (tab == modConfigTab) {
            collectModConfigs();
        } else if (searching()) {
            for (Module m : AnnoyingClient.modules().search(search.getText())) {
                visibleContent.add(cards.get(m));
            }
        } else if (tab == wardrobeTab) {
            if (wardrobe == null) {
                wardrobe = new WardrobePanel();
            }
            visibleContent.add(wardrobe);
        } else if (tab == themeTab) {
            visibleContent.addAll(themeContent);
        } else if (tab == clientTab) {
            visibleContent.addAll(clientContent);
        } else {
            for (Module m : AnnoyingClient.modules().getByCategory(tabs.get(tab).category())) {
                visibleContent.add(cards.get(m));
            }
        }
    }

    // ---- rendering ---------------------------------------------------------------------------

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        float o = MathUtil.clamp(open.get(), 0f, 1f);
        if (minecraft != null && minecraft.level == null) {
            renderPanorama(g, partialTick);
        }
        if (AnnoyingClient.theme().blurEnabled() && o > 0.05f) {
            g.blurBeforeThisStratum();
        }
        int top = ColorUtil.fade(0x70000000, o);
        int bottom = ColorUtil.fade(ColorUtil.withAlpha(ColorUtil.shade(AnnoyingClient.theme().accent(), -0.7f), 0x80), o);
        g.fillGradient(0, 0, width, height, top, bottom);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (recollect) {
            recollect = false;
            collectContent();
        }
        String currentSearch = search.getText();
        if (!currentSearch.equals(lastSearch)) {
            lastSearch = currentSearch;
            content.snap(0.4f);
            content.setTarget(1f);
        }
        float o = open.update();
        if (closing && open.isDone()) {
            finishClose();
            return;
        }
        updateLayout();
        collectContent();
        localMouseX = toLocalX(mouseX);
        localMouseY = toLocalY(mouseY);

        ThemeManager t = AnnoyingClient.theme();
        Matrix3x2fStack pose = g.pose();
        pose.pushMatrix();
        pose.translate(winX + W * scale / 2f, winY + H * scale / 2f);
        float s = scale * openScale();
        pose.scale(s, s);
        pose.translate(-W / 2f, -H / 2f);
        RenderUtil.setGlobalAlpha(MathUtil.clamp(o, 0f, 1f));
        try {
            drawWindow(g, t);
            drawSidebar(g, t);
            drawHeader(g, t);
            drawContent(g, t);
            drawTooltip(g, t);
        } finally {
            RenderUtil.setGlobalAlpha(1f);
            pose.popMatrix();
        }
    }

    private void drawWindow(GuiGraphics g, ThemeManager t) {
        RenderUtil.shadow(g, 0, 0, W, H, 14, 0x90000000);
        RenderUtil.roundedRect(g, 0, 0, W, H, 9, t.background());
        RenderUtil.roundedRectSides(g, 0, 0, SIDEBAR, H, 9, t.backgroundDark(), true, false);
        RenderUtil.rect(g, SIDEBAR, 0, 0.6f, H, ColorUtil.withAlpha(t.border(), 150));
    }

    private void drawSidebar(GuiGraphics g, ThemeManager t) {
        // logo
        String brand = "ANNOYING";
        float bx = 14;
        for (int i = 0; i < brand.length(); i++) {
            String ch = brand.substring(i, i + 1);
            int color = ColorUtil.wave(t.accent(), t.accent2(), i * 120L, 1f);
            TextRenderer.drawScaled(g, ch, bx, 13, 1.25f, color, true);
            bx += TextRenderer.width(ch, true) * 1.25f;
        }
        TextRenderer.drawScaled(g, "C L I E N T", 14.5f, 27, 0.7f, t.textMuted(), true);
        String version = AnnoyingClient.getInstance() != null ? AnnoyingClient.getInstance().getVersion() : "dev";
        int plus = version.indexOf('+');
        if (plus > 0) {
            version = version.substring(0, plus);
        }
        TextRenderer.drawScaled(g, "v" + version, 14.5f + TextRenderer.width("C L I E N T", true) * 0.7f + 6, 27, 0.7f, t.textDisabled(), false);

        // tabs
        indicator.setTarget(searchingModules() ? -10 : tab);
        float ind = indicator.update();
        if (!searchingModules() || ind > -1) {
            float iy = tabYFor(ind);
            RenderUtil.roundedRect(g, 8, iy, SIDEBAR - 16, TAB_H, 5, ColorUtil.withAlpha(t.accent(), 46));
            RenderUtil.roundedRect(g, 8, iy + 6, 2.5f, TAB_H - 12, 1.25f, t.accent());
        }
        for (int i = 0; i < tabs.size(); i++) {
            Tab tb = tabs.get(i);
            float y = tabYFor(i);
            if (i == modConfigTab) {
                RenderUtil.rect(g, 16, y - 6, SIDEBAR - 32, 0.6f, ColorUtil.withAlpha(t.border(), 160));
            }
            boolean hovered = MathUtil.inside(localMouseX, localMouseY, 8, y, SIDEBAR - 16, TAB_H);
            Animation hv = tabHover.get(i);
            hv.setTarget(hovered ? 1f : 0f);
            float h = hv.update();
            if (h > 0.01f && i != tab) {
                RenderUtil.roundedRect(g, 8, y, SIDEBAR - 16, TAB_H, 5, ColorUtil.withAlpha(t.text(), Math.round(14 * h)));
            }
            boolean selected = i == tab && !searchingModules();
            int color = selected ? t.text() : ColorUtil.lerp(t.textMuted(), t.text(), h);
            Icons.draw(g, tb.icon(), 22 + h * 1.5f, y + TAB_H / 2f, 8, selected ? t.accent() : color);
            TextRenderer.draw(g, tb.name(), 32 + h * 1.5f, y + 7, color);
            if (tb.category() != null) {
                int count = countEnabled(tb.category());
                if (count > 0) {
                    String n = Integer.toString(count);
                    float nw = TextRenderer.width(n) * 0.75f + 6;
                    RenderUtil.roundedRect(g, SIDEBAR - 14 - nw, y + 6, nw, 10, 3, ColorUtil.withAlpha(t.accent(), 60));
                    TextRenderer.drawScaled(g, n, SIDEBAR - 14 - nw + 3, y + 8.3f, 0.75f, t.text(), false);
                }
            }
        }

        // HUD editor button
        float by = H - 30;
        boolean hover = MathUtil.inside(localMouseX, localMouseY, 10, by, SIDEBAR - 20, 20);
        hudButtonHover.setTarget(hover ? 1f : 0f);
        float hh = hudButtonHover.update();
        RenderUtil.roundedRect(g, 10, by, SIDEBAR - 20, 20, 5, ColorUtil.lerp(t.accent(), ColorUtil.shade(t.accent(), 0.15f), hh));
        String label = "HUD Editor";
        float lw = TextRenderer.width(label) + 13;
        Icons.draw(g, Icons.Icon.EDIT, SIDEBAR / 2f - lw / 2f + 4, by + 10, 7, 0xFFFFFFFF);
        TextRenderer.draw(g, label, SIDEBAR / 2f - lw / 2f + 13, by + 6, 0xFFFFFFFF);
    }

    private int countEnabled(Category category) {
        int n = 0;
        for (Module m : AnnoyingClient.modules().getByCategory(category)) {
            if (m.isEnabled()) {
                n++;
            }
        }
        return n;
    }

    private float tabTop() {
        return 46;
    }

    private float tabYFor(float index) {
        float y = tabTop() + index * (TAB_H + 2);
        if (index >= modConfigTab) {
            y += 12;
        }
        return y;
    }

    private void drawHeader(GuiGraphics g, ThemeManager t) {
        String title;
        String subtitle;
        if (searching() && tab == modConfigTab) {
            title = "Mod Configs";
            subtitle = modConfigShown + (modConfigShown == 1 ? " mod matches" : " mods match") + " \"" + search.getText().trim() + "\"";
        } else if (searching()) {
            title = "Search";
            int results = AnnoyingClient.modules().search(search.getText()).size();
            subtitle = results + (results == 1 ? " module matches" : " modules match") + " \"" + search.getText().trim() + "\"";
        } else {
            Tab tb = tabs.get(tab);
            title = tb.name();
            subtitle = tb.description();
        }
        float x = SIDEBAR + PAD + 2;
        TextRenderer.drawScaled(g, title, x, 11, 1.15f, t.text(), true);
        TextRenderer.draw(g, TextRenderer.trim(subtitle, 170), x, 25, t.textMuted());

        float sw = 140;
        search.setBounds(W - PAD - 20 - sw, 11, sw);
        search.render(g, localMouseX, localMouseY);

        // close button
        float cx = W - PAD - 7;
        float cy = 20;
        boolean hover = MathUtil.inside(localMouseX, localMouseY, cx - 7, cy - 7, 14, 14);
        closeHover.setTarget(hover ? 1f : 0f);
        float ch = closeHover.update();
        if (ch > 0.01f) {
            RenderUtil.circle(g, cx, cy, 7, ColorUtil.withAlpha(0xFFEF4444, Math.round(60 * ch)));
        }
        Icons.draw(g, Icons.Icon.CLOSE, cx, cy, 7, ColorUtil.lerp(t.textMuted(), 0xFFEF4444, ch));
        RenderUtil.rect(g, SIDEBAR + PAD, HEADER, W - SIDEBAR - PAD * 2, 0.6f, ColorUtil.withAlpha(t.border(), 120));
    }

    private void drawContent(GuiGraphics g, ThemeManager t) {
        float cx = contentX();
        float cy = contentY();
        float cw = contentW();
        float ch = contentH();

        // measure
        float total = 0;
        for (UIComponent c : visibleContent) {
            total += c.getHeight() + CONTENT_GAP;
        }
        contentHeight = Math.max(0, total - CONTENT_GAP);
        float maxScroll = Math.max(0, contentHeight - ch + 4);
        scrollTarget = MathUtil.clamp(scrollTarget, 0, maxScroll);
        scroll.setTarget(scrollTarget);
        float sc = scroll.update();

        float c = content.update();
        float baseAlpha = RenderUtil.getGlobalAlpha();
        RenderUtil.setGlobalAlpha(baseAlpha * MathUtil.clamp(c, 0f, 1f));
        float slide = (1f - c) * 10f;

        RenderUtil.scissor(g, cx - 2, cy, cw + 4, ch);
        float y = cy + 2 - sc + slide;
        boolean mouseInContent = MathUtil.inside(localMouseX, localMouseY, cx, cy, cw, ch);
        float mx = mouseInContent ? localMouseX : -10000;
        float my = mouseInContent ? localMouseY : -10000;
        for (UIComponent comp : visibleContent) {
            comp.setBounds(cx, y, cw);
            float h = comp.getHeight();
            if (y + h >= cy - 2 && y <= cy + ch + 2) {
                comp.render(g, mx, my);
            }
            y += h + CONTENT_GAP;
        }
        boolean noMods = tab == modConfigTab && modConfigShown == 0;
        if (visibleContent.isEmpty() || noMods) {
            String empty = !noMods ? searching() ? "No modules found" : "Nothing here yet"
                    : searching() ? "No mods match your search"
                    : com.annoyingclient.modconfig.ModConfigPrefs.get().filter() == com.annoyingclient.modconfig.ModConfigPrefs.Filter.FAVORITES
                    ? "No favorites yet: click the star on a mod" : "No mods to show";
            TextRenderer.draw(g, empty, cx + cw / 2f - TextRenderer.width(empty) / 2f, cy + 30, t.textMuted());
        }
        RenderUtil.endScissor(g);
        RenderUtil.setGlobalAlpha(baseAlpha);

        // scrollbar
        if (maxScroll > 0) {
            float trackX = cx + cw + 2;
            float barH = Math.max(16, ch * ch / (contentHeight + 4));
            float barY = cy + (ch - barH) * (sc / maxScroll);
            RenderUtil.roundedRect(g, trackX, cy, 2, ch, 1, ColorUtil.withAlpha(t.text(), 14));
            RenderUtil.roundedRect(g, trackX, barY, 2, barH, 1, ColorUtil.withAlpha(t.accent(), 180));
        }
    }

    private void drawTooltip(GuiGraphics g, ThemeManager t) {
        String tip = null;
        if (MathUtil.inside(localMouseX, localMouseY, contentX(), contentY(), contentW(), contentH())) {
            for (UIComponent c : visibleContent) {
                if (c.isHovered(localMouseX, localMouseY)) {
                    tip = c.getTooltip(localMouseX, localMouseY);
                    break;
                }
            }
        } else {
            for (int i = 0; i < tabs.size(); i++) {
                if (MathUtil.inside(localMouseX, localMouseY, 8, tabYFor(i), SIDEBAR - 16, TAB_H)) {
                    tip = tabs.get(i).description();
                }
            }
        }
        if (tip == null || tip.isEmpty()) {
            return;
        }
        float tw = TextRenderer.width(tip) + 10;
        float tx = MathUtil.clamp(localMouseX + 8, 4, W - tw - 4);
        float ty = MathUtil.clamp(localMouseY + 12, 4, H - 18);
        RenderUtil.shadow(g, tx, ty, tw, 14, 4, 0x70000000);
        RenderUtil.roundedRect(g, tx, ty, tw, 14, 4, ColorUtil.withAlpha(ColorUtil.shade(t.background(), 0.12f), 245));
        TextRenderer.draw(g, tip, tx + 5, ty + 3, t.text());
    }

    // ---- geometry (used by automated tests and anything that needs to point at the window) --

    /** Converts window-local layout coordinates to screen GUI coordinates (once the open animation has finished). */
    public float[] localToScreen(float localX, float localY) {
        return new float[]{winX + localX * scale, winY + localY * scale};
    }

    /** Local Y of the sidebar tab at {@code index} (categories first, then Themes and Client). */
    public float getTabY(int index) {
        return tabYFor(index);
    }

    /** Scrolls the content area by {@code amount} (positive scrolls down), as the mouse wheel does. */
    public void scrollContent(float amount) {
        scrollTarget += amount;
    }

    public boolean isFullyOpen() {
        return !closing && open.get() >= 1f;
    }

    // ---- input -------------------------------------------------------------------------------

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (closing) {
            return true;
        }
        float mx = toLocalX(event.x());
        float my = toLocalY(event.y());
        int button = event.button();

        if (!MathUtil.inside(mx, my, 0, 0, W, H)) {
            unfocusAll();
            return true;
        }
        boolean onSearch = search.mouseClicked(mx, my, button);
        if (onSearch) {
            for (UIComponent c : visibleContent) {
                c.unfocus();
            }
            return true;
        }
        // close button
        if (MathUtil.inside(mx, my, W - PAD - 14, 13, 14, 14) && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            onClose();
            return true;
        }
        // tabs
        for (int i = 0; i < tabs.size(); i++) {
            if (MathUtil.inside(mx, my, 8, tabYFor(i), SIDEBAR - 16, TAB_H) && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                switchTab(i);
                return true;
            }
        }
        // HUD editor
        if (MathUtil.inside(mx, my, 10, H - 30, SIDEBAR - 20, 20) && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            savedX = winX;
            savedY = winY;
            lastTab = tab;
            unfocusAll();
            if (minecraft != null) {
                minecraft.setScreen(new HudEditorScreen(this));
            }
            return true;
        }
        // content
        if (MathUtil.inside(mx, my, contentX() - 2, contentY(), contentW() + 4, contentH())) {
            boolean handled = false;
            for (UIComponent c : visibleContent) {
                if (!handled && c.mouseClicked(mx, my, button)) {
                    handled = true;
                } else {
                    c.unfocus();
                }
            }
            return true;
        }
        for (UIComponent c : visibleContent) {
            c.unfocus();
        }
        // drag the window by the header or logo area
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && (my < HEADER || (mx < SIDEBAR && my < tabTop() - 4))) {
            draggingWindow = true;
            dragOffsetX = (float) event.x() - winX;
            dragOffsetY = (float) event.y() - winY;
        }
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (draggingWindow) {
            draggingWindow = false;
            savedX = winX;
            savedY = winY;
        }
        float mx = toLocalX(event.x());
        float my = toLocalY(event.y());
        for (UIComponent c : visibleContent) {
            c.mouseReleased(mx, my, event.button());
        }
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (draggingWindow) {
            winX = (float) event.x() - dragOffsetX;
            winY = (float) event.y() - dragOffsetY;
            return true;
        }
        float mx = toLocalX(event.x());
        float my = toLocalY(event.y());
        for (UIComponent c : visibleContent) {
            c.mouseDragged(mx, my, event.button());
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        float mx = toLocalX(mouseX);
        float my = toLocalY(mouseY);
        if (MathUtil.inside(mx, my, contentX(), contentY(), contentW() + 6, contentH())) {
            scrollTarget -= (float) vertical * 26f;
            return true;
        }
        if (MathUtil.inside(mx, my, 0, tabTop(), SIDEBAR, tabYFor(tabs.size()) - tabTop()) && vertical != 0) {
            int next = MathUtil.clamp(tab + (vertical < 0 ? 1 : -1), 0, tabs.size() - 1);
            switchTab(next);
            return true;
        }
        return false;
    }

    private UIComponent capturing() {
        for (UIComponent c : visibleContent) {
            if (c.isCapturingInput()) {
                return c;
            }
        }
        return null;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (closing) {
            return true;
        }
        UIComponent focused = capturing();
        if (focused != null && focused.keyPressed(event)) {
            return true;
        }
        if (search.isFocused()) {
            if (event.key() == GLFW.GLFW_KEY_ESCAPE && !search.getText().isEmpty()) {
                search.setText("");
                return true;
            }
            if (search.keyPressed(event)) {
                return true;
            }
        }
        int key = event.key();
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            if (!search.getText().isEmpty()) {
                search.setText("");
                return true;
            }
            onClose();
            return true;
        }
        if (key == GLFW.GLFW_KEY_F && event.hasControlDownWithQuirk()) {
            search.setFocused(true);
            return true;
        }
        ClickGuiModule gui = AnnoyingClient.modules().get(ClickGuiModule.class);
        if (gui != null && gui.getKeybind().matchesKey(key) && System.currentTimeMillis() - openedAt > 150) {
            onClose();
            return true;
        }
        if (key == GLFW.GLFW_KEY_TAB) {
            switchTab((tab + (event.hasShiftDown() ? tabs.size() - 1 : 1)) % tabs.size());
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (closing) {
            return true;
        }
        int cp = event.codepoint();
        UIComponent focused = capturing();
        if (focused != null) {
            return focused.charTyped(cp);
        }
        if (!search.isFocused() && cp > ' ' && Character.isLetterOrDigit(cp)) {
            search.setFocused(true); // start typing anywhere to search
        }
        return search.charTyped(cp);
    }

    @Override
    public void removed() {
        unfocusAll();
    }
}
