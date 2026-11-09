package com.annoyingclient.mixin;

import com.annoyingclient.AnnoyingClient;
import com.annoyingclient.event.Events;
import com.annoyingclient.module.impl.visual.ZoomModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Mouse buttons (CPS, mouse keybinds), scroll (zoom adjust) and zoom sensitivity. */
@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {

    @Shadow
    @Final
    private Minecraft minecraft;

    @Shadow
    private double accumulatedDX;

    @Shadow
    private double accumulatedDY;

    @Inject(method = "onButton", at = @At("HEAD"))
    private void annoyingclient$onButton(long window, MouseButtonInfo info, int action, CallbackInfo ci) {
        if (window != minecraft.getWindow().handle() || AnnoyingClient.getInstance() == null) {
            return;
        }
        boolean inGame = minecraft.screen == null && minecraft.getOverlay() == null;
        if (inGame && action == 1 && info.button() <= 1) {
            AnnoyingClient.cps().onClick(info.button() == 0);
        }
        AnnoyingClient.events().post(Events.MOUSE.set(info.button(), action, inGame));
    }

    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void annoyingclient$onScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
        if (window != minecraft.getWindow().handle() || minecraft.screen != null || minecraft.getOverlay() != null
                || AnnoyingClient.getInstance() == null) {
            return;
        }
        if (AnnoyingClient.events().post(Events.SCROLL.set(horizontal, vertical)).isCancelled()) {
            ci.cancel();
        }
    }

    @Inject(method = "turnPlayer", at = @At("HEAD"))
    private void annoyingclient$zoomSensitivity(double movementTime, CallbackInfo ci) {
        if (AnnoyingClient.getInstance() == null) {
            return;
        }
        ZoomModule zoom = AnnoyingClient.modules().get(ZoomModule.class);
        if (zoom != null) {
            double m = zoom.getSensitivityMultiplier();
            if (m != 1.0) {
                accumulatedDX *= m;
                accumulatedDY *= m;
            }
        }
    }
}
