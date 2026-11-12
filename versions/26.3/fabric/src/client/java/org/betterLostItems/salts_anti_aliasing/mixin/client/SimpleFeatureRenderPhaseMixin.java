package org.betterLostItems.salts_anti_aliasing.mixin.client;

import net.minecraft.client.renderer.feature.phase.SimpleFeatureRenderPhase;
import net.minecraft.client.renderer.feature.submit.SubmitNode;

import org.betterLostItems.salts_anti_aliasing.client.platform.modern.ModernMinecraftHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SimpleFeatureRenderPhase.class)
public class SimpleFeatureRenderPhaseMixin {
    @Inject(method = "submit", at = @At("HEAD"))
    private void saltsAntiAliasing$tag(SubmitNode submit, CallbackInfo ci) {
        ModernMinecraftHooks.tagGeometrySubmission(submit);
    }
}
