package com.annoyingclient.modconfig;

import com.annoyingclient.AnnoyingClient;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashSet;
import java.util.Set;

/** Mod Configs tab choices that stick between sessions: favorite mods and the list filter. */
public final class ModConfigPrefs {

    public enum Filter { ALL, SETTINGS, FAVORITES }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static ModConfigPrefs instance;

    // Saved fields
    private Set<String> favorites = new LinkedHashSet<>();
    private Filter filter = Filter.ALL;
    private boolean showLibraries;

    private ModConfigPrefs() {
    }

    public static synchronized ModConfigPrefs get() {
        if (instance == null) {
            instance = load();
        }
        return instance;
    }

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve(AnnoyingClient.MOD_ID).resolve("mod-configs.json");
    }

    private static ModConfigPrefs load() {
        Path f = file();
        if (Files.exists(f)) {
            try {
                ModConfigPrefs p = GSON.fromJson(Files.readString(f, StandardCharsets.UTF_8), ModConfigPrefs.class);
                if (p != null) {
                    if (p.favorites == null) {
                        p.favorites = new LinkedHashSet<>();
                    }
                    if (p.filter == null) {
                        p.filter = Filter.ALL;
                    }
                    return p;
                }
            } catch (IOException | RuntimeException e) {
                AnnoyingClient.LOGGER.warn("Mod Configs preferences unreadable, using defaults: {}", e.toString());
            }
        }
        return new ModConfigPrefs();
    }

    private void save() {
        try {
            Path f = file();
            Files.createDirectories(f.getParent());
            Path tmp = f.resolveSibling(f.getFileName() + ".tmp");
            Files.writeString(tmp, GSON.toJson(this), StandardCharsets.UTF_8);
            Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            AnnoyingClient.LOGGER.warn("Couldn't save Mod Configs preferences", e);
        }
    }

    public boolean isFavorite(String modId) {
        return favorites.contains(modId);
    }

    public void toggleFavorite(String modId) {
        if (!favorites.remove(modId)) {
            favorites.add(modId);
        }
        save();
    }

    public int favoriteCount() {
        return favorites.size();
    }

    public Filter filter() {
        return filter;
    }

    public void setFilter(Filter f) {
        filter = f;
        save();
    }

    public boolean showLibraries() {
        return showLibraries;
    }

    public void setShowLibraries(boolean show) {
        showLibraries = show;
        save();
    }
}
