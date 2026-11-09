package com.annoyingclient.module;

import com.annoyingclient.AnnoyingClient;
import com.annoyingclient.setting.BooleanSetting;
import com.annoyingclient.setting.KeybindSetting;
import com.annoyingclient.setting.Setting;
import com.annoyingclient.util.Animation;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Base class for every feature in Annoying Client.
 *
 * <p>To add a module: extend this class, declare settings with {@link #add(Setting)} in the
 * constructor, override {@link #onEnable()} / {@link #onDisable()} if needed, and register an
 * instance in {@code ModuleManager#registerDefaults}. The ClickGUI, keybinds, module list and
 * configuration pick it up automatically.
 */
public abstract class Module {

    /** Listener for enable/disable changes (used for notifications and auto-save). */
    public interface ToggleListener {
        /**
         * @param notify whether the user should see a notification (false while loading a profile)
         */
        void onToggle(Module module, boolean enabled, boolean notify);
    }

    private static ToggleListener toggleListener = (m, e, n) -> { };

    protected final Minecraft mc = Minecraft.getInstance();

    private final String name;
    private final String description;
    private final Category category;
    private final boolean enabledByDefault;
    private boolean enabled;

    private final List<Setting<?>> settings = new ArrayList<>();
    private final List<Setting<?>> settingsView = Collections.unmodifiableList(settings);

    protected final KeybindSetting keybind;
    protected final BooleanSetting showInModuleList;

    /** Slides the module in and out of the HUD module list. */
    private final Animation listAnimation = new Animation(0f, 14f);

    protected Module(String name, String description, Category category) {
        this(name, description, category, false, KeybindSetting.NONE);
    }

    protected Module(String name, String description, Category category, boolean enabledByDefault, int defaultKey) {
        this.name = name;
        this.description = description;
        this.category = category;
        this.enabledByDefault = enabledByDefault;
        this.keybind = add(new KeybindSetting("Keybind", "Key that toggles " + name, defaultKey));
        this.showInModuleList = add(new BooleanSetting("Show in Module List", "List this module on the HUD while it is enabled",
                listedByDefault()));
    }

    public static void setToggleListener(ToggleListener listener) {
        toggleListener = listener == null ? (m, e, n) -> { } : listener;
    }

    protected final <S extends Setting<?>> S add(S setting) {
        for (Setting<?> existing : settings) {
            if (existing.getName().equalsIgnoreCase(setting.getName())) {
                throw new IllegalStateException("Duplicate setting '" + setting.getName() + "' in module " + name);
            }
        }
        settings.add(setting);
        return setting;
    }

    public final void toggle() {
        setEnabled(!enabled);
    }

    public final void setEnabled(boolean state) {
        setEnabled(state, true);
    }

    /**
     * @param notify false when restoring state from configuration, so loading a profile does not
     *               produce a burst of "module enabled" notifications
     */
    public void setEnabled(boolean state, boolean notify) {
        if (state == enabled) {
            return;
        }
        if (!isToggleable()) {
            if (state) {
                onActivate();
            }
            return;
        }
        enabled = state;
        try {
            if (state) {
                onEnable();
            } else {
                onDisable();
            }
        } catch (RuntimeException e) {
            AnnoyingClient.LOGGER.error("Module {} failed while {}", name, state ? "enabling" : "disabling", e);
        }
        toggleListener.onToggle(this, state, notify);
    }

    /** Called when the module is enabled. The player and world may be null (for example on the title screen). */
    protected void onEnable() {
    }

    /** Called when the module is disabled. The player and world may be null. */
    protected void onDisable() {
    }

    /**
     * Modules that perform an action instead of staying on (such as the ClickGUI opener) return false here
     * and override {@link #onActivate()}.
     */
    public boolean isToggleable() {
        return true;
    }

    /** Action for non-toggleable modules, run when the keybind is pressed or the module is clicked. */
    protected void onActivate() {
    }

    /**
     * Default for "Show in Module List". Called from the constructor, so overrides must return a constant.
     * HUD elements and always-on helper modules are not listed by default.
     */
    protected boolean listedByDefault() {
        return category != Category.HUD;
    }

    /** Short text shown after the module name in the module list, e.g. "4.0x" for Zoom. Empty for none. */
    public String getDisplayInfo() {
        return "";
    }

    public boolean isVisibleInModuleList() {
        return isToggleable() && showInModuleList.isEnabled();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isEnabledByDefault() {
        return enabledByDefault;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public Category getCategory() {
        return category;
    }

    public KeybindSetting getKeybind() {
        return keybind;
    }

    public List<Setting<?>> getSettings() {
        return settingsView;
    }

    public Setting<?> getSetting(String settingName) {
        for (Setting<?> s : settings) {
            if (s.getName().equalsIgnoreCase(settingName)) {
                return s;
            }
        }
        return null;
    }

    public Animation getListAnimation() {
        return listAnimation;
    }

    /** Resets every setting and the enabled state to defaults. */
    public void resetToDefaults() {
        for (Setting<?> s : settings) {
            s.reset();
        }
        setEnabled(enabledByDefault, false);
    }

    @Override
    public String toString() {
        return name;
    }
}
