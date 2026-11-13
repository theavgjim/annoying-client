package com.annoyingclient.gametest;

import com.annoyingclient.AnnoyingClient;
import com.annoyingclient.gui.clickgui.ClickGuiScreen;
import com.annoyingclient.hud.HudEditorScreen;
import com.annoyingclient.hud.HudModule;
import com.annoyingclient.hud.element.WatermarkHud;
import com.annoyingclient.module.impl.visual.CrosshairModule;
import com.annoyingclient.module.impl.visual.FullbrightModule;
import com.annoyingclient.module.impl.visual.ScreenshotModule;
import com.annoyingclient.module.impl.visual.ZoomModule;
import com.annoyingclient.setting.KeybindSetting;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * End-to-end test of Annoying Client inside a real Minecraft client: creates a world, uses the
 * ClickGUI with the mouse and keyboard, edits the HUD, checks keybinds, zoom, fullbright,
 * profiles and corrupted-config recovery, and saves screenshots of every step.
 */
public class AnnoyingClientGameTest implements FabricClientGameTest {

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    @Override
    public void runTest(ClientGameTestContext ctx) {
        ctx.getInput().resizeWindow(1600, 900);
        ctx.waitTicks(10);
        ctx.takeScreenshot("annoying-01-title-screen");
        // This machine renders on the CPU, so keep world generation and rendering cheap.
        ctx.runOnClient(mc -> {
            mc.options.renderDistance().set(4);
            mc.options.simulationDistance().set(5);
            mc.options.menuBackgroundBlurriness().set(0);
            ((com.annoyingclient.setting.BooleanSetting) AnnoyingClient.theme().getSettings().stream()
                    .filter(st -> st.getName().equals("Background Blur")).findFirst().orElseThrow()).setValue(false);
        });

        TestSingleplayerContext created = ctx.worldBuilder().adjustSettings(state -> {
            for (WorldCreationUiState.WorldTypeEntry entry : state.getNormalPresetList()) {
                if (entry.preset() != null && entry.preset().is(WorldPresets.FLAT)) {
                    state.setWorldType(entry);
                }
            }
            state.setGenerateStructures(false);
        }).create();
        try (TestSingleplayerContext world = created) {
            world.getClientWorld().waitForChunksRender();
            world.getServer().runCommand("time set noon");
            ctx.waitTicks(30);
            ctx.takeScreenshot("annoying-02-hud-default");

            // Enable every HUD element and wear some armor so the Armor HUD has something to show.
            world.getServer().runCommand("item replace entity @a armor.head with minecraft:diamond_helmet");
            world.getServer().runCommand("item replace entity @a armor.chest with minecraft:iron_chestplate");
            world.getServer().runCommand("item replace entity @a armor.legs with minecraft:golden_leggings");
            world.getServer().runCommand("item replace entity @a armor.feet with minecraft:leather_boots");
            ctx.runOnClient(mc -> {
                for (HudModule m : AnnoyingClient.hud().getElements()) {
                    m.setEnabled(true, false);
                }
            });
            ctx.getInput().holdKey(GLFW.GLFW_KEY_W);
            ctx.waitTicks(10);
            ctx.takeScreenshot("annoying-03-hud-all-elements");
            ctx.getInput().releaseKey(GLFW.GLFW_KEY_W);

            testClickGui(ctx);
            testCosmetics(ctx);
            testSaturationAndEffects(ctx, world);
            testKeybindAndZoom(ctx, world);
            testHudEditor(ctx);
            testConfig(ctx);
        }
    }

    // ---- ClickGUI ----------------------------------------------------------------------------

    private void click(ClientGameTestContext ctx, float localX, float localY, int button) {
        float[] gui = ctx.computeOnClient(mc -> ((ClickGuiScreen) mc.screen).localToScreen(localX, localY));
        double scale = ctx.computeOnClient(mc -> (double) mc.getWindow().getGuiScale());
        ctx.getInput().setCursorPos(gui[0] * scale, gui[1] * scale);
        ctx.waitTick();
        ctx.getInput().pressMouse(button);
        ctx.waitTicks(2);
    }

    private void clickTab(ClientGameTestContext ctx, int index) {
        float y = ctx.computeOnClient(mc -> ((ClickGuiScreen) mc.screen).getTabY(index));
        click(ctx, 60, y + 11, GLFW.GLFW_MOUSE_BUTTON_LEFT);
        ctx.waitTicks(8);
    }

