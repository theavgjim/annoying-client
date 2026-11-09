package com.annoyingclient;

import com.annoyingclient.config.ConfigManager;
import com.annoyingclient.event.ClientTickEvent;
import com.annoyingclient.event.EventBus;
import com.annoyingclient.gui.clickgui.ClickGuiScreen;
import com.annoyingclient.hud.HudManager;
import com.annoyingclient.hud.element.ArmorHud;
import com.annoyingclient.hud.element.ClockHud;
import com.annoyingclient.hud.element.CoordinatesHud;
import com.annoyingclient.hud.element.CpsHud;
import com.annoyingclient.hud.element.FpsHud;
import com.annoyingclient.hud.element.KeystrokesHud;
import com.annoyingclient.hud.element.ModuleListHud;
import com.annoyingclient.hud.element.PingHud;
import com.annoyingclient.hud.element.WatermarkHud;
import com.annoyingclient.manager.KeybindManager;
import com.annoyingclient.manager.ModuleManager;
import com.annoyingclient.module.impl.misc.BackgroundFpsModule;
import com.annoyingclient.module.impl.render.LowFireModule;
import com.annoyingclient.module.impl.render.NoHurtCamModule;
import com.annoyingclient.module.impl.utility.ClickGuiModule;
import com.annoyingclient.module.impl.utility.HudEditorModule;
import com.annoyingclient.module.impl.utility.NotificationsModule;
import com.annoyingclient.module.impl.utility.ProfileSwitcherModule;
import com.annoyingclient.module.impl.utility.ThemeSwitcherModule;
import com.annoyingclient.module.impl.visual.CrosshairModule;
import com.annoyingclient.module.impl.visual.FullbrightModule;
import com.annoyingclient.module.impl.visual.HudColorsModule;
import com.annoyingclient.module.impl.visual.ScreenshotModule;
import com.annoyingclient.module.impl.visual.ZoomModule;
import com.annoyingclient.module.Module;
import com.annoyingclient.notification.NotificationManager;
import com.annoyingclient.notification.NotificationType;
import com.annoyingclient.render.RenderUtil;
import com.annoyingclient.render.TextRenderer;
import com.annoyingclient.render.Textures;
import com.annoyingclient.setting.Setting;
import com.annoyingclient.theme.ThemeManager;
import com.annoyingclient.util.ColorUtil;
import com.annoyingclient.util.CpsTracker;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Entry point of ANNOYING CLIENT.
 *
 * <p>Fabric calls {@link #onInitializeClient()} once, on the render thread, before the game window
 * finishes loading. Systems are created in dependency order:
 * <ol>
 *   <li>event bus, theme, notifications, CPS tracker</li>
 *   <li>modules (which may subscribe to events)</li>
 *   <li>HUD manager and keybind manager (which read the module list)</li>
 *   <li>configuration (which needs every module and setting to exist before it applies saved values)</li>
 *   <li>render callbacks, tick callbacks and screen hooks</li>
 * </ol>
 */
public final class AnnoyingClient implements ClientModInitializer {

    public static final String MOD_ID = "annoyingclient";
    public static final String NAME = "Annoying Client";
    public static final String BRANDING = "ANNOYING CLIENT";
    public static final Logger LOGGER = LoggerFactory.getLogger(NAME);

    private static AnnoyingClient instance;

    private String version = "dev";
    private Path configDirectory;

    private EventBus events;
    private ThemeManager theme;
    private NotificationManager notifications;
    private CpsTracker cps;
    private ModuleManager modules;
    private HudManager hud;
    private KeybindManager keybinds;
    private ConfigManager config;
    private HudColorsModule hudColors;

    @Override
    public void onInitializeClient() {
        instance = this;
        long start = System.nanoTime();

        version = FabricLoader.getInstance().getModContainer(MOD_ID)
                .map(ModContainer::getMetadata)
                .map(meta -> meta.getVersion().getFriendlyString())
                .orElse("dev");

        configDirectory = FabricLoader.getInstance().getConfigDir().resolve(MOD_ID);
        try {
            Files.createDirectories(configDirectory);
        } catch (IOException e) {
            LOGGER.error("Could not create configuration directory {}", configDirectory, e);
        }

        // 1. Core services
        events = new EventBus();
        theme = new ThemeManager();
        notifications = new NotificationManager();
        cps = new CpsTracker();

        // 2. Modules
        modules = new ModuleManager();
        registerModules();
        hudColors = modules.get(HudColorsModule.class);

        // 3. Systems that read the module list
        hud = new HudManager(modules, notifications, theme);
        keybinds = new KeybindManager(modules, events);

        // 4. Configuration
        config = new ConfigManager(configDirectory, modules, theme, notifications);
        Setting.setGlobalChangeHook(config::markDirty);
        Module.setToggleListener(this::onModuleToggled);
        config.init();
        NotificationsModule notificationsModule = modules.get(NotificationsModule.class);
        if (notificationsModule != null) {
            notificationsModule.apply();
        }

        // 5. Callbacks
        hud.registerLayer();
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            events.post(ClientTickEvent.INSTANCE);
            config.tick();
        });
        ClientLifecycleEvents.CLIENT_STARTED.register(this::onClientStarted);
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            config.shutdown();
            LOGGER.info("{} shut down, configuration saved", BRANDING);
        });
        registerScreenHooks();

        LOGGER.info("{} {} initialized {} modules in {} ms (profile '{}', config: {})", BRANDING, version,
                modules.getModules().size(), (System.nanoTime() - start) / 1_000_000, config.getActiveProfile(), configDirectory);
    }

    /** Every module of the client. To add a feature, create the module class and register it here. */
    private void registerModules() {
        // Render
        modules.register(new NoHurtCamModule());
        modules.register(new LowFireModule());
        // HUD
        modules.register(new WatermarkHud());
        modules.register(new ModuleListHud());
        modules.register(new FpsHud());
        modules.register(new PingHud());
        modules.register(new CpsHud());
        modules.register(new CoordinatesHud());
        modules.register(new ClockHud());
        modules.register(new KeystrokesHud());
        modules.register(new ArmorHud());
        // Utility
        modules.register(new ClickGuiModule());
        modules.register(new HudEditorModule());
        modules.register(new NotificationsModule());
        modules.register(new ProfileSwitcherModule());
        modules.register(new ThemeSwitcherModule());
        // Visual
        modules.register(new FullbrightModule());
        modules.register(new CrosshairModule());
        modules.register(new ZoomModule());
        modules.register(new HudColorsModule());
        modules.register(new ScreenshotModule());
        // Miscellaneous
        modules.register(new BackgroundFpsModule());
    }

    private void onModuleToggled(Module module, boolean enabled, boolean notify) {
        config.markDirty();
        if (notify) {
            notifications.push(enabled ? NotificationType.MODULE_ENABLED : NotificationType.MODULE_DISABLED,
                    module.getName(), enabled ? "Enabled" : "Disabled");
        }
    }

    private void onClientStarted(Minecraft client) {
        Textures.init();
        ClickGuiModule gui = modules.get(ClickGuiModule.class);
        String key = gui != null && gui.getKeybind().isBound() ? gui.getKeybind().getDisplayName() : "the ClickGUI keybind";
        notifications.push(NotificationType.INFO, BRANDING + " loaded", "Press " + key + " to open the menu");
        LOGGER.info("{} is ready", BRANDING);
    }

    private void registerScreenHooks() {
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            // Notifications are drawn on top of every screen (in game they are part of the HUD layer).
            ScreenEvents.afterRender(screen).register((s, g, mouseX, mouseY, delta) -> notifications.render(g, theme));

            if (screen instanceof TitleScreen) {
                Screens.getButtons(screen).add(Button.builder(Component.literal(NAME),
                                b -> client.setScreen(new ClickGuiScreen(screen)))
                        .bounds(4, 4, 98, 20)
                        .build());
                ScreenEvents.afterRender(screen).register((s, g, mouseX, mouseY, delta) -> {
                    int plus = version.indexOf('+');
                    String text = BRANDING + "  v" + (plus > 0 ? version.substring(0, plus) : version);
                    int color = ColorUtil.wave(theme.accent(), theme.accent2(), 0, 1f);
                    TextRenderer.draw(g, text, s.width - 4 - TextRenderer.width(text), 4, color);
                    RenderUtil.rect(g, s.width - 4 - TextRenderer.width(text), 14, TextRenderer.width(text), 1, color);
                });
            }
        });
    }

    // ---- global access -----------------------------------------------------------------------

    public static AnnoyingClient getInstance() {
        return instance;
    }

    public static EventBus events() {
        return instance.events;
    }

    public static ThemeManager theme() {
        return instance.theme;
    }

    public static NotificationManager notifications() {
        return instance.notifications;
    }

    public static ModuleManager modules() {
        return instance.modules;
    }

    public static HudManager hud() {
        return instance.hud;
    }

    public static ConfigManager config() {
        return instance.config;
    }

    public static CpsTracker cps() {
        return instance.cps;
    }

    public static HudColorsModule hudColors() {
        return instance == null ? null : instance.hudColors;
    }

    public KeybindManager getKeybindManager() {
        return keybinds;
    }

    public String getVersion() {
        return version;
    }

    public Path getConfigDirectory() {
        return configDirectory;
    }
}
