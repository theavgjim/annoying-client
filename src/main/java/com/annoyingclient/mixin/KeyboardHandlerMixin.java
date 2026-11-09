package com.annoyingclient.mixin;

import com.annoyingclient.AnnoyingClient;
import com.annoyingclient.event.Events;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Forwards raw key events to the client event bus (keybinds, zoom). */
@Mixin(KeyboardHandler.class)
public abstract class KeyboardHandlerMixin {

    @Shadow
    @Final
    private Minecraft minecraft;

    @Inject(method = "keyPress", at = @At("HEAD"))
    private void annoyingclient$onKey(long window, int action, KeyEvent event, CallbackInfo ci) {
        if (window != minecraft.getWindow().handle() || minecraft.getOverlay() != null || AnnoyingClient.getInstance() == null) {
            return;
        }
        AnnoyingClient.events().post(Events.KEY.set(event.key(), event.scancode(), action, event.modifiers()));
    }
}
