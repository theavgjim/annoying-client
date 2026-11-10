package com.annoyingclient.gui.component;

import com.annoyingclient.AnnoyingClient;
import com.annoyingclient.cosmetics.Cosmetic;
import com.annoyingclient.cosmetics.CosmeticsManager;
import com.annoyingclient.module.impl.visual.CosmeticsModule;
import com.annoyingclient.render.Icons;
import com.annoyingclient.render.RenderUtil;
import com.annoyingclient.render.TextRenderer;
import com.annoyingclient.setting.ModeSetting;
import com.annoyingclient.setting.StringSetting;
import com.annoyingclient.util.Animation;
import com.annoyingclient.util.ColorUtil;
import com.annoyingclient.util.MathUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2f;
import org.joml.Quaternionf;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The Wardrobe tab: coin balance and daily reward, a turnable preview of your player, the cosmetics
 * grid (buy, equip, unequip), and admin tools for admins. Without a server it works as a local
 * preview only you can see.
 */
public class WardrobePanel extends UIComponent {

    private static final float STATUS_H = 40;
    private static final float STATUS_H_SETUP = 62;
    private static final float PREVIEW_W = 112;
    private static final float CARD_H = 50;
    private static final float GAP = 6;

    private final TextField urlField = new TextField("https://your-cosmetics-server.example.com", 200, 16);
    private final Button connect = new Button("Connect", true, 16, this::connect);
    private final Button daily = new Button("Daily +50", true, 16, this::daily);
    private final Button signIn = new Button("Sign in", true, 16, () -> cos().signIn(this::report));
    private final Map<String, Animation> hover = new HashMap<>();
    private String confirmBuy;
    private float yaw = 25; // start turned away, so the cape shows
    private boolean dragging;
    private float dragStartX;
    private float yawStart;
    private boolean busy;

    // admin tools
    private final StringSetting adminPlayer = new StringSetting("Player", "Minecraft username", "", 16);
    private final StringSetting adminAmount = new StringSetting("Coins", "Amount of coins", "100", 9);
    private final ModeSetting adminCosmetic;
    private final StringSetting adminMessage = new StringSetting("Announcement", "Shown to every signed-in player", "", 200);
    private final List<SettingComponent<?>> adminFields = new ArrayList<>();
    /** "Thin capes" and "Wavy capes", under the preview. */
    private final List<SettingComponent<?>> capeToggles = new ArrayList<>();
    private final List<Button> adminButtons = new ArrayList<>();
    private final Button sendAnnouncement;
    private final Button refreshPlayers;
    private List<String> players = List.of();

    public WardrobePanel() {
        String[] names = Cosmetic.BUILTIN.stream().map(Cosmetic::name).toArray(String[]::new);
        adminCosmetic = new ModeSetting("Cosmetic", "Cosmetic to give or remove", names[0], names);
        adminFields.add(SettingComponent.create(adminPlayer));
        adminFields.add(SettingComponent.create(adminAmount));
        adminFields.add(SettingComponent.create(adminCosmetic));
        adminButtons.add(new Button("Give coins", true, 16, () -> coins(1)));
        adminButtons.add(new Button("Take coins", false, 16, () -> coins(-1)));
        adminButtons.add(new Button("Give cosmetic", true, 16, () -> cosmetic(true)));
        adminButtons.add(new Button("Remove cosmetic", false, 16, () -> cosmetic(false)));
        adminButtons.add(new Button("Make admin", false, 16, () -> role(true)));
        adminButtons.add(new Button("Remove admin", false, 16, () -> role(false)));
        adminFields.add(SettingComponent.create(adminMessage));
        sendAnnouncement = new Button("Send announcement", true, 16, this::announce);
        refreshPlayers = new Button("Show players", false, 16, () -> cos().adminPlayers(list -> players = list));
        CosmeticsModule m = module();
        if (m != null) {
            urlField.setText(m.serverUrlSetting());
            capeToggles.add(SettingComponent.create(m.thinCapesSetting(), "Thin capes"));
            capeToggles.add(SettingComponent.create(m.wavyCapesSetting(), "Wavy capes"));
        }
    }

