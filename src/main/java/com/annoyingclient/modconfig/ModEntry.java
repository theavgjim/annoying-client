package com.annoyingclient.modconfig;

import net.fabricmc.loader.api.ModContainer;

import java.nio.file.Path;
import java.util.List;

/** One installed mod as shown in the Mod Configs tab. */
public record ModEntry(String id, String name, String version, String description, String authors,
                       ModContainer container, Object screenFactory, List<Path> configFiles) {

    public boolean hasScreen() {
        return screenFactory != null;
    }
}
