package com.annoyingclient.mixin;

import com.annoyingclient.AnnoyingClient;
import com.annoyingclient.module.impl.render.LowFireModule;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Low Fire: moves the first-person fire overlay down while it is drawn, then restores the pose. */
@Mixin(ScreenEffectRenderer.class)
public abstract class ScreenEffectRendererMixin {

    private static float annoyingclient$fireOffset;

    @Inject(method = "renderFire", at = @At("HEAD"))
    private static void annoyingclient$lowerFire(PoseStack poseStack, MultiBufferSource buffers, TextureAtlasSprite sprite, CallbackInfo ci) {
        annoyingclient$fireOffset = 0;
        if (AnnoyingClient.getInstance() == null) {
            return;
        }
        LowFireModule module = AnnoyingClient.modules().get(LowFireModule.class);
        if (module != null && module.isEnabled()) {
            annoyingclient$fireOffset = module.getOffset();
            poseStack.translate(0, -annoyingclient$fireOffset, 0);
        }
    }

    @Inject(method = "renderFire", at = @At("RETURN"))
    private static void annoyingclient$restoreFire(PoseStack poseStack, MultiBufferSource buffers, TextureAtlasSprite sprite, CallbackInfo ci) {
        if (annoyingclient$fireOffset != 0) {
            poseStack.translate(0, annoyingclient$fireOffset, 0);
            annoyingclient$fireOffset = 0;
        }
    }
}
