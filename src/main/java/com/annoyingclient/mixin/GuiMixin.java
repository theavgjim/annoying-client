package com.annoyingclient.mixin;

import com.annoyingclient.AnnoyingClient;
import com.annoyingclient.module.impl.visual.CrosshairModule;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.annoyingclient.hud.element.PotionEffectsHud;
import com.annoyingclient.module.impl.visual.SaturationModule;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Custom Crosshair: swaps only the vanilla crosshair sprite. Vanilla still decides when a crosshair
 * is visible and still draws the attack indicator. Saturation draws on the hunger bar, and the Potion
 * Effects HUD can hide the vanilla effect icons.
 */
@Mixin(Gui.class)
public abstract class GuiMixin {

    @Shadow
    public abstract int getGuiTicks();

    @Inject(method = "renderFood", at = @At("TAIL"))
    private void annoyingclient$saturation(GuiGraphics g, Player player, int top, int right, CallbackInfo ci) {
        SaturationModule module = AnnoyingClient.getInstance() == null ? null : AnnoyingClient.modules().get(SaturationModule.class);
        if (module != null && module.isEnabled()) {
            module.renderOnFoodBar(g, player, top, right, getGuiTicks());
        }
    }

    @Inject(method = "renderEffects", at = @At("HEAD"), cancellable = true)
    private void annoyingclient$hideVanillaEffects(GuiGraphics g, DeltaTracker deltaTracker, CallbackInfo ci) {
        PotionEffectsHud hud = AnnoyingClient.getInstance() == null ? null : AnnoyingClient.modules().get(PotionEffectsHud.class);
        if (hud != null && hud.hidesVanillaIcons()) {
            ci.cancel();
        }
    }

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
