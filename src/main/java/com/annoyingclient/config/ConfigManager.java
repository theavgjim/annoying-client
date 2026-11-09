package com.annoyingclient.config;

import com.annoyingclient.AnnoyingClient;
import com.annoyingclient.manager.ModuleManager;
import com.annoyingclient.module.Module;
import com.annoyingclient.notification.NotificationManager;
import com.annoyingclient.notification.NotificationType;
import com.annoyingclient.setting.Setting;
import com.annoyingclient.theme.ThemeManager;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * JSON configuration with named profiles.
 *
 * <pre>
 * config/annoyingclient/
 *   client.json            { "activeProfile": "default" }
 *   profiles/default.json  module states, settings, keybinds, HUD positions, theme
 *   profiles/&lt;name&gt;.json
 * </pre>
 *
 * <p>Any change to a setting or module marks the configuration dirty; it is written at most once
 * per {@link #SAVE_DELAY_MS}, on a background thread, through a temporary file and an atomic move so
 * a crash mid-write cannot corrupt the profile. A profile that cannot be parsed is renamed to
 * {@code *.corrupt-<timestamp>.json}, defaults are used, and the user is notified.
 */
public final class ConfigManager {

    public static final String DEFAULT_PROFILE = "default";
    public static final int FORMAT_VERSION = 1;
    private static final long SAVE_DELAY_MS = 1500;
    private static final Pattern VALID_NAME = Pattern.compile("[A-Za-z0-9 _.-]{1,32}");

    private final Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private final Path root;
    private final Path profilesDir;
    private final Path clientFile;
    private final ModuleManager modules;
    private final ThemeManager theme;
    private final NotificationManager notifications;
    private final ExecutorService writer = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Annoying Client Config Writer");
        t.setDaemon(true);
        return t;
    });

    private String activeProfile = DEFAULT_PROFILE;
    private boolean dirty;
    private long dirtySince;
    private boolean loading;

    public ConfigManager(Path root, ModuleManager modules, ThemeManager theme, NotificationManager notifications) {
        this.root = root;
        this.profilesDir = root.resolve("profiles");
        this.clientFile = root.resolve("client.json");
        this.modules = modules;
        this.theme = theme;
        this.notifications = notifications;
    }

    // ---- lifecycle ---------------------------------------------------------------------------

    /** Loads client.json and the active profile. Called once during client initialization. */
    public void init() {
        try {
            Files.createDirectories(profilesDir);
        } catch (IOException e) {
            AnnoyingClient.LOGGER.error("Cannot create {}", profilesDir, e);
        }
        readClientFile();
        if (!Files.exists(profilePath(activeProfile))) {
            if (!activeProfile.equals(DEFAULT_PROFILE)) {
                AnnoyingClient.LOGGER.warn("Active profile '{}' is missing, falling back to '{}'", activeProfile, DEFAULT_PROFILE);
                activeProfile = DEFAULT_PROFILE;
            }
            applyDefaults();
            saveNow();
            writeClientFile();
            AnnoyingClient.LOGGER.info("Created default configuration in {}", root);
            return;
        }
        loadProfile(activeProfile, false);
    }

    /** Called from the setting/module change hooks. */
    public void markDirty() {
        if (loading) {
            return;
        }
        if (!dirty) {
            dirty = true;
            dirtySince = System.currentTimeMillis();
        }
    }

    /** Called every client tick; writes the profile once changes have settled. */
    public void tick() {
        if (dirty && System.currentTimeMillis() - dirtySince >= SAVE_DELAY_MS) {
            saveAsync();
        }
    }

    /** Synchronous save used on shutdown. */
    public void shutdown() {
        if (dirty) {
            saveNow();
        }
        writer.shutdown();
        try {
            writer.awaitTermination(3, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ---- profiles ----------------------------------------------------------------------------

    public String getActiveProfile() {
        return activeProfile;
    }

    public List<String> listProfiles() {
        List<String> names = new ArrayList<>();
        if (Files.isDirectory(profilesDir)) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(profilesDir, "*.json")) {
                for (Path p : stream) {
                    String file = p.getFileName().toString();
                    if (!file.contains(".corrupt-")) {
                        names.add(file.substring(0, file.length() - ".json".length()));
                    }
                }
            } catch (IOException e) {
                AnnoyingClient.LOGGER.error("Cannot list profiles", e);
            }
        }
        if (!names.contains(activeProfile)) {
            names.add(activeProfile);
        }
        names.sort(String.CASE_INSENSITIVE_ORDER);
        return names;
    }

    public static boolean isValidName(String name) {
        return name != null && VALID_NAME.matcher(name).matches() && !name.isBlank()
                && !name.startsWith(".") && !name.contains("..");
    }

    /** Saves the current state as a new profile and switches to it. */
    public boolean createProfile(String name) {
        name = name == null ? "" : name.trim();
        if (!isValidName(name)) {
            notifications.error("Invalid profile name", "Use 1-32 letters, digits, spaces, - _ .");
            return false;
        }
        if (Files.exists(profilePath(name))) {
            notifications.error("Profile exists", "'" + name + "' already exists");
            return false;
        }
        saveNow();
        activeProfile = name;
        saveNow();
        writeClientFile();
        notifications.push(NotificationType.CONFIG, "Profile created", "Now using '" + name + "'");
        return true;
    }

    /** Saves the current profile, then loads {@code name}. */
    public boolean switchProfile(String name) {
        if (name.equals(activeProfile)) {
            return true;
        }
        if (!Files.exists(profilePath(name))) {
            notifications.error("Profile not found", name);
            return false;
        }
        saveNow();
        boolean ok = loadProfile(name, true);
        if (ok) {
            writeClientFile();
        }
        return ok;
    }

    public boolean deleteProfile(String name) {
        if (name.equals(DEFAULT_PROFILE)) {
            notifications.error("Cannot delete", "The default profile is required");
            return false;
        }
        try {
            if (name.equals(activeProfile)) {
                loadProfile(DEFAULT_PROFILE, false);
                writeClientFile();
            }
            Files.deleteIfExists(profilePath(name));
            notifications.push(NotificationType.CONFIG, "Profile deleted", "'" + name + "' was removed");
            return true;
        } catch (IOException e) {
            AnnoyingClient.LOGGER.error("Cannot delete profile {}", name, e);
            notifications.error("Delete failed", e.getMessage());
            return false;
        }
    }

    /** Resets every module, setting and the theme in the active profile to defaults. */
    public void resetActiveProfile() {
        applyDefaults();
        saveNow();
        notifications.push(NotificationType.CONFIG, "Profile reset", "'" + activeProfile + "' restored to defaults");
    }

    public void reloadActiveProfile() {
        loadProfile(activeProfile, true);
    }

    public void saveManually() {
        saveNow();
        notifications.push(NotificationType.CONFIG, "Configuration saved", "Profile '" + activeProfile + "'");
    }

    // ---- serialization -----------------------------------------------------------------------

    private Path profilePath(String name) {
        return profilesDir.resolve(name + ".json");
    }

    private JsonObject serialize() {
        JsonObject root = new JsonObject();
        root.addProperty("formatVersion", FORMAT_VERSION);
        root.addProperty("client", AnnoyingClient.NAME);
        AnnoyingClient instance = AnnoyingClient.getInstance();
        root.addProperty("clientVersion", instance != null ? instance.getVersion() : "dev");

        JsonObject theme = new JsonObject();
        for (Setting<?> s : this.theme.getSettings()) {
            theme.add(s.getName(), s.toJson());
        }
        root.add("theme", theme);

        JsonObject mods = new JsonObject();
        for (Module module : modules.getModules()) {
            JsonObject m = new JsonObject();
            m.addProperty("enabled", module.isEnabled());
            JsonObject settings = new JsonObject();
            for (Setting<?> s : module.getSettings()) {
                settings.add(s.getName(), s.toJson());
            }
            m.add("settings", settings);
            mods.add(module.getName(), m);
        }
        root.add("modules", mods);
        return root;
    }

    /** Applies a parsed profile. Unknown modules/settings are ignored; missing ones keep defaults. */
    private void apply(JsonObject root) {
        int problems = 0;
        if (root.has("theme") && root.get("theme").isJsonObject()) {
            JsonObject themeObj = root.getAsJsonObject("theme");
            // Apply the preset first so explicit colors saved after it win.
            for (Setting<?> s : theme.getSettings()) {
                if (themeObj.has(s.getName()) && !s.fromJson(themeObj.get(s.getName()))) {
                    problems++;
                }
            }
            // Second pass: color settings may have been overwritten by the preset listener.
            for (Setting<?> s : theme.getSettings()) {
                if (!s.getName().equals("Theme") && themeObj.has(s.getName())) {
                    s.fromJson(themeObj.get(s.getName()));
                }
            }
        }
        JsonObject mods = root.has("modules") && root.get("modules").isJsonObject() ? root.getAsJsonObject("modules") : new JsonObject();
        for (Module module : modules.getModules()) {
            JsonElement element = mods.get(module.getName());
            if (element == null || !element.isJsonObject()) {
                continue;
            }
            JsonObject m = element.getAsJsonObject();
            if (m.has("settings") && m.get("settings").isJsonObject()) {
                JsonObject settings = m.getAsJsonObject("settings");
                for (Setting<?> s : module.getSettings()) {
                    JsonElement v = settings.get(s.getName());
                    if (v != null && !s.fromJson(v)) {
                        problems++;
                        AnnoyingClient.LOGGER.warn("Ignoring invalid value {} for {} / {}", v, module.getName(), s.getName());
                    }
                }
            }
            if (m.has("enabled") && m.get("enabled").isJsonPrimitive()) {
                module.setEnabled(m.get("enabled").getAsBoolean(), false);
            }
        }
        if (problems > 0) {
            notifications.push(NotificationType.WARNING, "Some settings were invalid", problems + " value(s) reset to defaults");
        }
    }

    private void applyDefaults() {
        loading = true;
        try {
            for (Setting<?> s : theme.getSettings()) {
                s.reset();
            }
            for (Module module : modules.getModules()) {
                module.resetToDefaults();
            }
        } finally {
            loading = false;
        }
        theme.syncStatics();
    }

    private boolean loadProfile(String name, boolean notify) {
        Path path = profilePath(name);
        JsonObject json;
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            JsonElement parsed = JsonParser.parseReader(reader);
            if (!parsed.isJsonObject()) {
                throw new JsonParseException("Root is not a JSON object");
            }
            json = parsed.getAsJsonObject();
        } catch (IOException e) {
            AnnoyingClient.LOGGER.error("Cannot read profile {}", path, e);
            notifications.error("Config read failed", e.getMessage() == null ? name : e.getMessage());
            return false;
        } catch (JsonParseException | IllegalStateException e) {
            Path backup = backupCorrupt(path);
            AnnoyingClient.LOGGER.error("Profile {} is corrupted; moved to {} and using defaults", path, backup, e);
            activeProfile = name;
            applyDefaults();
            saveNow();
            notifications.error("Corrupted profile '" + name + "'", "Backed up and reset to defaults");
            return true;
        }

        activeProfile = name;
        loading = true;
        try {
            // Start from defaults so values missing from the file do not leak from the previous profile.
            for (Setting<?> s : theme.getSettings()) {
                s.reset();
            }
            for (Module module : modules.getModules()) {
                module.resetToDefaults();
            }
            apply(json);
        } finally {
            loading = false;
        }
        theme.syncStatics();
        dirty = false;
        AnnoyingClient.LOGGER.info("Loaded profile '{}'", name);
        if (notify) {
            notifications.push(NotificationType.CONFIG, "Profile loaded", "Now using '" + name + "'");
        }
        return true;
    }

    private Path backupCorrupt(Path path) {
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.ROOT));
        String file = path.getFileName().toString();
        Path backup = path.resolveSibling(file.substring(0, file.length() - 5) + ".corrupt-" + stamp + ".json");
        try {
            Files.move(path, backup, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            AnnoyingClient.LOGGER.error("Cannot back up corrupted file {}", path, e);
        }
        return backup;
    }

    private void saveAsync() {
        dirty = false;
        String text = gson.toJson(serialize());
        Path path = profilePath(activeProfile);
        writer.execute(() -> writeAtomically(path, text));
    }

    private void saveNow() {
        dirty = false;
        writeAtomically(profilePath(activeProfile), gson.toJson(serialize()));
    }

    private void readClientFile() {
        if (!Files.exists(clientFile)) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(clientFile, StandardCharsets.UTF_8)) {
            JsonElement parsed = JsonParser.parseReader(reader);
            if (parsed.isJsonObject() && parsed.getAsJsonObject().has("activeProfile")) {
                String name = parsed.getAsJsonObject().get("activeProfile").getAsString();
                if (isValidName(name)) {
                    activeProfile = name;
                }
            }
        } catch (IOException | RuntimeException e) {
            backupCorrupt(clientFile);
            AnnoyingClient.LOGGER.error("client.json is unreadable; using the default profile", e);
        }
    }

    private void writeClientFile() {
        JsonObject o = new JsonObject();
        o.addProperty("activeProfile", activeProfile);
        writeAtomically(clientFile, gson.toJson(o));
    }

    private static synchronized void writeAtomically(Path path, String text) {
        try {
            Files.createDirectories(path.getParent());
            Path tmp = path.resolveSibling(path.getFileName() + ".tmp");
            try (Writer w = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
                w.write(text);
            }
            try {
                Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            AnnoyingClient.LOGGER.error("Failed to write {}", path, e);
        }
    }
}
