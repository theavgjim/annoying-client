package com.annoyingclient.hud;

import com.annoyingclient.AnnoyingClient;
import com.annoyingclient.manager.ModuleManager;
import com.annoyingclient.module.Module;
import com.annoyingclient.notification.NotificationManager;
import com.annoyingclient.theme.ThemeManager;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Draws every enabled {@link HudModule} and in-game notifications as one layer registered with
 * Fabric's HUD element registry (on top of the vanilla HUD).
 */
public final class HudManager {

    public static final Identifier LAYER_ID = Identifier.fromNamespaceAndPath(AnnoyingClient.MOD_ID, "hud");

    private final List<HudModule> elements = new ArrayList<>();
    private final NotificationManager notifications;
    private final ThemeManager theme;

    public HudManager(ModuleManager modules, NotificationManager notifications, ThemeManager theme) {
        this.notifications = notifications;
        this.theme = theme;
        for (Module module : modules.getModules()) {
            if (module instanceof HudModule hud) {
                elements.add(hud);
            }
        }
    }

    public void registerLayer() {
        HudElementRegistry.addLast(LAYER_ID, this::render);
    }

    public List<HudModule> getElements() {
        return Collections.unmodifiableList(elements);
    }

    private void render(GuiGraphics g, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) {
            return;
        }
        // The HUD editor draws the elements itself (with outlines and drag handles).
        if (!(mc.screen instanceof HudEditorScreen)) {
            float partialTick = deltaTracker.getGameTimeDeltaPartialTick(false);
            for (int i = 0; i < elements.size(); i++) {
                HudModule element = elements.get(i);
                if (element.isEnabled()) {
                    element.render(g, partialTick, false);
                }
            }
        }
        // While a screen is open, notifications are drawn on top of it by the screen render hook instead.
        if (mc.screen == null) {
            notifications.render(g, theme);
        }
    }

    public void resetAllPositions() {
        for (HudModule element : elements) {
            element.resetPosition();
        }
    }
}
