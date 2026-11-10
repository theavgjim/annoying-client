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
        clickTab(ctx, 6);
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
        clickTab(ctx, 7);
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
        int index = ctx.computeOnClient(mc -> {
            var mods = com.annoyingclient.modconfig.ModConfigRegistry.mods();
            for (int i = 0; i < mods.size(); i++) {
                if (mods.get(i).id().equals("modmenu")) {
                    return i;
                }
            }
            return -1;
        });
        check(index >= 0, "Mod Menu should be listed in the Mod Configs tab");
        boolean hasScreen = ctx.computeOnClient(mc -> com.annoyingclient.modconfig.ModConfigRegistry.mods().get(index).hasScreen());
        check(hasScreen, "Mod Menu's settings screen should be found through its entrypoint");
        boolean hasFiles = ctx.computeOnClient(mc -> !com.annoyingclient.modconfig.ModConfigRegistry.mods().get(index).configFiles().isEmpty());
        float cardY = 48 + index * (34 + 5);
        float right = 132 + 324 - 10 - (hasFiles ? 14 : 0);
        // The Settings button opens Mod Menu's own config screen, with the ClickGUI as its parent.
        click(ctx, right - 26, cardY + 17, GLFW.GLFW_MOUSE_BUTTON_LEFT);
        ctx.waitTicks(10);
        String opened = ctx.computeOnClient(mc -> mc.screen == null ? "none" : mc.screen.getClass().getName());
        check(!opened.equals(ClickGuiScreen.class.getName()) && !opened.equals("none"), "Settings should open the mod's screen, got " + opened);
        ctx.takeScreenshot("annoying-12c-mod-settings-screen");
        ctx.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
        ctx.waitFor(mc -> mc.screen instanceof ClickGuiScreen s && s.isFullyOpen(), 200);
        ctx.waitTicks(5);
        if (hasFiles) {
            click(ctx, 200, cardY + 17, GLFW.GLFW_MOUSE_BUTTON_LEFT); // expand the config file editor
            ctx.waitTicks(12);
            ctx.takeScreenshot("annoying-12d-mod-config-file-editor");
        }
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
