package com.annoyingclient.modconfig;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;

import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * An editable view of one mod config file.
 *
 * <p>Only simple values (true/false, numbers, text) are editable; lists and nested tables are kept as
 * they are. .properties and .toml files are rewritten line by line, so comments and unknown lines stay
 * untouched. JSON files are rewritten with Gson (comments in JSON5 files are not preserved; the
 * editor says so). A backup (*.bak) is written before the first save.
 */
public final class ConfigFile {

    public enum Kind { BOOLEAN, NUMBER, TEXT }

    public static final class Entry {
        public final String key;
        public final Kind kind;
        public String value;
        final int line;          // line index for line-based formats, -1 for JSON
        final String[] jsonPath; // path for JSON

        Entry(String key, Kind kind, String value, int line, String[] jsonPath) {
            this.key = key;
            this.kind = kind;
            this.value = value;
            this.line = line;
            this.jsonPath = jsonPath;
        }
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Pattern NUMBER = Pattern.compile("[-+]?(\\d+\\.?\\d*|\\.\\d+)([eE][-+]?\\d+)?");
    private static final Pattern TOML_LINE = Pattern.compile("^(\\s*)([A-Za-z0-9_.\"'-]+)(\\s*=\\s*)(.*?)(\\s*(#.*)?)$");
    private static final Pattern PROP_LINE = Pattern.compile("^(\\s*)([^#!=:\\s][^=:]*?)(\\s*[=:]\\s*)(.*)$");
    private static final int MAX_ENTRIES = 250;

    private final Path path;
    private final String format;
    private final List<Entry> entries = new ArrayList<>();
    private List<String> lines = List.of();
    private JsonElement json;
    private String error;

    private ConfigFile(Path path) {
        this.path = path;
        String n = path.getFileName().toString().toLowerCase(Locale.ROOT);
        this.format = n.endsWith(".json") || n.endsWith(".json5") ? "json" : n.endsWith(".toml") ? "toml" : "properties";
    }

    public static ConfigFile load(Path path) {
        ConfigFile f = new ConfigFile(path);
        try {
            String text = Files.readString(path, StandardCharsets.UTF_8);
            switch (f.format) {
                case "json" -> f.loadJson(text);
                case "toml" -> f.loadLines(text, true);
                default -> f.loadLines(text, false);
            }
        } catch (IOException | RuntimeException e) {
            f.error = "Can't read this file: " + e.getMessage();
        }
        return f;
    }

    public Path path() {
        return path;
    }

    public String format() {
        return format;
    }

    public List<Entry> entries() {
        return entries;
    }

    public String error() {
        return error;
    }

    public boolean isJson5() {
        return path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".json5");
    }

    private void loadJson(String text) throws IOException {
        JsonReader reader = new JsonReader(new StringReader(text));
        reader.setStrictness(Strictness.LENIENT);
        json = com.google.gson.JsonParser.parseReader(reader);
        flatten(json, new ArrayList<>());
    }

    private void flatten(JsonElement e, List<String> path) {
        if (entries.size() >= MAX_ENTRIES) {
            return;
        }
        if (e.isJsonObject()) {
            for (var en : e.getAsJsonObject().entrySet()) {
                path.add(en.getKey());
                flatten(en.getValue(), path);
                path.removeLast();
            }
        } else if (e.isJsonPrimitive() && !path.isEmpty()) {
            JsonPrimitive p = e.getAsJsonPrimitive();
            Kind kind = p.isBoolean() ? Kind.BOOLEAN : p.isNumber() ? Kind.NUMBER : Kind.TEXT;
            entries.add(new Entry(String.join(" > ", path), kind, p.getAsString(), -1, path.toArray(new String[0])));
        }
    }

