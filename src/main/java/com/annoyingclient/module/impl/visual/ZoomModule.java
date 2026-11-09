package com.annoyingclient.module.impl.visual;

import com.annoyingclient.AnnoyingClient;
import com.annoyingclient.event.KeyInputEvent;
import com.annoyingclient.event.MouseInputEvent;
import com.annoyingclient.event.MouseScrollEvent;
import com.annoyingclient.module.Category;
import com.annoyingclient.module.Module;
import com.annoyingclient.setting.BooleanSetting;
import com.annoyingclient.setting.KeybindSetting;
import com.annoyingclient.setting.ModeSetting;
import com.annoyingclient.setting.NumberSetting;
import com.annoyingclient.util.Animation;
import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;

/**
 * Optifine-style zoom. While the zoom key is held (or toggled), the field of view is divided by the
 * zoom factor. The scroll wheel can adjust the factor, and mouse sensitivity scales down with it.
 */
public class ZoomModule extends Module {

    private final KeybindSetting zoomKey = add(new KeybindSetting("Zoom Key", "Hold (or press) to zoom", GLFW.GLFW_KEY_C));
    private final ModeSetting activation = add(new ModeSetting("Activation", "Hold the key, or press it to toggle", "Hold", "Hold", "Toggle"));
    private final NumberSetting factor = add(new NumberSetting("Zoom Factor", "How far to zoom in", 4.0, 1.5, 30.0, 0.5).withSuffix("x"));
    private final BooleanSetting smooth = add(new BooleanSetting("Smooth Zoom", "Animate zooming in and out", true));
    private final BooleanSetting scroll = add(new BooleanSetting("Scroll to Adjust", "Use the mouse wheel to change the zoom while zooming", true));
    private final BooleanSetting reduceSensitivity = add(new BooleanSetting("Reduce Sensitivity", "Lower mouse sensitivity while zoomed", true));
    private final BooleanSetting cinematic = add(new BooleanSetting("Cinematic Camera", "Use smooth camera movement while zoomed", false));

    private final Animation current = new Animation(1f, 14f);
    private double scrollFactor = -1;
    private boolean toggled;
    private boolean wasZooming;
    private boolean savedSmoothCamera;

    public ZoomModule() {
        super("Zoom", "Zoom in with a key (default C)", Category.VISUAL, true, KeybindSetting.NONE);
        var bus = AnnoyingClient.events();
        bus.subscribe(KeyInputEvent.class, this::onKey);
        bus.subscribe(MouseInputEvent.class, this::onMouse);
        bus.subscribe(MouseScrollEvent.class, this::onScroll);
    }

    private void onKey(KeyInputEvent e) {
        if (isEnabled() && activation.is("Toggle") && e.isPress() && mc.screen == null && zoomKey.matchesKey(e.getKey())) {
            toggled = !toggled;
        }
    }

    private void onMouse(MouseInputEvent e) {
        if (isEnabled() && activation.is("Toggle") && e.isPress() && e.isInGame() && zoomKey.matchesMouse(e.getButton())) {
            toggled = !toggled;
        }
    }

    private void onScroll(MouseScrollEvent e) {
        if (!isZooming() || !scroll.isEnabled() || e.getVertical() == 0) {
            return;
        }
        double base = scrollFactor > 0 ? scrollFactor : factor.get();
        scrollFactor = Math.max(1.1, Math.min(50.0, base * Math.pow(1.15, Math.signum(e.getVertical()))));
        e.cancel();
    }

    public boolean isZooming() {
        if (!isEnabled() || mc.screen != null || mc.player == null || !zoomKey.isBound()) {
            return false;
        }
        if (activation.is("Toggle")) {
            return toggled;
        }
        int key = zoomKey.getKey();
        long window = mc.getWindow().handle();
        if (KeybindSetting.isMouse(key)) {
            return GLFW.glfwGetMouseButton(window, KeybindSetting.mouseButton(key)) == GLFW.GLFW_PRESS;
        }
        return InputConstants.isKeyDown(mc.getWindow(), key);
    }

    /** Current (animated) zoom multiplier; 1 when not zooming. Called every frame by the FOV mixin. */
    public float getFovDivisor() {
        boolean zooming = isZooming();
        if (zooming != wasZooming) {
            wasZooming = zooming;
            if (!zooming) {
                scrollFactor = -1;
            }
            if (cinematic.isEnabled()) {
                if (zooming) {
                    savedSmoothCamera = mc.options.smoothCamera;
                    mc.options.smoothCamera = true;
                } else {
                    mc.options.smoothCamera = savedSmoothCamera;
                }
            }
        }
        float target = zooming ? (float) (scrollFactor > 0 ? scrollFactor : factor.get()) : 1f;
        if (!smooth.isEnabled()) {
            current.snap(target);
            return target;
        }
        current.setTarget(target);
        return current.update();
    }

    /** Multiplier for mouse movement, used by the mouse mixin. */
    public double getSensitivityMultiplier() {
        if (!isEnabled() || !reduceSensitivity.isEnabled()) {
            return 1.0;
        }
        float divisor = current.get();
        return divisor <= 1.001f ? 1.0 : 1.0 / divisor;
    }

    @Override
    protected void onDisable() {
        toggled = false;
        if (wasZooming && cinematic.isEnabled()) {
            mc.options.smoothCamera = savedSmoothCamera;
        }
        wasZooming = false;
        current.snap(1f);
    }

    private double infoFor = Double.NaN;
    private String info = "";

    @Override
    public String getDisplayInfo() {
        if (factor.get() != infoFor) {
            infoFor = factor.get();
            info = String.format(Locale.ROOT, "%.1fx", infoFor);
        }
        return info;
    }
}
