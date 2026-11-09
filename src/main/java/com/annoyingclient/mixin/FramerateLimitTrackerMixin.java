package com.annoyingclient.mixin;

import com.annoyingclient.AnnoyingClient;
import com.annoyingclient.module.impl.misc.BackgroundFpsModule;
import com.mojang.blaze3d.platform.FramerateLimitTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Background FPS: lowers the frame-rate cap while the window is unfocused. */
@Mixin(FramerateLimitTracker.class)
public abstract class FramerateLimitTrackerMixin {

    @Inject(method = "getFramerateLimit", at = @At("RETURN"), cancellable = true)
    private void annoyingclient$backgroundFps(CallbackInfoReturnable<Integer> cir) {
        if (AnnoyingClient.getInstance() == null) {
            return;
        }
        BackgroundFpsModule module = AnnoyingClient.modules().get(BackgroundFpsModule.class);
        if (module != null) {
            int adjusted = module.adjust(cir.getReturnValue());
            if (adjusted != cir.getReturnValueI()) {
                cir.setReturnValue(adjusted);
            }
        }
    }
}
