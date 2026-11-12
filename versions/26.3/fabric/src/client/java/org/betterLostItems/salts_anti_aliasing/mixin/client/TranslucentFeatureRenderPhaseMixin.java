package org.betterLostItems.salts_anti_aliasing.mixin.client;

import net.minecraft.client.renderer.feature.phase.TranslucentFeatureRenderPhase;
import net.minecraft.client.renderer.feature.submit.TranslucentSubmit;

import org.betterLostItems.salts_anti_aliasing.client.platform.modern.ModernMinecraftHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TranslucentFeatureRenderPhase.class)
public class TranslucentFeatureRenderPhaseMixin {
    @Inject(
            method = "submit(Lnet/minecraft/client/renderer/feature/submit/TranslucentSubmit;)V",
            at = @At("HEAD"))
    private void saltsAntiAliasing$tag(TranslucentSubmit submit, CallbackInfo ci) {
        ModernMinecraftHooks.tagGeometrySubmission(submit);
    }
}
