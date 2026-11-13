package com.annoyingclient.mixin;

import com.annoyingclient.modconfig.LiveReload;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mod Configs: remembers every YetAnotherConfigLib config as it is created, so a file saved in the Mod
 * Configs tab can be reloaded into the mod without a restart. Does nothing when YACL isn't installed.
 */
@Pseudo
@Mixin(targets = "dev.isxander.yacl3.config.v2.impl.ConfigClassHandlerImpl", remap = false)
public abstract class YaclConfigHandlerMixin {

    @Inject(method = "<init>", at = @At("RETURN"), require = 0)
    private void annoyingclient$track(CallbackInfo ci) {
        LiveReload.registerYaclHandler(this);
    }
}