    /** Turns the player preview (used by the game test for side views). */
    public void setPreviewYaw(float yaw) {
        this.yaw = yaw;
    }

    private static CosmeticsManager cos() {
        return AnnoyingClient.cosmetics();
    }

    private static CosmeticsModule module() {
        return AnnoyingClient.modules().get(CosmeticsModule.class);
    }

    private void report(String err) {
        busy = false;
        if (err != null) {
            AnnoyingClient.notifications().error("Cosmetics", err);
        }
    }

    private void connect() {
        String url = urlField.getText().trim();
        String problem = CosmeticsManager.checkUrl(url);
        if (url.isEmpty() || problem != null) {
            AnnoyingClient.notifications().error("Can't use that address", url.isEmpty() ? "Enter your server's address" : problem);
            return;
        }
        module().setServerUrl(url);
        AnnoyingClient.notifications().info("Connecting", url);
    }

    private void daily() {
        busy = true;
        cos().claimDaily(err -> {
            report(err);
            if (err == null) {
                AnnoyingClient.notifications().success("Daily reward", "+50 coins");
            }
        });
    }

    private String selectedCosmeticId() {
        for (Cosmetic c : Cosmetic.BUILTIN) {
            if (c.name().equals(adminCosmetic.getValue())) {
                return c.id();
            }
        }
        return "";
    }

    private void adminDone(String what, String err) {
        if (err == null) {
            AnnoyingClient.notifications().success("Done", what);
        } else {
            AnnoyingClient.notifications().error("Admin action failed", err);
        }
    }

    private void coins(int sign) {
        int amount;
        try {
            amount = Integer.parseInt(adminAmount.getValue().trim());
        } catch (NumberFormatException e) {
            AnnoyingClient.notifications().error("Enter a whole number of coins", "");
            return;
        }
        String player = adminPlayer.getValue().trim();
        cos().admin("coins", Map.of("player", player, "amount", amount * sign),
                err -> adminDone((sign > 0 ? "Gave " : "Took ") + amount + " coins " + (sign > 0 ? "to " : "from ") + player, err));
    }

    private void cosmetic(boolean give) {
        String player = adminPlayer.getValue().trim();
        cos().admin("cosmetic", Map.of("player", player, "id", selectedCosmeticId(), "give", give),
                err -> adminDone((give ? "Gave " : "Removed ") + adminCosmetic.getValue() + (give ? " to " : " from ") + player, err));
    }

    private void role(boolean admin) {
        String player = adminPlayer.getValue().trim();
        cos().admin("role", Map.of("player", player, "admin", admin), err -> adminDone(player + (admin ? " is now an admin" : " is no longer an admin"), err));
    }

    private void announce() {
        String msg = adminMessage.getValue().trim();
        cos().admin("announce", Map.of("message", msg), err -> {
            adminDone("Announcement sent", err);
            if (err == null) {
                adminMessage.setValue("");
            }
        });
    }

    // ---- layout -------------------------------------------------------------------------------

    private boolean admin() {
        CosmeticsManager.Profile p = cos().profile();
        return p != null && p.admin();
    }

    private float gridX() {
        return x + PREVIEW_W + 10;
    }

    private float gridW() {
        return width - PREVIEW_W - 10;
    }

    private float cardW() {
        return (gridW() - GAP) / 2f;
    }

    private float statusH() {
        return cos().status() == CosmeticsManager.Status.NO_SERVER ? STATUS_H_SETUP : STATUS_H;
    }

    private float gridTop() {
        return y + statusH() + 10;
    }

    private float gridHeight() {
        int rows = (cos().catalog().size() + 1) / 2;
        return rows * CARD_H + (rows - 1) * GAP;
    }

    private float previewColumnHeight() {
        float h = 170;
        for (SettingComponent<?> f : capeToggles) {
            h += 4 + f.getHeight();
        }
        return h;
    }

