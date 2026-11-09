package com.annoyingclient.mixin;

import com.annoyingclient.AnnoyingClient;
import com.annoyingclient.module.impl.visual.FullbrightModule;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.renderer.LightTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Slice;

/**
 * Fullbright: replaces the gamma value read while building the lightmap. The player's saved
 * Brightness option itself is never changed.
 */
@Mixin(LightTexture.class)
public abstract class LightTextureMixin {

    @ModifyExpressionValue(
            method = "updateLightTexture",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/OptionInstance;get()Ljava/lang/Object;", ordinal = 0),
            slice = @Slice(from = @At(value = "INVOKE", target = "Lnet/minecraft/client/Options;gamma()Lnet/minecraft/client/OptionInstance;")))
    private Object annoyingclient$fullbright(Object original) {
        if (AnnoyingClient.getInstance() == null || !(original instanceof Double gamma)) {
            return original;
        }
        FullbrightModule module = AnnoyingClient.modules().get(FullbrightModule.class);
        return module == null ? original : (Object) module.adjustGamma(gamma);
    }
}