    private void testClickGui(ClientGameTestContext ctx) {
        ctx.getInput().pressKey(GLFW.GLFW_KEY_RIGHT_SHIFT);
        ctx.waitForScreen(ClickGuiScreen.class);
        ctx.waitFor(mc -> mc.screen instanceof ClickGuiScreen s && s.isFullyOpen());
        ctx.waitTicks(4);
        ctx.takeScreenshot("annoying-04-clickgui-render-tab");

        // Visual category: Fullbright is the first card.
        clickTab(ctx, 3);
        ctx.takeScreenshot("annoying-05-clickgui-visual-tab");
        boolean before = ctx.computeOnClient(mc -> AnnoyingClient.modules().get(FullbrightModule.class).isEnabled());
        click(ctx, 180, 48 + 15, GLFW.GLFW_MOUSE_BUTTON_LEFT);
        boolean after = ctx.computeOnClient(mc -> AnnoyingClient.modules().get(FullbrightModule.class).isEnabled());
        check(after != before, "Clicking the Fullbright card should toggle it");
        click(ctx, 180, 48 + 15, GLFW.GLFW_MOUSE_BUTTON_RIGHT); // expand settings
        ctx.waitTicks(10);
        ctx.takeScreenshot("annoying-06-clickgui-module-settings");

        // Custom Crosshair is the second card: expand it and open its color picker.
        click(ctx, 180, 48 + 15, GLFW.GLFW_MOUSE_BUTTON_RIGHT); // collapse Fullbright again
        ctx.waitTicks(10);
        click(ctx, 180, 48 + 35 + 15, GLFW.GLFW_MOUSE_BUTTON_RIGHT);
        ctx.waitTicks(10);
        ctx.takeScreenshot("annoying-07-clickgui-crosshair-settings");

        // Search by typing.
        ctx.getInput().typeChars("zoom");
        ctx.waitTicks(8);
        ctx.takeScreenshot("annoying-08-clickgui-search");
        ctx.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE); // clears search, GUI stays open
        ctx.waitTicks(2);
        check(ctx.computeOnClient(mc -> mc.screen instanceof ClickGuiScreen), "Escape with a search should only clear the search");

        // Themes tab: pick Crimson (third preset card) and open the accent color picker.
        clickTab(ctx, 7);
        ctx.takeScreenshot("annoying-09-clickgui-themes");
        click(ctx, 132 + 2 * 110 + 50, 69 + 20, GLFW.GLFW_MOUSE_BUTTON_LEFT);
        ctx.waitTicks(6);
        String preset = ctx.computeOnClient(mc -> AnnoyingClient.theme().getPresetName());
        check(preset.equals("Crimson"), "Clicking the Crimson card should apply it, got " + preset);
        float accentRowY = 69 + 94 + 5 + 16 + 5;
        click(ctx, 132 + 324 - 8, accentRowY + 9, GLFW.GLFW_MOUSE_BUTTON_LEFT);
        ctx.waitTicks(10);
        ctx.takeScreenshot("annoying-10-clickgui-color-picker");

        // Client tab: create a profile from the text field.
        clickTab(ctx, 8);
        ctx.takeScreenshot("annoying-11-clickgui-client-tab");
        click(ctx, 132 + 60, 69 + 8, GLFW.GLFW_MOUSE_BUTTON_LEFT);
        ctx.getInput().typeChars("pvp");
        ctx.getInput().pressKey(GLFW.GLFW_KEY_ENTER);
        ctx.waitTicks(6);
        String active = ctx.computeOnClient(mc -> AnnoyingClient.config().getActiveProfile());
        check(active.equals("pvp"), "Creating a profile should switch to it, active is " + active);
        ctx.takeScreenshot("annoying-12-clickgui-profile-created");

        testModConfigs(ctx);

