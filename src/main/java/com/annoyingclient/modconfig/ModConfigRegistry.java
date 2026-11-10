package com.annoyingclient.modconfig;

import com.annoyingclient.AnnoyingClient;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.entrypoint.EntrypointContainer;
import net.fabricmc.loader.api.metadata.ModMetadata;
import net.fabricmc.loader.api.metadata.Person;
import net.minecraft.client.gui.screens.Screen;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Finds installed mods, their config screens and their config files.
 *
 * <p>Config screens come from the {@code modmenu} entrypoint that most mods already provide for Mod
 * Menu ({@code getModConfigScreenFactory} and {@code getProvidedConfigScreenFactories}). They are called
 * reflectively, so nothing here needs Mod Menu on the classpath.
 */
public final class ModConfigRegistry {

    private static final Set<String> HIDDEN = Set.of("minecraft", "java", "fabricloader", "mixinextras");
    private static final Set<String> CONFIG_EXT = Set.of("json", "json5", "properties", "toml", "cfg", "txt");

    private static List<ModEntry> cache;

    private ModConfigRegistry() {
    }

    public static synchronized List<ModEntry> mods() {
        if (cache == null) {
            cache = scan();
        }
        return cache;
    }

    /** Rescans config files (a mod may have just created its file). */
    public static synchronized void refresh() {
        cache = null;
    }

    private static Map<String, Object> screenFactories() {
        Map<String, Object> out = new HashMap<>();
        List<EntrypointContainer<Object>> containers;
        try {
            containers = FabricLoader.getInstance().getEntrypointContainers("modmenu", Object.class);
        } catch (Throwable t) {
            return out;
        }
        for (EntrypointContainer<Object> c : containers) {
            String provider = c.getProvider().getMetadata().getId();
            try {
                Object api = c.getEntrypoint();
                Method single = findMethod(api.getClass(), "getModConfigScreenFactory");
                if (single != null) {
                    Object factory = single.invoke(api);
                    if (factory != null) {
                        out.putIfAbsent(provider, factory);
                    }
                }
                Method provided = findMethod(api.getClass(), "getProvidedConfigScreenFactories");
                if (provided != null && provided.invoke(api) instanceof Map<?, ?> map) {
                    map.forEach((k, v) -> {
                        if (k instanceof String id && v != null) {
                            out.putIfAbsent(id, v);
                        }
                    });
                }
            } catch (Throwable t) {
                AnnoyingClient.LOGGER.debug("Config screen entrypoint of {} failed: {}", provider, t.toString());
            }
        }
        return out;
    }

    private static Method findMethod(Class<?> type, String name) {
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            for (Method m : c.getDeclaredMethods()) {
                if (m.getName().equals(name) && m.getParameterCount() == 0) {
                    m.setAccessible(true);
                    return m;
                }
            }
        }
        for (Class<?> i : type.getInterfaces()) {
            for (Method m : i.getMethods()) {
                if (m.getName().equals(name) && m.getParameterCount() == 0) {
                    return m;
                }
            }
        }
        return null;
    }

    /** Creates the mod's own config screen, returning to {@code parent} when it closes; null if it fails. */
    public static Screen createScreen(ModEntry entry, Screen parent) {
        Object factory = entry.screenFactory();
        if (factory == null) {
            return null;
        }
        try {
            for (Class<?> i : allInterfaces(factory.getClass())) {
                for (Method m : i.getMethods()) {
                    if (m.getName().equals("create") && m.getParameterCount() == 1
                            && m.getParameterTypes()[0].isAssignableFrom(Screen.class)) {
                        Object screen = m.invoke(factory, parent);
                        return screen instanceof Screen s ? s : null;
                    }
                }
            }
        } catch (Throwable t) {
            AnnoyingClient.LOGGER.error("Config screen of {} failed to open", entry.id(), t);
        }
        return null;
    }

    private static List<Class<?>> allInterfaces(Class<?> type) {
        List<Class<?>> out = new ArrayList<>();
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            for (Class<?> i : c.getInterfaces()) {
                out.add(i);
                out.addAll(allInterfaces(i));
            }
        }
        return out;
    }

    private static List<ModEntry> scan() {
        Map<String, Object> factories = screenFactories();
        Path configDir = FabricLoader.getInstance().getConfigDir();
        List<Path> allConfigs = listConfigs(configDir);
        List<ModEntry> out = new ArrayList<>();
        for (ModContainer mod : FabricLoader.getInstance().getAllMods()) {
            ModMetadata meta = mod.getMetadata();
            String id = meta.getId();
            // Skip built-ins and jar-in-jar libraries bundled inside another mod.
            if (HIDDEN.contains(id) || mod.getContainingMod().isPresent() || "builtin".equals(meta.getType())
                    || id.startsWith("fabric-") && !id.equals("fabric-api") && !id.startsWith("fabric-language-")) {
                continue; // Fabric API modules are listed once, as Fabric API
            }
            String authors = meta.getAuthors().stream().map(Person::getName).limit(3).collect(Collectors.joining(", "));
            out.add(new ModEntry(id, meta.getName(), meta.getVersion().getFriendlyString(), meta.getDescription(), authors,
                    mod, factories.get(id),
                    // Annoying Client's own settings are the ClickGUI itself, so its files are not offered here.
                    id.equals(AnnoyingClient.MOD_ID) ? List.of() : matchConfigs(id, meta.getName(), configDir, allConfigs)));
        }
        out.sort((a, b) -> {
            int rank = Boolean.compare(b.hasScreen() || !b.configFiles().isEmpty(), a.hasScreen() || !a.configFiles().isEmpty());
            return rank != 0 ? rank : a.name().compareToIgnoreCase(b.name());
        });
        return out;
    }

    private static List<Path> listConfigs(Path dir) {
        try (Stream<Path> s = Files.walk(dir, 2)) {
            return s.filter(Files::isRegularFile).filter(p -> CONFIG_EXT.contains(ext(p))).toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    private static String ext(Path p) {
        String n = p.getFileName().toString();
        int dot = n.lastIndexOf('.');
        return dot < 0 ? "" : n.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static String norm(String s) {
        return s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    /** Config files named after the mod (config/sodium-options.json, config/iris.properties, config/xaero/...). */
    private static List<Path> matchConfigs(String id, String name, Path dir, List<Path> all) {
        String nid = norm(id);
        String nname = norm(name);
        List<Path> out = new ArrayList<>();
        for (Path p : all) {
            Path rel = dir.relativize(p);
            String first = norm(rel.getName(0).toString().replaceFirst("\\.[^.]+$", ""));
            if (nid.length() >= 3 && (first.equals(nid) || first.startsWith(nid))
                    || nname.length() >= 4 && first.startsWith(nname)) {
                out.add(p);
            }
        }
        out.sort(null);
        return out.size() > 12 ? out.subList(0, 12) : out;
    }
}
