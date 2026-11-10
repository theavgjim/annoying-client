package com.annoyingclient.mixin;

import com.annoyingclient.AnnoyingClient;
import com.annoyingclient.cosmetics.CosmeticsManager;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.ClientAsset;
import net.minecraft.world.entity.player.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Cosmetics: replaces the cape (and elytra texture) of players who equipped an Annoying Client cape. */
@Mixin(AbstractClientPlayer.class)
public abstract class AbstractClientPlayerMixin {

    @Unique
    private PlayerSkin annoyingclient$base;
    @Unique
    private String annoyingclient$capeId;
    @Unique
    private PlayerSkin annoyingclient$result;

    @Inject(method = "getSkin", at = @At("RETURN"), cancellable = true)
    private void annoyingclient$cape(CallbackInfoReturnable<PlayerSkin> cir) {
        if (AnnoyingClient.getInstance() == null || AnnoyingClient.cosmetics() == null) {
            return;
        }
        String cape = AnnoyingClient.cosmetics().capeFor(((AbstractClientPlayer) (Object) this).getUUID());
        if (cape == null) {
            return;
        }
        PlayerSkin base = cir.getReturnValue();
        // Cache: getSkin is called every frame for every visible player.
        if (base != annoyingclient$base || !cape.equals(annoyingclient$capeId)) {
            ClientAsset.ResourceTexture texture = CosmeticsManager.capeTexture(cape);
            annoyingclient$base = base;
            annoyingclient$capeId = cape;
            annoyingclient$result = texture == null ? base : new PlayerSkin(base.body(), texture, texture, base.model(), base.secure());
        }
        cir.setReturnValue(annoyingclient$result);
    }
}