    private void loadLines(String text, boolean toml) {
        lines = new ArrayList<>(List.of(text.split("\r?\n", -1)));
        String section = "";
        for (int i = 0; i < lines.size() && entries.size() < MAX_ENTRIES; i++) {
            String line = lines.get(i);
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("!") || trimmed.startsWith(";")) {
                continue;
            }
            if (toml && trimmed.startsWith("[")) {
                section = trimmed.replaceAll("[\\[\\]]", "").trim();
                continue;
            }
            Matcher m = (toml ? TOML_LINE : PROP_LINE).matcher(line);
            if (!m.matches()) {
                continue;
            }
            String key = m.group(2).trim();
            String raw = m.group(4).trim();
            if (toml && (raw.startsWith("[") || raw.startsWith("{"))) {
                continue; // arrays and inline tables stay as they are
            }
            Kind kind;
            String value = raw;
            if (raw.equalsIgnoreCase("true") || raw.equalsIgnoreCase("false")) {
                kind = Kind.BOOLEAN;
            } else if (NUMBER.matcher(raw).matches()) {
                kind = Kind.NUMBER;
            } else {
                kind = Kind.TEXT;
                if (toml && raw.length() >= 2 && (raw.startsWith("\"") && raw.endsWith("\"") || raw.startsWith("'") && raw.endsWith("'"))) {
                    value = raw.substring(1, raw.length() - 1);
                }
            }
            entries.add(new Entry(section.isEmpty() ? key : section + " > " + key, kind, value, i, null));
        }
    }

    /** Checks every value; returns a message for the first invalid one, or null when all are valid. */
    public String validate() {
        for (Entry e : entries) {
            if (e.kind == Kind.NUMBER && !NUMBER.matcher(e.value.trim()).matches()) {
                return "\"" + e.key + "\" must be a number";
            }
            if (e.kind == Kind.BOOLEAN && !e.value.equals("true") && !e.value.equals("false")) {
                return "\"" + e.key + "\" must be true or false";
            }
        }
        return null;
    }

    /** Writes the file (atomically, after a one-time .bak backup). */
    public void save() throws IOException {
        String problem = validate();
        if (problem != null) {
            throw new IOException(problem);
        }
        String out;
        if ("json".equals(format)) {
            for (Entry e : entries) {
                JsonObject parent = json.getAsJsonObject();
                for (int i = 0; i < e.jsonPath.length - 1; i++) {
                    parent = parent.getAsJsonObject(e.jsonPath[i]);
                }
                String leaf = e.jsonPath[e.jsonPath.length - 1];
                parent.add(leaf, switch (e.kind) {
                    case BOOLEAN -> new JsonPrimitive(Boolean.parseBoolean(e.value));
                    case NUMBER -> new JsonPrimitive(toNumber(e.value.trim()));
                    case TEXT -> new JsonPrimitive(e.value);
                });
            }
            out = GSON.toJson(json) + "\n";
        } else {
            boolean toml = "toml".equals(format);
            List<String> copy = new ArrayList<>(lines);
            for (Entry e : entries) {
                Matcher m = (toml ? TOML_LINE : PROP_LINE).matcher(copy.get(e.line));
                if (!m.matches()) {
                    continue;
                }
                String raw = m.group(4).trim();
                String value = e.value;
                if (toml && e.kind == Kind.TEXT) {
                    char q = raw.startsWith("'") ? '\'' : '"';
                    value = q + value.replace("\\", "\\\\").replace(String.valueOf(q), "\\" + q) + q;
                }
                String tail = toml && m.group(5) != null ? m.group(5) : "";
                copy.set(e.line, m.group(1) + m.group(2) + m.group(3) + value + tail);
            }
            out = String.join("\n", copy);
            lines = copy;
        }
        Path backup = backupOf(path);
        if (!Files.exists(backup)) {
            Files.copy(path, backup);
        }
        Path tmp = path.resolveSibling(path.getFileName() + ".tmp");
        Files.writeString(tmp, out, StandardCharsets.UTF_8);
        Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
    }

    /** The copy of the file taken before the first save from the Mod Configs tab. */
    public static Path backupOf(Path path) {
        return path.resolveSibling(path.getFileName() + ".bak");
    }

    /** Puts the original file back and removes the backup. */
    public static void restoreBackup(Path path) throws IOException {
        Path backup = backupOf(path);
        if (!Files.exists(backup)) {
            throw new IOException("There is no backup of " + path.getFileName());
        }
        Files.move(backup, path, StandardCopyOption.REPLACE_EXISTING);
    }

    private static Number toNumber(String s) {
        BigDecimal d = new BigDecimal(s);
        if (d.scale() <= 0 && !s.contains(".") && !s.toLowerCase(Locale.ROOT).contains("e")) {
            try {
                return d.longValueExact();
            } catch (ArithmeticException ignored) {
                return d;
            }
        }
        return d.doubleValue();
    }
}
