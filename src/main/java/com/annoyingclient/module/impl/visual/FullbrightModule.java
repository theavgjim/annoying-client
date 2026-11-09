package com.annoyingclient.module.impl.visual;

import com.annoyingclient.module.Category;
import com.annoyingclient.module.Module;
import com.annoyingclient.setting.KeybindSetting;
import com.annoyingclient.setting.NumberSetting;
import com.annoyingclient.util.Animation;
import com.annoyingclient.util.Easing;

/**
 * Raises the brightness used when building the lightmap. The player's saved Brightness option is
 * never modified: the override exists only while the module is enabled, and fades in and out.
 */
public class FullbrightModule extends Module {

    private final NumberSetting strength = add(new NumberSetting("Strength", "Brightness multiplier (vanilla maximum is 1.0)", 10.0, 1.0, 16.0, 0.5).withSuffix("x"));
    private final Animation fade = Animation.timed(0f, 350, Easing.EASE_IN_OUT_CUBIC);

    public FullbrightModule() {
        super("Fullbright", "See clearly in the dark without changing your brightness setting", Category.VISUAL, false, KeybindSetting.NONE);
    }

    @Override
    protected void onEnable() {
        fade.setTarget(1f);
    }

    @Override
    protected void onDisable() {
        fade.setTarget(0f);
    }

    /**
     * Returns the gamma to use instead of {@code vanillaGamma}. Called by the lightmap mixin each time
     * the lightmap is rebuilt (once per tick).
     */
    public double adjustGamma(double vanillaGamma) {
        float f = fade.update();
        if (f <= 0f) {
            return vanillaGamma;
        }
        return vanillaGamma + (strength.get() - vanillaGamma) * f;
    }

    @Override
    public String getDisplayInfo() {
        return strength.getDisplayValue();
    }

    @Override
    public void resetToDefaults() {
        super.resetToDefaults();
        fade.snap(isEnabled() ? 1f : 0f);
    }
}
