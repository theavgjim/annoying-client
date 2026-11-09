package com.annoyingclient.mixin;

import com.annoyingclient.AnnoyingClient;
import com.annoyingclient.module.impl.visual.CrosshairModule;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Custom Crosshair: swaps only the vanilla crosshair sprite. Vanilla still decides when a crosshair
 * is visible and still draws the attack indicator.
 */
@Mixin(Gui.class)
public abstract class GuiMixin {

    @WrapOperation(
            method = "renderCrosshair",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GuiGraphics;blitSprite(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIII)V",
                    ordinal = 0))
    private void annoyingclient$customCrosshair(GuiGraphics g, RenderPipeline pipeline, Identifier sprite, int x, int y, int w, int h,
                                                 Operation<Void> original) {
        CrosshairModule module = AnnoyingClient.getInstance() == null ? null : AnnoyingClient.modules().get(CrosshairModule.class);
        if (module != null && module.isEnabled()) {
            module.render(g);
        } else {
            original.call(g, pipeline, sprite, x, y, w, h);
        }
    }
}
