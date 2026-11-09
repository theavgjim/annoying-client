package com.annoyingclient;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Entry point of ANNOYING CLIENT.
 *
 * <p>Fabric calls {@link #onInitializeClient()} once, on the render thread, before the game window
 * finishes loading. Systems are brought up in a fixed order so that later systems can rely on
 * earlier ones (configuration needs modules and their settings to exist before it can apply values).
 */
public final class AnnoyingClient implements ClientModInitializer {

    public static final String MOD_ID = "annoyingclient";
    public static final String NAME = "Annoying Client";
    public static final String BRANDING = "ANNOYING CLIENT";
    public static final Logger LOGGER = LoggerFactory.getLogger(NAME);

    private static AnnoyingClient instance;

    private String version = "dev";
    private Path configDirectory;

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

        ClientLifecycleEvents.CLIENT_STARTED.register(client ->
                LOGGER.info("{} is ready (Minecraft window up)", BRANDING));
        ClientLifecycleEvents.CLIENT_STOPPING.register(client ->
                LOGGER.info("{} shutting down", BRANDING));

        LOGGER.info("{} {} initialized in {} ms (config: {})", BRANDING, version,
                (System.nanoTime() - start) / 1_000_000, configDirectory);
    }

    public static AnnoyingClient getInstance() {
        return instance;
    }

    public String getVersion() {
        return version;
    }

    public Path getConfigDirectory() {
        return configDirectory;
    }
}
