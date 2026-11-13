package com.annoyingclient.modconfig;

import com.annoyingclient.AnnoyingClient;
import net.minecraft.client.Minecraft;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Makes a mod pick up a config file that was just saved in the Mod Configs tab, without restarting.
 *
 * <p>Most mods read their config once, at startup, through a config library. This reloads the file
 * through those libraries (Cloth Config's AutoConfig and YetAnotherConfigLib), and has dedicated
 * handling for a few popular mods (Sodium, Iris). Files that switch mixins on and off (Lithium,
 * FerriteCore, ModernFix...) are read before the game starts, so nothing can apply them live; those
 * are labelled "needs restart".
 */
public final class LiveReload {

    /** How a saved file takes effect. */
    public enum Mode {
        /** Reloaded right away. */
        LIVE("Applies instantly"),
        /** Mixin switches: only read when the game starts. */
        RESTART("Needs a restart"),
        /** No reload hook known for this mod; it may read the file only at startup. */
        UNKNOWN("Applies on restart (use Settings for instant changes)");

        public final String label;

        Mode(String label) {
            this.label = label;
        }
    }

    /** YACL config handlers, collected as they are created (see YaclConfigHandlerMixin). */
    private static final Set<Object> YACL_HANDLERS = Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));

    private LiveReload() {
    }

    public static void registerYaclHandler(Object handler) {
        YACL_HANDLERS.add(handler);
    }

    /** Whether this file only switches code on or off at startup (mixin configs). */
    public static boolean isStartupOnly(Path file) {
        String n = file.getFileName().toString().toLowerCase(Locale.ROOT);
        return n.contains("mixin") || n.equals("lithium.properties") || n.equals("vmp.properties")
                || n.equals("c2me.toml") || n.equals("krypton.properties");
    }

    /** What saving this file is expected to do, before the user saves. */
    public static Mode predict(ModEntry mod, Path file) {
        if (isStartupOnly(file)) {
            return Mode.RESTART;
        }
        if (autoConfigHolder(file) != null || yaclHandler(file) != null || dedicated(mod.id(), file) != null) {
            return Mode.LIVE;
        }
        return Mode.UNKNOWN;
    }

    /** Reloads {@code file} into the running mod. Returns how it was applied; never throws. */
    public static Mode apply(ModEntry mod, Path file) {
        if (isStartupOnly(file)) {
            return Mode.RESTART;
        }
        try {
            Object holder = autoConfigHolder(file);
            if (holder != null && invokeBoolean(holder, "load")) {
                return Mode.LIVE;
            }
            Object handler = yaclHandler(file);
            if (handler != null && invokeBoolean(handler, "load")) {
                return Mode.LIVE;
            }
            Runnable special = dedicated(mod.id(), file);
            if (special != null) {
                special.run();
                return Mode.LIVE;
            }
        } catch (Throwable t) {
            AnnoyingClient.LOGGER.warn("Live reload of {} failed", file, t);
        }
        return Mode.UNKNOWN;
    }

    // ---- Cloth Config (AutoConfig) ------------------------------------------------------------

    private static Object autoConfigHolder(Path file) {
        Class<?> autoConfig = classOrNull("me.shedaniel.autoconfig.AutoConfig");
        if (autoConfig == null) {
            return null;
        }
        try {
            Field f = autoConfig.getDeclaredField("holders");
            f.setAccessible(true);
            Map<?, ?> holders = (Map<?, ?>) f.get(null);
            List<Object> all;
            synchronized (holders) {
                all = new ArrayList<>(holders.values());
            }
            for (Object holder : all) {
                Object serializer = callOrNull(holder, "getSerializer");
                Path p = serializer == null ? null : findPath(serializer);
                if (p != null ? same(p, file) : sameName(configName(holder), file)) {
                    return holder;
                }
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            AnnoyingClient.LOGGER.debug("AutoConfig lookup failed: {}", e.toString());
        }
        return null;
    }

    /** The {@code @Config(name = ...)} of an AutoConfig holder: its file name without extension. */
    private static String configName(Object holder) {
        Object type = callOrNull(holder, "getConfigClass");
        if (!(type instanceof Class<?> c)) {
            return null;
        }
        for (var a : c.getAnnotations()) {
            if (a.annotationType().getName().equals("me.shedaniel.autoconfig.annotation.Config")) {
                Object name = callOrNull(a, "name");
                return name instanceof String s ? s : null;
            }
        }
        return null;
    }

    // ---- YetAnotherConfigLib ------------------------------------------------------------------

    private static Object yaclHandler(Path file) {
        List<Object> all;
        synchronized (YACL_HANDLERS) {
            all = new ArrayList<>(YACL_HANDLERS);
        }
        for (Object handler : all) {
            Object serializer = callOrNull(handler, "serializer");
            Path p = serializer == null ? null : findPath(serializer);
            if (p != null && same(p, file)) {
                return handler;
            }
        }
        return null;
    }

    // ---- mods with their own config code ------------------------------------------------------

    private static Runnable dedicated(String modId, Path file) {
        String name = file.getFileName().toString();
        if (modId.equals("sodium") && name.equals("sodium-options.json")) {
            Class<?> mod = classOrNull("net.caffeinemc.mods.sodium.client.SodiumClientMod");
            if (mod == null) {
                return null;
            }
            return () -> {
                try {
                    Method load = mod.getDeclaredMethod("loadConfig");
                    load.setAccessible(true);
                    Object options = load.invoke(null);
                    Field f = mod.getDeclaredField("OPTIONS");
                    f.setAccessible(true);
                    f.set(null, options);
                    rebuildChunks();
                } catch (ReflectiveOperationException e) {
                    throw new IllegalStateException("Sodium changed its options code", e);
                }
            };
        }
        if (modId.equals("iris") && name.equals("iris.properties")) {
            Class<?> iris = classOrNull("net.irisshaders.iris.Iris");
            if (iris == null) {
                return null;
            }
            return () -> {
                try {
                    Object config = iris.getMethod("getIrisConfig").invoke(null);
                    config.getClass().getMethod("load").invoke(config);
                    iris.getMethod("reload").invoke(null); // re-applies the shader pack setting
                } catch (ReflectiveOperationException e) {
                    throw new IllegalStateException("Iris changed its config code", e);
                }
            };
        }
        return null;
    }

    private static void rebuildChunks() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.levelRenderer != null && mc.level != null) {
            mc.levelRenderer.allChanged();
        }
    }

    // ---- helpers ------------------------------------------------------------------------------

    private static Class<?> classOrNull(String name) {
        try {
            return Class.forName(name, false, LiveReload.class.getClassLoader());
        } catch (ClassNotFoundException | LinkageError e) {
            return null;
        }
    }

    private static Object callOrNull(Object target, String method) {
        for (Class<?> c = target.getClass(); c != null; c = c.getSuperclass()) {
            try {
                Method m = c.getDeclaredMethod(method);
                m.setAccessible(true);
                return m.invoke(target);
            } catch (NoSuchMethodException ignored) {
                // keep looking in the superclass
            } catch (ReflectiveOperationException | RuntimeException e) {
                return null;
            }
        }
        for (Class<?> i : target.getClass().getInterfaces()) {
            try {
                return i.getMethod(method).invoke(target);
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // not on this interface
            }
        }
        return null;
    }

    private static boolean invokeBoolean(Object target, String method) {
        Object r = callOrNull(target, method);
        return !(r instanceof Boolean b) || b;
    }

    /** The first {@link Path} field (or getConfigPath()) of a config serializer. */
    private static Path findPath(Object serializer) {
        Object viaMethod = callOrNull(serializer, "getConfigPath");
        if (viaMethod instanceof Path p) {
            return p;
        }
        for (Class<?> c = serializer.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (Path.class.isAssignableFrom(f.getType())) {
                    try {
                        f.setAccessible(true);
                        Object v = f.get(serializer);
                        if (v instanceof Path p) {
                            return p;
                        }
                    } catch (ReflectiveOperationException | RuntimeException ignored) {
                        // try the next field
                    }
                }
            }
        }
        return null;
    }

    private static boolean same(Path a, Path b) {
        try {
            if (Files.exists(a) && Files.exists(b)) {
                return Files.isSameFile(a, b);
            }
        } catch (Exception ignored) {
            // fall back to comparing paths
        }
        return a.toAbsolutePath().normalize().equals(b.toAbsolutePath().normalize());
    }

    private static boolean sameName(String configName, Path file) {
        if (configName == null) {
            return false;
        }
        String n = file.getFileName().toString();
        int dot = n.lastIndexOf('.');
        return (dot < 0 ? n : n.substring(0, dot)).equals(configName);
    }
}
