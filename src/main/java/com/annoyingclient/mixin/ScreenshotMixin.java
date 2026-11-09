package com.annoyingclient.mixin;

import com.annoyingclient.AnnoyingClient;
import com.annoyingclient.module.impl.visual.ScreenshotModule;
import net.minecraft.client.Screenshot;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.function.Consumer;

/** Screenshot Manager: wraps the result callback so a notification can be shown after the file is written. */
@Mixin(Screenshot.class)
public abstract class ScreenshotMixin {

    @ModifyVariable(
            method = "grab(Ljava/io/File;Ljava/lang/String;Lcom/mojang/blaze3d/pipeline/RenderTarget;ILjava/util/function/Consumer;)V",
            at = @At("HEAD"), argsOnly = true)
    private static Consumer<Component> annoyingclient$wrapCallback(Consumer<Component> original) {
        return message -> {
            original.accept(message);
            if (AnnoyingClient.getInstance() == null) {
                return;
            }
            ScreenshotModule module = AnnoyingClient.modules().get(ScreenshotModule.class);
            if (module != null) {
                boolean success = message.getContents() instanceof TranslatableContents tc && tc.getKey().equals("screenshot.success");
                String detail = success && message.getContents() instanceof TranslatableContents tc && tc.getArgs().length > 0
                        && tc.getArgs()[0] instanceof Component file ? file.getString() : message.getString();
                module.onScreenshotResult(success, detail);
            }
        };
    }
}
