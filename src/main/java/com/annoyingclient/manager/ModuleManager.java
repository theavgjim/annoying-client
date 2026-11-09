package com.annoyingclient.manager;

import com.annoyingclient.module.Category;
import com.annoyingclient.module.Module;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Registry of every module: registration, lookup by class or name, category filtering and enabled lists. */
public final class ModuleManager {

    private final List<Module> modules = new ArrayList<>();
    private final List<Module> modulesView = Collections.unmodifiableList(modules);
    private final Map<Class<? extends Module>, Module> byClass = new LinkedHashMap<>();
    private final Map<String, Module> byName = new LinkedHashMap<>();
    private final Map<Category, List<Module>> byCategory = new EnumMap<>(Category.class);

    public ModuleManager() {
        for (Category category : Category.values()) {
            byCategory.put(category, new ArrayList<>());
        }
    }

    public <M extends Module> M register(M module) {
        String key = module.getName().toLowerCase(Locale.ROOT);
        if (byName.containsKey(key)) {
            throw new IllegalStateException("A module named '" + module.getName() + "' is already registered");
        }
        modules.add(module);
        byClass.put(module.getClass(), module);
        byName.put(key, module);
        byCategory.get(module.getCategory()).add(module);
        return module;
    }

    public List<Module> getModules() {
        return modulesView;
    }

    @SuppressWarnings("unchecked")
    public <M extends Module> M get(Class<M> type) {
        return (M) byClass.get(type);
    }

    public Module get(String name) {
        return name == null ? null : byName.get(name.toLowerCase(Locale.ROOT));
    }

    public List<Module> getByCategory(Category category) {
        return Collections.unmodifiableList(byCategory.get(category));
    }

    public List<Module> getEnabled() {
        List<Module> enabled = new ArrayList<>();
        for (Module m : modules) {
            if (m.isEnabled()) {
                enabled.add(m);
            }
        }
        return enabled;
    }

    /** Case-insensitive search over module names and descriptions. */
    public List<Module> search(String query) {
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) {
            return modulesView;
        }
        List<Module> result = new ArrayList<>();
        for (Module m : modules) {
            if (m.getName().toLowerCase(Locale.ROOT).contains(q)) {
                result.add(m);
            }
        }
        for (Module m : modules) {
            if (!result.contains(m) && m.getDescription().toLowerCase(Locale.ROOT).contains(q)) {
                result.add(m);
            }
        }
        return result;
    }

    public void setEnabled(Module module, boolean enabled) {
        module.setEnabled(enabled);
    }
}