        ctx.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
        ctx.waitFor(mc -> mc.screen == null);
        ctx.waitTicks(10);
        ctx.takeScreenshot("annoying-13-hud-crimson-theme");
    }

    // ---- Mod Configs tab ---------------------------------------------------------------------

    private void testModConfigs(ClientGameTestContext ctx) {
        clickTab(ctx, 5);
        ctx.waitTicks(10);
        ctx.takeScreenshot("annoying-12b-mod-configs");
        int listed = ctx.computeOnClient(mc -> com.annoyingclient.modconfig.ModConfigRegistry.mods().size());
        check(listed > 0, "The Mod Configs tab should list the installed mods");
        check(ctx.computeOnClient(mc -> ((ClickGuiScreen) mc.screen).modConfigToolbar() != null), "The Mod Configs toolbar should be shown");

        // Libraries (Fabric API) are hidden until the Libraries switch is turned on.
        check(!visibleMods(ctx).contains("fabric-api"), "Fabric API is a library and should be hidden by default: " + visibleMods(ctx));
        float[] tb = ctx.computeOnClient(mc -> {
            var t = ((ClickGuiScreen) mc.screen).modConfigToolbar();
            return new float[]{t.getX(), t.getY(), t.getWidth()};
        });
        click(ctx, tb[0] + tb[2] - 8, tb[1] + 11, GLFW.GLFW_MOUSE_BUTTON_LEFT);
        ctx.waitTicks(5);
        check(visibleMods(ctx).contains("fabric-api"), "Fabric API should show with the Libraries switch on");
        click(ctx, tb[0] + tb[2] - 8, tb[1] + 11, GLFW.GLFW_MOUSE_BUTTON_LEFT);
        ctx.waitTicks(5);
        check(!visibleMods(ctx).contains("fabric-api"), "Fabric API should hide again");

        // Live reload through real Cloth Config and YACL configs (no restart). Development runs only.
        var loader = net.fabricmc.loader.api.FabricLoader.getInstance();
        if (loader.isModLoaded("cloth-config") && loader.isModLoaded("yet_another_config_lib_v3")) {
            testLiveReload(ctx);
        }

        if (!net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("modmenu")) {
            return; // the production test run has no Mod Menu; development runs check its card
        }
        // Favorite Mod Menu with its star: it moves to the top and stays in the Favorites filter.
        float[] card = cardBounds(ctx, "modmenu");
        check(card != null, "Mod Menu should be listed in the Mod Configs tab");
        boolean hasScreen = ctx.computeOnClient(mc -> ((ClickGuiScreen) mc.screen).visibleModConfigCard("modmenu").getMod().hasScreen());
        check(hasScreen, "Mod Menu's settings screen should be found through its entrypoint");
        float star = card[0] + card[2] - 32 - 58 + 7;
        click(ctx, star, card[1] + 17, GLFW.GLFW_MOUSE_BUTTON_LEFT);
        ctx.waitTicks(5);
        check(ctx.computeOnClient(mc -> com.annoyingclient.modconfig.ModConfigPrefs.get().isFavorite("modmenu")), "The star should make Mod Menu a favorite");
        check(visibleMods(ctx).getFirst().equals("modmenu"), "Favorites should be listed first: " + visibleMods(ctx));
        ctx.runOnClient(mc -> {
            com.annoyingclient.modconfig.ModConfigPrefs.get().setFilter(com.annoyingclient.modconfig.ModConfigPrefs.Filter.FAVORITES);
            ((ClickGuiScreen) mc.screen).scrollContent(0);
        });
        clickTab(ctx, 5);
        ctx.waitTicks(5);
        check(visibleMods(ctx).equals(java.util.List.of("modmenu")), "The Favorites filter should show only favorites: " + visibleMods(ctx));
        ctx.takeScreenshot("annoying-12e-mod-configs-favorites");
        ctx.runOnClient(mc -> com.annoyingclient.modconfig.ModConfigPrefs.get().setFilter(com.annoyingclient.modconfig.ModConfigPrefs.Filter.ALL));
        clickTab(ctx, 5);
        ctx.waitTicks(5);

        // The Settings button opens Mod Menu's own config screen, with the ClickGUI as its parent.
        card = cardBounds(ctx, "modmenu");
        click(ctx, card[0] + card[2] - 24 - 26, card[1] + 17, GLFW.GLFW_MOUSE_BUTTON_LEFT);
        ctx.waitTicks(10);
        String opened = ctx.computeOnClient(mc -> mc.screen == null ? "none" : mc.screen.getClass().getName());
        check(!opened.equals(ClickGuiScreen.class.getName()) && !opened.equals("none"), "Settings should open the mod's screen, got " + opened);
        ctx.takeScreenshot("annoying-12c-mod-settings-screen");
        ctx.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
        ctx.waitFor(mc -> mc.screen instanceof ClickGuiScreen s && s.isFullyOpen(), 200);
        ctx.waitTicks(5);
        // Clicking the row opens details (description, links) and the config file editor.
        card = cardBounds(ctx, "modmenu");
        click(ctx, card[0] + 60, card[1] + 17, GLFW.GLFW_MOUSE_BUTTON_LEFT);
        ctx.waitTicks(12);
        ctx.takeScreenshot("annoying-12d-mod-config-details");
        ctx.runOnClient(mc -> com.annoyingclient.modconfig.ModConfigPrefs.get().toggleFavorite("modmenu"));
    }

    private void testLiveReload(ClientGameTestContext ctx) {
        ctx.runOnClient(mc -> LiveReloadConfigs.register());
        var any = ctx.computeOnClient(mc -> com.annoyingclient.modconfig.ModConfigRegistry.mods().getFirst());
        for (java.nio.file.Path file : java.util.List.of(LiveReloadConfigs.CLOTH_FILE, LiveReloadConfigs.YACL_FILE)) {
            String name = file.getFileName().toString();
            var predicted = ctx.computeOnClient(mc -> com.annoyingclient.modconfig.LiveReload.predict(any, file));
            check(predicted == com.annoyingclient.modconfig.LiveReload.Mode.LIVE, name + " should be recognised as live-reloadable, got " + predicted);
            // Edit the file the way the Mod Configs editor does, then apply it.
            var result = ctx.computeOnClient(mc -> {
                var cf = com.annoyingclient.modconfig.ConfigFile.load(file);
                for (var e : cf.entries()) {
                    if (e.key.equals("fancyMode")) {
                        e.value = "true";
                    } else if (e.key.equals("amount")) {
                        e.value = "42";
                    }
                }
                try {
                    cf.save();
                } catch (java.io.IOException ex) {
                    throw new RuntimeException(ex);
                }
                return com.annoyingclient.modconfig.LiveReload.apply(any, file);
            });
            check(result == com.annoyingclient.modconfig.LiveReload.Mode.LIVE, name + " should apply live, got " + result);
        }
        check(ctx.computeOnClient(mc -> LiveReloadConfigs.cloth.getConfig().fancyMode && LiveReloadConfigs.cloth.getConfig().amount == 42),
                "Cloth Config mod should see the saved values without a restart");
        check(ctx.computeOnClient(mc -> LiveReloadConfigs.yacl.instance().fancyMode && LiveReloadConfigs.yacl.instance().amount == 42),
                "YACL mod should see the saved values without a restart");
        // Undo puts the original file back, and the mod follows.
        ctx.runOnClient(mc -> {
            try {
                com.annoyingclient.modconfig.ConfigFile.restoreBackup(LiveReloadConfigs.CLOTH_FILE);
            } catch (java.io.IOException ex) {
                throw new RuntimeException(ex);
            }
            com.annoyingclient.modconfig.LiveReload.apply(any, LiveReloadConfigs.CLOTH_FILE);
        });
        check(ctx.computeOnClient(mc -> !LiveReloadConfigs.cloth.getConfig().fancyMode && LiveReloadConfigs.cloth.getConfig().amount == 1),
                "Undo should restore the original values in the running mod");
        // Mixin switches can't be applied live, and say so.
        var mixinMode = ctx.computeOnClient(mc -> com.annoyingclient.modconfig.LiveReload.predict(any,
                net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("lithium.properties")));
        check(mixinMode == com.annoyingclient.modconfig.LiveReload.Mode.RESTART, "Mixin config files should need a restart, got " + mixinMode);
    }

    private static void setField(Object target, String name, Object value) {
        try {
            var f = target.getClass().getDeclaredField(name);
            f.setAccessible(true);
            f.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private static Object getField(Object target, String name) {
        try {
            var f = target.getClass().getDeclaredField(name);
            f.setAccessible(true);
            return f.get(target);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private java.util.List<String> visibleMods(ClientGameTestContext ctx) {
        return ctx.computeOnClient(mc -> ((ClickGuiScreen) mc.screen).visibleModConfigIds());
    }

    /** {x, y, width} of a mod's card in ClickGUI coordinates, scrolled into view; null if not listed. */
    private float[] cardBounds(ClientGameTestContext ctx, String modId) {
        for (int attempt = 0; attempt < 30; attempt++) {
            float[] b = ctx.computeOnClient(mc -> {
                var card = ((ClickGuiScreen) mc.screen).visibleModConfigCard(modId);
                return card == null ? null : new float[]{card.getX(), card.getY(), card.getWidth()};
            });
            if (b == null) {
                return null;
            }
            if (b[1] >= 40 && b[1] + 34 <= 300) {
                return b;
            }
            float delta = b[1] < 40 ? -60 : 60;
            ctx.runOnClient(mc -> ((ClickGuiScreen) mc.screen).scrollContent(delta));
            ctx.waitTicks(4);
        }
        return null;
    }

    // ---- saturation and potion effects --------------------------------------------------------

    private void testSaturationAndEffects(ClientGameTestContext ctx, TestSingleplayerContext world) {
        world.getServer().runCommand("effect give @a minecraft:speed 90 1");
        world.getServer().runCommand("effect give @a minecraft:slowness 9 0");
        world.getServer().runCommand("effect give @a minecraft:haste 300 2");
        world.getServer().runCommand("item replace entity @a weapon.mainhand with minecraft:bread 16");
        world.getServer().runOnServer(server -> {
            var p = server.getPlayerList().getPlayers().getFirst();
            p.getFoodData().setFoodLevel(12);
            p.getFoodData().setSaturation(3f);
        });
        var saturation = AnnoyingClient.modules().get(com.annoyingclient.module.impl.visual.SaturationModule.class);
        var effects = AnnoyingClient.modules().get(com.annoyingclient.hud.element.PotionEffectsHud.class);
        ctx.runOnClient(mc -> {
            saturation.setEnabled(true, false);
            effects.setEnabled(true, false);
            for (var setting : saturation.getSettings()) {
                if (setting.getName().equals("Show Numbers") && setting instanceof com.annoyingclient.setting.BooleanSetting b) {
                    b.setValue(true);
                }
            }
        });
        // The client learns the new food values from the server.
        ctx.waitFor(mc -> mc.player.getFoodData().getFoodLevel() == 12 && Math.abs(mc.player.getFoodData().getSaturationLevel() - 3f) < 0.01f, 100);
        ctx.waitFor(mc -> mc.player.getActiveEffects().size() == 3, 100);
        check(ctx.computeOnClient(mc -> effects.hidesVanillaIcons()), "The Potion Effects HUD should hide the vanilla icons by default");
        ctx.waitTicks(10);
        ctx.takeScreenshot("annoying-26-saturation-and-potion-effects");
        // Clean up for the following tests.
        world.getServer().runCommand("effect clear @a");
        world.getServer().runCommand("item replace entity @a weapon.mainhand with minecraft:air");
        world.getServer().runOnServer(server -> server.getPlayerList().getPlayers().getFirst().getFoodData().setFoodLevel(20));
        ctx.runOnClient(mc -> saturation.setEnabled(false, false));
    }

    // ---- cosmetics, wardrobe and admin tools ---------------------------------------------------

    /** Runs only when a cosmetics server is given in ANNOYING_COSMETICS_URL (the build script starts a local one). */
    private void testCosmetics(ClientGameTestContext ctx) {
        String url = System.getenv("ANNOYING_COSMETICS_URL");
        if (url == null || url.isBlank()) {
            return;
        }
        var cos = ctx.computeOnClient(mc -> AnnoyingClient.cosmetics());
        // Preview mode first: no server set.
        ctx.getInput().pressKey(GLFW.GLFW_KEY_RIGHT_SHIFT);
        ctx.waitFor(mc -> mc.screen instanceof ClickGuiScreen s && s.isFullyOpen());
        clickTab(ctx, 6);
        ctx.waitTicks(10);
        ctx.takeScreenshot("annoying-21-wardrobe-preview-mode");

        ctx.runOnClient(mc -> AnnoyingClient.modules().get(com.annoyingclient.module.impl.visual.CosmeticsModule.class).setServerUrl(url));
        ctx.waitFor(mc -> cos.status() == com.annoyingclient.cosmetics.CosmeticsManager.Status.SIGNED_IN
                || cos.status() == com.annoyingclient.cosmetics.CosmeticsManager.Status.ERROR, 400);
        if (cos.status() != com.annoyingclient.cosmetics.CosmeticsManager.Status.SIGNED_IN) {
            // An attempt made while the game was still starting can fail; the Retry button signs in again.
            AnnoyingClient.LOGGER.warn("First cosmetics sign-in failed ({}); retrying", cos.error());
            ctx.runOnClient(mc -> cos.signIn(null));
            ctx.waitFor(mc -> cos.status() == com.annoyingclient.cosmetics.CosmeticsManager.Status.SIGNED_IN
                    || cos.status() == com.annoyingclient.cosmetics.CosmeticsManager.Status.ERROR, 400);
        }
        check(cos.status() == com.annoyingclient.cosmetics.CosmeticsManager.Status.SIGNED_IN, "Should sign in to the cosmetics server: " + cos.error());
        // The development run's player (Player0) is in ADMINS; production runs use a random name and must NOT be admin.
        String playerName = ctx.computeOnClient(mc -> mc.getUser().getName());
        boolean expectAdmin = playerName.equals("Player0");
        check(cos.profile().admin() == expectAdmin, playerName + " admin=" + cos.profile().admin() + ", expected " + expectAdmin);
        check(cos.profile().owner() == expectAdmin, playerName + " owner=" + cos.profile().owner() + ", expected " + expectAdmin);
        // A server restart forgets every login (sessions are not backed up): the client signs back in by itself.
        ctx.runOnClient(mc -> setField(cos, "token", "expired-after-server-restart"));
        ctx.runOnClient(mc -> cos.equip("trail", null, e -> {}));
        ctx.waitFor(mc -> cos.status() == com.annoyingclient.cosmetics.CosmeticsManager.Status.SIGNED_IN
                && !"expired-after-server-restart".equals(getField(cos, "token")), 400);
        check(cos.profile() != null, "Profile should be back after signing in again");
        int start = cos.profile().coins();

        // Daily reward through the button.
        ctx.waitTicks(10);
        click(ctx, 132 + 324 - 76 + 34, 48 + 20, GLFW.GLFW_MOUSE_BUTTON_LEFT);
        ctx.waitFor(mc -> cos.profile().coins() == start + 50, 200);

        // Buy Monochrome (100 coins) by clicking its card twice, then equip it with a third click.
        int index = -1;
        for (int i = 0; i < cos.catalog().size(); i++) {
            if (cos.catalog().get(i).id().equals("cape_mono")) {
                index = i;
            }
        }
        float gridX = 132 + 112 + 10;
        float cardW = (324 - 112 - 10 - 6) / 2f;
        float cx = gridX + (index % 2) * (cardW + 6) + cardW / 2f;
        float cy = 48 + 40 + 10 + (index / 2) * (50 + 6) + 25;
        click(ctx, cx, cy, GLFW.GLFW_MOUSE_BUTTON_LEFT);
        click(ctx, cx, cy, GLFW.GLFW_MOUSE_BUTTON_LEFT);
        ctx.waitFor(mc -> cos.profile().owned().contains("cape_mono"), 200);
        check(cos.profile().coins() == start + 50 - 100, "Buying should cost 100 coins, have " + cos.profile().coins());
        ctx.waitTicks(4);
        click(ctx, cx, cy, GLFW.GLFW_MOUSE_BUTTON_LEFT);
        ctx.waitFor(mc -> "cape_mono".equals(cos.profile().equipped().get("cape")), 200);

        String[] err = {"pending"};
        if (!expectAdmin) {
            // A regular player's admin requests must be refused by the server.
            ctx.runOnClient(mc -> cos.admin("coins", java.util.Map.of("player", playerName, "amount", 1000), e -> err[0] = e == null ? "" : e));
            ctx.waitFor(mc -> !err[0].equals("pending"), 200);
            check(err[0].toLowerCase(java.util.Locale.ROOT).contains("admins only"), "Non-admin coin grant should be refused, got: " + err[0]);
            check(cos.profile().coins() == start + 50 - 100, "Coins must not change after a refused admin request");
            ctx.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
            ctx.waitFor(mc -> mc.screen == null);
            return;
        }
        // Admin tools: give yourself coins and the galaxy cape through the server.
        ctx.runOnClient(mc -> cos.admin("coins", java.util.Map.of("player", "Player0", "amount", 1000), e -> err[0] = e == null ? "" : e));
        ctx.waitFor(mc -> !err[0].equals("pending"), 200);
        check(err[0].isEmpty(), "Admin coins failed: " + err[0]);
        err[0] = "pending";
        ctx.runOnClient(mc -> cos.admin("cosmetic", java.util.Map.of("player", "Player0", "id", "cape_galaxy", "give", true), e -> err[0] = e == null ? "" : e));
        ctx.waitFor(mc -> !err[0].equals("pending"), 200);
        ctx.waitFor(mc -> cos.profile().owned().contains("cape_galaxy"), 200);
        err[0] = "pending";
        ctx.runOnClient(mc -> cos.equip("trail", null, e -> {}));
        ctx.runOnClient(mc -> cos.admin("cosmetic", java.util.Map.of("player", "Player0", "id", "trail_sparkle", "give", true), e -> err[0] = e == null ? "" : e));
        ctx.waitFor(mc -> cos.profile().owned().contains("trail_sparkle"), 200);
        String[] eq = {"pending"};
        ctx.runOnClient(mc -> cos.equip("trail", "trail_sparkle", e -> eq[0] = e == null ? "" : e));
        ctx.waitFor(mc -> !eq[0].equals("pending"), 200);
        ctx.waitTicks(10);
        ctx.takeScreenshot("annoying-22-wardrobe-signed-in");
        ctx.runOnClient(mc -> ((ClickGuiScreen) mc.screen).scrollContent(400));
        ctx.waitTicks(15);
        ctx.takeScreenshot("annoying-23-wardrobe-admin-tools");
        ctx.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
        ctx.waitFor(mc -> mc.screen == null);

        // Third person: the equipped cape is drawn by vanilla's cape renderer; walk to show the trail.
        ctx.runOnClient(mc -> mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT));
        ctx.runOnClient(mc -> mc.player.setYRot(mc.player.getYRot() + 180));
        ctx.getInput().holdKey(GLFW.GLFW_KEY_S);
        ctx.waitTicks(25);
        ctx.getInput().releaseKey(GLFW.GLFW_KEY_S);
        ctx.runOnClient(mc -> mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK));
        ctx.waitTicks(8);
        ctx.takeScreenshot("annoying-24-cape-third-person");

        // Cape styles, seen from the side in the Wardrobe preview: vanilla and thin (the default).
        var capes = AnnoyingClient.modules().get(com.annoyingclient.module.impl.visual.CosmeticsModule.class);
        check(capes.thinCapes(), "Thin capes should be on by default");
        ctx.getInput().pressKey(GLFW.GLFW_KEY_RIGHT_SHIFT);
        ctx.waitFor(mc -> mc.screen instanceof ClickGuiScreen s && s.isFullyOpen());
        clickTab(ctx, 6);
        ctx.waitFor(mc -> ((ClickGuiScreen) mc.screen).wardrobe() != null);
        ctx.runOnClient(mc -> ((ClickGuiScreen) mc.screen).wardrobe().setPreviewYaw(90));
        String[][] styles = {{"classic", "false"}, {"thin", "true"}};
        for (String[] style : styles) {
            ctx.runOnClient(mc -> {
                capes.thinCapesSetting().setValue(Boolean.parseBoolean(style[1]));
            });
            long before = com.annoyingclient.cosmetics.CapeRenderer.drawn;
            ctx.waitTicks(3);
            ctx.takeScreenshot("annoying-25-cape-" + style[0]);
            long after = com.annoyingclient.cosmetics.CapeRenderer.drawn;
            check(style[0].equals("classic") ? after == before : after > before,
                    style[0] + " cape: custom renderer drew " + (after - before) + " times");
        }
        ctx.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
        ctx.waitFor(mc -> mc.screen == null);
        ctx.runOnClient(mc -> mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON));
    }

    // ---- keybinds, zoom, fullbright, crosshair ------------------------------------------------

    private void testKeybindAndZoom(ClientGameTestContext ctx, TestSingleplayerContext world) {
        ctx.runOnClient(mc -> {
            FullbrightModule fb = AnnoyingClient.modules().get(FullbrightModule.class);
            fb.setEnabled(false, false);
            fb.getKeybind().setValue(GLFW.GLFW_KEY_G);
        });
        ctx.getInput().pressKey(GLFW.GLFW_KEY_G);
        ctx.waitTicks(2);
        check(ctx.computeOnClient(mc -> AnnoyingClient.modules().get(FullbrightModule.class).isEnabled()),
                "Pressing the bound key G should enable Fullbright");

        world.getServer().runCommand("time set midnight");
        ctx.runOnClient(mc -> AnnoyingClient.modules().get(CrosshairModule.class).setEnabled(true, false));
        ctx.waitTicks(30);
        ctx.takeScreenshot("annoying-14-night-fullbright-custom-crosshair");
        ctx.getInput().pressKey(GLFW.GLFW_KEY_G);
        ctx.waitTicks(30);
        check(!ctx.computeOnClient(mc -> AnnoyingClient.modules().get(FullbrightModule.class).isEnabled()),
                "Pressing G again should disable Fullbright");
        ctx.takeScreenshot("annoying-15-night-without-fullbright");
        world.getServer().runCommand("time set noon");

        ctx.getInput().holdKey(GLFW.GLFW_KEY_C);
        ctx.waitTicks(20);
        float divisor = ctx.computeOnClient(mc -> AnnoyingClient.modules().get(ZoomModule.class).getFovDivisor());
        check(divisor > 3.5f, "Holding C should zoom in, FOV divisor was " + divisor);
        ctx.takeScreenshot("annoying-16-zoom");
        ctx.getInput().releaseKey(GLFW.GLFW_KEY_C);
        ctx.waitTicks(20);
        float released = ctx.computeOnClient(mc -> AnnoyingClient.modules().get(ZoomModule.class).getFovDivisor());
        check(released < 1.05f, "Releasing C should zoom out, divisor was " + released);
        ctx.runOnClient(mc -> AnnoyingClient.modules().get(FullbrightModule.class).getKeybind().setValue(KeybindSetting.NONE));

        // Screenshot Manager: a real F2 screenshot should be counted and announced.
        ctx.getInput().pressKey(GLFW.GLFW_KEY_F2);
        ctx.waitFor(mc -> !AnnoyingClient.modules().get(ScreenshotModule.class).getDisplayInfo().isEmpty(), 200);
        ctx.waitTicks(5);
        ctx.takeScreenshot("annoying-16b-screenshot-notification");
    }

    // ---- HUD editor --------------------------------------------------------------------------

    private void testHudEditor(ClientGameTestContext ctx) {
        ctx.setScreen(() -> new HudEditorScreen(null));
        ctx.waitTicks(10);
        ctx.takeScreenshot("annoying-17-hud-editor");
        double scale = ctx.computeOnClient(mc -> (double) mc.getWindow().getGuiScale());
        float[] wm = ctx.computeOnClient(mc -> {
            WatermarkHud w = AnnoyingClient.modules().get(WatermarkHud.class);
            int sw = mc.getWindow().getGuiScaledWidth();
            int sh = mc.getWindow().getGuiScaledHeight();
            return new float[]{w.getScreenX(sw) + 8, w.getScreenY(sh) + 6, sw, sh};
        });
        ctx.getInput().setCursorPos(wm[0] * scale, wm[1] * scale);
        ctx.waitTick();
        ctx.getInput().holdMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
        ctx.waitTick();
        for (int i = 1; i <= 10; i++) {
            ctx.getInput().setCursorPos((wm[0] + (wm[2] * 0.5 - wm[0]) * i / 10.0) * scale, (wm[1] + (wm[3] * 0.4 - wm[1]) * i / 10.0) * scale);
            ctx.waitTick();
        }
        ctx.getInput().releaseMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
        ctx.waitTicks(3);
        float newX = ctx.computeOnClient(mc -> AnnoyingClient.modules().get(WatermarkHud.class).getScreenX(mc.getWindow().getGuiScaledWidth()));
        check(newX > wm[2] * 0.3f, "Dragging the watermark should move it toward the center, x is " + newX);
        ctx.takeScreenshot("annoying-18-hud-editor-dragged");
        ctx.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
        ctx.waitFor(mc -> mc.screen == null);
    }

    // ---- configuration -----------------------------------------------------------------------

    private void testConfig(ClientGameTestContext ctx) {
        Path dir = ctx.computeOnClient(mc -> AnnoyingClient.getInstance().getConfigDirectory());
        ctx.runOnClient(mc -> AnnoyingClient.config().saveManually());
        ctx.waitTicks(2);
        try {
            JsonObject pvp = JsonParser.parseString(Files.readString(dir.resolve("profiles/pvp.json"), StandardCharsets.UTF_8)).getAsJsonObject();
            String savedTheme = pvp.getAsJsonObject("theme").get("Theme").getAsString();
            check(savedTheme.equals("Crimson"), "pvp.json should store the Crimson theme, has " + savedTheme);
            JsonObject watermark = pvp.getAsJsonObject("modules").getAsJsonObject("Watermark").getAsJsonObject("settings");
            check(watermark.get("Anchor X").getAsInt() == 1, "Watermark should be saved with a center anchor");
            check(pvp.getAsJsonObject("modules").getAsJsonObject("Custom Crosshair").get("enabled").getAsBoolean(),
                    "Custom Crosshair enabled state should be saved");

            // A corrupted profile must be backed up and replaced with defaults, not crash the client.
            Files.writeString(dir.resolve("profiles/broken.json"), "{ \"modules\": { this is not json", StandardCharsets.UTF_8);
            boolean switched = ctx.computeOnClient(mc -> AnnoyingClient.config().switchProfile("broken"));
            check(switched, "Switching to a corrupted profile should fall back to defaults");
            check(Files.exists(dir.resolve("profiles/broken.json")), "A fresh broken.json with defaults should be written");
            boolean backup = false;
            try (DirectoryStream<Path> files = Files.newDirectoryStream(dir.resolve("profiles"), "broken.corrupt-*.json")) {
                backup = files.iterator().hasNext();
            }
            check(backup, "The corrupted file should be kept as broken.corrupt-<time>.json");
            String themeAfter = ctx.computeOnClient(mc -> AnnoyingClient.theme().getPresetName());
            check(themeAfter.equals("Midnight Purple"), "Defaults should be applied for a corrupted profile, theme is " + themeAfter);
            ctx.waitTicks(10);
            ctx.takeScreenshot("annoying-19-corrupted-profile-recovered");

            // Switching back restores everything that was saved in pvp.json.
            ctx.runOnClient(mc -> AnnoyingClient.config().switchProfile("pvp"));
            check(ctx.computeOnClient(mc -> AnnoyingClient.theme().getPresetName()).equals("Crimson"), "pvp profile should restore Crimson");
            check(ctx.computeOnClient(mc -> AnnoyingClient.modules().get(CrosshairModule.class).isEnabled()), "pvp profile should restore the crosshair");
            ctx.runOnClient(mc -> AnnoyingClient.config().deleteProfile("broken"));
        } catch (IOException e) {
            throw new AssertionError("Config files could not be read", e);
        }
        ctx.waitTicks(5);
        ctx.takeScreenshot("annoying-20-profile-restored");
    }
}
