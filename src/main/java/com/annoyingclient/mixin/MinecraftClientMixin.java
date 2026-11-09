package com.annoyingclient.mixin;

import com.annoyingclient.AnnoyingClient;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Brands the game window title, e.g. "Annoying Client 0.1.0 | Minecraft 1.21.11 - Singleplayer".
 */
@Mixin(Minecraft.class)
public abstract class MinecraftClientMixin {

    @Inject(method = "createTitle", at = @At("RETURN"), cancellable = true)
    private void annoyingclient$brandTitle(CallbackInfoReturnable<String> cir) {
        AnnoyingClient client = AnnoyingClient.getInstance();
        String version = client != null ? client.getVersion() : "dev";
        cir.setReturnValue(AnnoyingClient.NAME + " " + version + " | " + cir.getReturnValue());
    }
}
