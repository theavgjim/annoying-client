package com.annoyingclient.theme;

import com.annoyingclient.render.TextRenderer;
import com.annoyingclient.setting.BooleanSetting;
import com.annoyingclient.setting.ColorSetting;
import com.annoyingclient.setting.ModeSetting;
import com.annoyingclient.setting.NumberSetting;
import com.annoyingclient.setting.Setting;
import com.annoyingclient.util.Animation;
import com.annoyingclient.util.ColorUtil;

import java.util.List;

/**
 * Owns the active theme and all appearance settings.
 *
 * <p>Picking a preset copies its colors into the editable color settings; editing a color afterwards
 * switches the preset label to "Custom". Every getter reads the settings directly, so changes apply
 * to the ClickGUI, HUD and notifications on the very next frame.
 */
public final class ThemeManager {

    public static final String CUSTOM = "Custom";

    public static final List<Theme> PRESETS = List.of(
            new Theme("Midnight Purple", 0xFF8B5CF6, 0xFF3B82F6, 0xFF0E0E16, 0xFFECECF4),
            new Theme("Dark Blue", 0xFF3B82F6, 0xFF06B6D4, 0xFF0B1120, 0xFFE6EDF7),
            new Theme("Crimson", 0xFFE11D48, 0xFFF97316, 0xFF140B0E, 0xFFF5E9EC),
            new Theme("Emerald", 0xFF10B981, 0xFF22D3EE, 0xFF09140F, 0xFFE7F5EF),
            new Theme("Monochrome", 0xFFE5E5E5, 0xFF8A8A8A, 0xFF0E0E0E, 0xFFF2F2F2));

    private final ModeSetting preset;
    private final ColorSetting accent;
    private final ColorSetting accent2;
    private final ColorSetting background;
    private final ColorSetting text;
    private final NumberSetting panelOpacity;
    private final NumberSetting animationSpeed;
    private final NumberSetting guiScale;
    private final ModeSetting font;
    private final BooleanSetting textShadow;
    private final BooleanSetting blur;
    private final List<Setting<?>> settings;

    private boolean applyingPreset;

    public ThemeManager() {
        String[] names = new String[PRESETS.size() + 1];
        for (int i = 0; i < PRESETS.size(); i++) {
            names[i] = PRESETS.get(i).name();
        }
        names[PRESETS.size()] = CUSTOM;
        Theme def = PRESETS.getFirst();

        preset = new ModeSetting("Theme", "Preset color scheme", def.name(), names);
        accent = new ColorSetting("Accent Color", "Main accent color", def.accent(), false);
        accent2 = new ColorSetting("Secondary Accent", "Second accent color used in gradients", def.accent2(), false);
        background = new ColorSetting("Background Color", "Window background color", def.background(), false);
        text = new ColorSetting("Text Color", "Primary text color", def.text(), false);
        panelOpacity = new NumberSetting("Panel Opacity", "How opaque the GUI panels are", 0.92, 0.3, 1.0, 0.01);
        animationSpeed = new NumberSetting("Animation Speed", "Speed multiplier for every animation", 1.0, 0.25, 3.0, 0.05).withSuffix("x");
        guiScale = new NumberSetting("GUI Scale", "Size of the ClickGUI relative to Minecraft's GUI scale", 1.0, 0.5, 2.0, 0.05).withSuffix("x");
        font = new ModeSetting("Font", "Typeface used by the ClickGUI, HUD and notifications", "Outfit", "Outfit", "Minecraft");
        textShadow = new BooleanSetting("Text Shadow", "Draw a drop shadow under text", true);
        blur = new BooleanSetting("Background Blur", "Blur the game behind the ClickGUI", true);

        preset.onChange(name -> {
            Theme theme = find(name);
            if (theme != null) {
                applyingPreset = true;
                accent.setRainbow(false);
                accent.setValue(theme.accent());
                accent2.setValue(theme.accent2());
                background.setValue(theme.background());
                text.setValue(theme.text());
                applyingPreset = false;
            }
        });
        for (ColorSetting color : List.of(accent, accent2, background, text)) {
            color.onChange(v -> markCustom());
        }
        animationSpeed.onChange(v -> Animation.setGlobalSpeed(v.floatValue()));
        font.onChange(v -> TextRenderer.setCustomFont(!"Minecraft".equals(v)));
        textShadow.onChange(TextRenderer::setShadows);

        settings = List.of(preset, accent, accent2, background, text, panelOpacity, animationSpeed, guiScale, font, textShadow, blur);
        syncStatics();
    }

    /** Pushes values that other systems cache statically; call after loading a configuration. */
    public void syncStatics() {
        Animation.setGlobalSpeed(animationSpeed.getFloat());
        TextRenderer.setCustomFont(!font.is("Minecraft"));
        TextRenderer.setShadows(textShadow.isEnabled());
    }

    private void markCustom() {
        if (applyingPreset || preset.is(CUSTOM)) {
            return;
        }
        Theme theme = find(preset.getValue());
        if (theme == null || accent.getValue() != theme.accent() || accent2.getValue() != theme.accent2()
                || background.getValue() != theme.background() || text.getValue() != theme.text() || accent.isRainbow()) {
            applyingPreset = true;
            preset.setValue(CUSTOM);
            applyingPreset = false;
        }
    }

    private static Theme find(String name) {
        for (Theme theme : PRESETS) {
            if (theme.name().equals(name)) {
                return theme;
            }
        }
        return null;
    }

    public void applyPreset(Theme theme) {
        preset.setValue(theme.name());
    }

    public String getPresetName() {
        return preset.getValue();
    }

    public List<Setting<?>> getSettings() {
        return settings;
    }

    // ---- Colors used by every renderer -------------------------------------------------------

    public int accent() {
        return accent.getColor();
    }

    public int accent(long offsetMillis) {
        return accent.getColor(offsetMillis);
    }

    public int accent2() {
        return accent2.getColor();
    }

    public int text() {
        return text.getValue();
    }

    public int textMuted() {
        return ColorUtil.withAlpha(ColorUtil.lerp(text.getValue(), background.getValue(), 0.42f), 255);
    }

    public int textDisabled() {
        return ColorUtil.withAlpha(ColorUtil.lerp(text.getValue(), background.getValue(), 0.62f), 255);
    }

    private int opacityAlpha() {
        return (int) Math.round(panelOpacity.get() * 255);
    }

    /** Main window background. */
    public int background() {
        return ColorUtil.withAlpha(background.getValue(), opacityAlpha());
    }

    /** Sidebar and header: slightly darker than the window. */
    public int backgroundDark() {
        return ColorUtil.withAlpha(ColorUtil.shade(background.getValue(), -0.25f), opacityAlpha());
    }

    /** Cards (module rows). */
    public int surface() {
        return ColorUtil.withAlpha(ColorUtil.shade(background.getValue(), 0.045f), Math.min(255, opacityAlpha() + 10));
    }

    public int surfaceHover() {
        return ColorUtil.withAlpha(ColorUtil.shade(background.getValue(), 0.085f), Math.min(255, opacityAlpha() + 10));
    }

    /** Inputs, slider tracks, switch tracks. */
    public int control() {
        return ColorUtil.withAlpha(ColorUtil.shade(background.getValue(), 0.13f), 255);
    }

    public int border() {
        return ColorUtil.withAlpha(ColorUtil.shade(background.getValue(), 0.16f), 255);
    }

    public float guiScale() {
        return guiScale.getFloat();
    }

    public boolean blurEnabled() {
        return blur.isEnabled();
    }
}
