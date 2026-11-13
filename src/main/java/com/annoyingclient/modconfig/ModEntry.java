package com.annoyingclient.modconfig;

import net.fabricmc.loader.api.ModContainer;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * One installed mod as shown in the Mod Configs tab.
 *
 * @param links         "Website", "Issues", "Source" to URL, in that order (only the ones the mod declares)
 * @param library       a library other mods need (Mod Menu's "library" badge); hidden unless asked for
 * @param videoSettings the mod's settings live in the Video Settings screen (Sodium and its add-ons)
 */
public record ModEntry(String id, String name, String version, String description, String authors,
                       ModContainer container, Object screenFactory, List<Path> configFiles,
                       String license, Map<String, String> links, boolean library, boolean videoSettings) {

    /** Has its own settings screen (or uses Video Settings). */
    public boolean hasScreen() {
        return screenFactory != null || videoSettings;
    }

    public boolean hasSettings() {
        return hasScreen() || !configFiles.isEmpty();
    }
}