    private float adminTop() {
        return gridTop() + Math.max(gridHeight(), previewColumnHeight()) + 16;
    }

    private float adminHeight() {
        if (!admin()) {
            return 0;
        }
        float h = 20;
        for (SettingComponent<?> f : adminFields) {
            h += f.getHeight() + 4;
        }
        h += 3 * 20 + 24 + 22 + players.size() * 11;
        return h;
    }

    @Override
    public float getHeight() {
        return adminTop() - y + adminHeight();
    }

    // ---- rendering ----------------------------------------------------------------------------

    @Override
    public void render(GuiGraphics g, float mouseX, float mouseY) {
        renderStatus(g, mouseX, mouseY);
        renderPreview(g, mouseX, mouseY);
        renderGrid(g, mouseX, mouseY);
        if (admin()) {
            renderAdmin(g, mouseX, mouseY);
        }
    }

    private void renderStatus(GuiGraphics g, float mouseX, float mouseY) {
        var t = theme();
        CosmeticsManager c = cos();
        RenderUtil.roundedRect(g, x, y, width, statusH(), 6, t.surface());
        if (c.status() == CosmeticsManager.Status.NO_SERVER) {
            TextRenderer.drawBold(g, "Preview mode", x + 10, y + 7, t.text());
            TextRenderer.draw(g, TextRenderer.trim("Only you see these. Connect your server to sync them and earn coins.", (int) width - 20),
                    x + 10, y + 19, t.textMuted());
            urlField.setBounds(x + 10, y + 36, width - 20 - 62);
            connect.setBounds(x + width - 66, y + 36, 56);
            urlField.render(g, mouseX, mouseY);
            connect.render(g, mouseX, mouseY);
            return;
        }
        CosmeticsManager.Profile p = c.profile();
        Icons.draw(g, Icons.Icon.COIN, x + 18, y + STATUS_H / 2f, 14, 0xFFF5BE3C);
        if (p != null) {
            TextRenderer.drawScaled(g, p.coins() + " coins", x + 32, y + 8, 1.2f, t.text(), true);
            TextRenderer.draw(g, "Signed in as " + p.name() + (p.owner() ? "  ·  Owner" : p.admin() ? "  ·  Admin" : ""), x + 32, y + 24, t.textMuted());
            if (p.dailyReady()) {
                daily.setBounds(x + width - 76, y + 12, 68);
                daily.render(g, mouseX, mouseY);
            } else {
                String next = "Daily reward claimed";
                TextRenderer.draw(g, next, x + width - 10 - TextRenderer.width(next), y + 16, t.textDisabled());
            }
        } else {
            String line = switch (c.status()) {
                case SIGNING_IN -> "Signing in...";
                case ERROR -> TextRenderer.trim(c.error(), (int) width - 120);
                default -> "Not signed in";
            };
            TextRenderer.drawBold(g, "Cosmetics server", x + 32, y + 8, t.text());
            TextRenderer.draw(g, line, x + 32, y + 22, c.status() == CosmeticsManager.Status.ERROR ? 0xFFF87171 : t.textMuted());
            if (c.status() != CosmeticsManager.Status.SIGNING_IN) {
                signIn.setLabel(c.status() == CosmeticsManager.Status.ERROR ? "Retry" : "Sign in");
                signIn.setBounds(x + width - 66, y + 12, 58);
                signIn.render(g, mouseX, mouseY);
            }
        }
    }

    private void renderPreview(GuiGraphics g, float mouseX, float mouseY) {
        float ty = gridTop() + 170;
        for (SettingComponent<?> f : capeToggles) {
            ty += 4;
            f.setBounds(x, ty, PREVIEW_W);
            f.render(g, mouseX, mouseY);
            ty += f.getHeight();
        }
        renderPlayer(g, mouseX, mouseY);
    }

