package com.annoyingclient.modconfig;

import com.annoyingclient.AnnoyingClient;
import com.mojang.blaze3d.platform.NativeImage;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** Loads each mod's icon from its jar once and keeps it as a texture. Must be used on the render thread. */
public final class ModIcons {

    private static final Map<String, Optional<Identifier>> CACHE = new HashMap<>();

    private ModIcons() {
    }

    public static Identifier get(ModContainer mod) {
        String id = mod.getMetadata().getId();
        return CACHE.computeIfAbsent(id, k -> load(mod)).orElse(null);
    }

    private static Optional<Identifier> load(ModContainer mod) {
        try {
            Optional<String> iconPath = mod.getMetadata().getIconPath(64);
            if (iconPath.isEmpty()) {
                return Optional.empty();
            }
            Optional<Path> file = mod.findPath(iconPath.get());
            if (file.isEmpty()) {
                return Optional.empty();
            }
            NativeImage image;
            try (InputStream in = Files.newInputStream(file.get())) {
                image = NativeImage.read(in);
            }
            String safe = mod.getMetadata().getId().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.-]", "_");
            Identifier id = Identifier.fromNamespaceAndPath(AnnoyingClient.MOD_ID, "modicon/" + safe);
            Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(() -> "mod icon " + safe, image));
            return Optional.of(id);
        } catch (Exception | LinkageError e) {
            return Optional.empty();
        }
    }
}
