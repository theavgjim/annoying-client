package com.annoyingclient.mixin;

import com.annoyingclient.AnnoyingClient;
import com.annoyingclient.module.impl.render.NoHurtCamModule;
import com.annoyingclient.module.impl.visual.ZoomModule;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Zoom (field of view) and No Hurt Cam. */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {

    @Shadow
    @Final
    private Minecraft minecraft;

    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void annoyingclient$zoom(Camera camera, float partialTick, boolean useFovSetting, CallbackInfoReturnable<Float> cir) {
        // Only the world FOV zooms; the held-item FOV (useFovSetting == false) is left alone.
        if (!useFovSetting || AnnoyingClient.getInstance() == null) {
            return;
        }
        ZoomModule zoom = AnnoyingClient.modules().get(ZoomModule.class);
        if (zoom != null) {
            float divisor = zoom.getFovDivisor();
            if (divisor != 1f) {
                cir.setReturnValue(cir.getReturnValue() / divisor);
            }
        }
    }

    @Inject(method = "bobHurt", at = @At("HEAD"), cancellable = true)
    private void annoyingclient$noHurtCam(PoseStack poseStack, float partialTick, CallbackInfo ci) {
        if (AnnoyingClient.getInstance() == null) {
            return;
        }
        NoHurtCamModule module = AnnoyingClient.modules().get(NoHurtCamModule.class);
        if (module != null && module.isEnabled()
                && !(minecraft.getCameraEntity() instanceof LivingEntity living && living.isDeadOrDying())) {
            ci.cancel();
        }
    }
}