    private void renderPlayer(GuiGraphics g, float mouseX, float mouseY) {
        var t = theme();
        float px = x, py = gridTop(), pw = PREVIEW_W, ph = 170;
        RenderUtil.roundedRect(g, px, py, pw, ph, 6, t.surface());
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            String msg = "Join a world";
            TextRenderer.draw(g, msg, px + pw / 2f - TextRenderer.width(msg) / 2f, py + ph / 2f - 10, t.textMuted());
            String msg2 = "to see the preview";
            TextRenderer.draw(g, msg2, px + pw / 2f - TextRenderer.width(msg2) / 2f, py + ph / 2f + 2, t.textMuted());
            return;
        }
        if (dragging) {
            yaw = yawStart + (mouseX - dragStartX) * 2f;
        }
        // GUI entity rendering takes screen coordinates, so map the box through the current pose.
        Matrix3x2f m = g.pose();
        Vector2f a = m.transformPosition(px + 4, py + 4, new Vector2f());
        Vector2f b = m.transformPosition(px + pw - 4, py + ph - 18, new Vector2f());
        float scale = (float) Math.sqrt(m.m00 * m.m00 + m.m01 * m.m01);
        var renderer = mc.getEntityRenderDispatcher().getRenderer(mc.player);
        EntityRenderState state = renderer.createRenderState(mc.player, 1.0F);
        state.lightCoords = 15728880;
        state.shadowPieces.clear();
        state.outlineColor = 0;
        if (state instanceof LivingEntityRenderState living) {
            living.bodyRot = yaw;
            living.yRot = 0;
            living.xRot = 0;
            living.boundingBoxWidth = living.boundingBoxWidth / living.scale;
            living.boundingBoxHeight = living.boundingBoxHeight / living.scale;
            living.scale = 1.0F;
        }
        Quaternionf pose = new Quaternionf().rotateZ((float) Math.PI);
        Vector3f offset = new Vector3f(0.0F, state.boundingBoxHeight / 2.0F + 0.0625F, 0.0F);
        g.submitEntityRenderState(state, 64 * scale, offset, pose, new Quaternionf(), (int) a.x, (int) a.y, (int) b.x, (int) b.y);
        String hint = "Drag to turn";
        TextRenderer.drawScaled(g, hint, px + pw / 2f - TextRenderer.width(hint) * 0.375f, py + ph - 13, 0.75f, t.textDisabled(), false);
    }

    private boolean owned(Cosmetic c) {
        CosmeticsManager.Profile p = cos().profile();
        return p != null && p.owned().contains(c.id());
    }

    private boolean equipped(Cosmetic c) {
        CosmeticsManager.Profile p = cos().profile();
        if (p != null) {
            return c.id().equals(p.equipped().get(c.type()));
        }
        CosmeticsModule m = module();
        return m != null && c.id().equals(c.isCape() ? m.previewCape() : m.previewTrail());
    }

    private void renderGrid(GuiGraphics g, float mouseX, float mouseY) {
        var t = theme();
        List<Cosmetic> list = cos().catalog();
        boolean preview = cos().status() == CosmeticsManager.Status.NO_SERVER;
        for (int i = 0; i < list.size(); i++) {
            Cosmetic c = list.get(i);
            float cx = gridX() + (i % 2) * (cardW() + GAP);
            float cy = gridTop() + (i / 2) * (CARD_H + GAP);
            boolean hovered = MathUtil.inside(mouseX, mouseY, cx, cy, cardW(), CARD_H);
            Animation an = hover.computeIfAbsent(c.id(), k -> new Animation(0f, 16f));
            an.setTarget(hovered ? 1f : 0f);
            float h = an.update();
            boolean eq = equipped(c);
            RenderUtil.roundedRect(g, cx, cy, cardW(), CARD_H, 6, ColorUtil.lerp(t.surface(), t.surfaceHover(), h));
            if (eq) {
                RenderUtil.roundedOutline(g, cx, cy, cardW(), CARD_H, 6, 1.1f, t.accent());
            }
            // thumbnail
            if (c.isCape()) {
                Identifier tex = Identifier.fromNamespaceAndPath(AnnoyingClient.MOD_ID, "textures/cape/" + c.id() + ".png");
                RenderUtil.roundedRect(g, cx + 7, cy + 7, 24, 36, 3, 0x50000000);
                g.blit(RenderPipelines.GUI_TEXTURED, tex, (int) (cx + 9), (int) (cy + 9), 4, 4, 20, 32, 40, 64, 256, 128,
                        ColorUtil.withAlpha(0xFFFFFF, Math.round(255 * RenderUtil.getGlobalAlpha())));
            } else {
                int color = switch (c.id()) {
                    case "trail_hearts" -> 0xFFF43F5E;
                    case "trail_flames" -> 0xFFF97316;
                    case "trail_sparkle" -> 0xFFFDE68A;
                    default -> 0xFF34D399;
                };
                RenderUtil.roundedRect(g, cx + 7, cy + 7, 24, 36, 3, 0x30000000);
                for (int k = 0; k < 4; k++) {
                    RenderUtil.circle(g, cx + 13 + (k % 2) * 10, cy + 13 + k * 7.5f, 2.2f + (k % 2) * 0.6f, ColorUtil.withAlpha(color, 255 - k * 45));
                }
            }
            float tx = cx + 38;
            int maxW = (int) (cardW() - 44);
            TextRenderer.drawBold(g, TextRenderer.trim(c.name(), maxW), tx, cy + 7, t.text());
            TextRenderer.drawScaled(g, c.rarity() + "  ·  " + (c.isCape() ? "Cape" : "Trail"), tx, cy + 19, 0.75f, c.rarityColor(), false);
            String state;
            int stateColor = t.textMuted();
            if (eq) {
                state = preview ? "Previewing" : "Equipped";
                stateColor = t.accent();
            } else if (preview) {
                state = "Click to preview";
            } else if (owned(c)) {
                state = "Owned";
            } else if (c.price() < 0) {
                state = "Staff only";
                stateColor = t.textDisabled();
            } else if (c.id().equals(confirmBuy)) {
                state = "Click again to buy";
                stateColor = 0xFFF5BE3C;
            } else {
                state = c.price() == 0 ? "Free" : c.price() + " coins";
                stateColor = 0xFFF5BE3C;
            }
            TextRenderer.draw(g, TextRenderer.trim(state, maxW), tx, cy + 32, stateColor);
        }
    }

    private void renderAdmin(GuiGraphics g, float mouseX, float mouseY) {
        var t = theme();
        float ay = adminTop();
        TextRenderer.drawBold(g, "Admin tools", x, ay, t.text());
        String note = "Every action is checked by the server and logged.";
        TextRenderer.draw(g, note, x + TextRenderer.width("Admin tools", true) + 10, ay, t.textMuted());
        float fy = ay + 16;
        int i = 0;
        for (SettingComponent<?> f : adminFields) {
            f.setBounds(x, fy, width);
            f.render(g, mouseX, mouseY);
            fy += f.getHeight() + 4;
            if (++i == 3) {
                // action buttons after the player, coins and cosmetic fields
                float bw = (width - 2 * GAP) / 3f;
                for (int k = 0; k < adminButtons.size(); k++) {
                    Button b = adminButtons.get(k);
                    b.setBounds(x + (k % 3) * (bw + GAP), fy + (k / 3) * 20, bw);
                    b.render(g, mouseX, mouseY);
                }
                fy += 2 * 20 + 6;
            }
        }
        sendAnnouncement.setBounds(x, fy, 140);
        sendAnnouncement.render(g, mouseX, mouseY);
        refreshPlayers.setBounds(x + 146, fy, 100);
        refreshPlayers.render(g, mouseX, mouseY);
        fy += 24;
        for (String line : players) {
            TextRenderer.draw(g, line, x + 4, fy, t.textMuted());
            fy += 11;
        }
    }

    // ---- input --------------------------------------------------------------------------------

    @Override
    public boolean mouseClicked(float mouseX, float mouseY, int button) {
        if (!isHovered(mouseX, mouseY)) {
            unfocus();
            return false;
        }
        CosmeticsManager c = cos();
        if (c.status() == CosmeticsManager.Status.NO_SERVER) {
            if (urlField.mouseClicked(mouseX, mouseY, button) || connect.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
        } else if (c.profile() != null) {
            if (c.profile().dailyReady() && !busy && daily.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
        } else if (c.status() != CosmeticsManager.Status.SIGNING_IN && signIn.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        // preview drag
        if (MathUtil.inside(mouseX, mouseY, x, gridTop(), PREVIEW_W, 170) && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            dragging = true;
            dragStartX = mouseX;
            yawStart = yaw;
            return true;
        }
        for (SettingComponent<?> f : capeToggles) {
            if (f.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
        }
        // cosmetics grid
        List<Cosmetic> list = c.catalog();
        for (int i = 0; i < list.size(); i++) {
            float cx = gridX() + (i % 2) * (cardW() + GAP);
            float cy = gridTop() + (i / 2) * (CARD_H + GAP);
            if (MathUtil.inside(mouseX, mouseY, cx, cy, cardW(), CARD_H) && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                clickCosmetic(list.get(i));
                return true;
            }
        }
        if (admin()) {
            for (SettingComponent<?> f : adminFields) {
                if (f.mouseClicked(mouseX, mouseY, button)) {
                    return true;
                } else if (!f.isHovered(mouseX, mouseY)) {
                    f.unfocus();
                }
            }
            for (Button b : adminButtons) {
                if (b.mouseClicked(mouseX, mouseY, button)) {
                    return true;
                }
            }
            if (sendAnnouncement.mouseClicked(mouseX, mouseY, button) || refreshPlayers.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
        }
        urlField.unfocus();
        return true;
    }

    private void clickCosmetic(Cosmetic cos) {
        CosmeticsManager c = cos();
        if (c.status() == CosmeticsManager.Status.NO_SERVER) {
            module().setPreview(cos.type(), equipped(cos) ? null : cos.id());
            return;
        }
        if (c.profile() == null || busy) {
            return;
        }
        if (owned(cos)) {
            busy = true;
            c.equip(cos.type(), equipped(cos) ? null : cos.id(), this::report);
            confirmBuy = null;
        } else if (cos.price() >= 0) {
            if (cos.id().equals(confirmBuy)) {
                busy = true;
                confirmBuy = null;
                c.buy(cos.id(), err -> {
                    report(err);
                    if (err == null) {
                        AnnoyingClient.notifications().success("Bought " + cos.name(), "Click it again to equip it");
                    }
                });
            } else {
                confirmBuy = cos.id();
            }
        }
    }

    @Override
    public void mouseReleased(float mouseX, float mouseY, int button) {
        dragging = false;
        for (SettingComponent<?> f : adminFields) {
            f.mouseReleased(mouseX, mouseY, button);
        }
    }

    private List<UIComponent> inputs() {
        List<UIComponent> out = new ArrayList<>(adminFields);
        out.add(urlField);
        return out;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (urlField.isFocused() && (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER)) {
            connect();
            urlField.unfocus();
            return true;
        }
        for (UIComponent c : inputs()) {
            if (c.isCapturingInput() && c.keyPressed(event)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean charTyped(int codepoint) {
        for (UIComponent c : inputs()) {
            if (c.isCapturingInput() && c.charTyped(codepoint)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean isCapturingInput() {
        for (UIComponent c : inputs()) {
            if (c.isCapturingInput()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void unfocus() {
        for (UIComponent c : inputs()) {
            c.unfocus();
        }
        confirmBuy = null;
    }

    @Override
    public String getTooltip(float mouseX, float mouseY) {
        List<Cosmetic> list = cos().catalog();
        for (int i = 0; i < list.size(); i++) {
            float cx = gridX() + (i % 2) * (cardW() + GAP);
            float cy = gridTop() + (i / 2) * (CARD_H + GAP);
            if (MathUtil.inside(mouseX, mouseY, cx, cy, cardW(), CARD_H)) {
                return list.get(i).name();
            }
        }
        return null;
    }
}
