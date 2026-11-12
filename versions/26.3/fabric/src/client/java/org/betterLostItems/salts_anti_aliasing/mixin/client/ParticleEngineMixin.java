package org.betterLostItems.salts_anti_aliasing.mixin.client;

import net.minecraft.client.particle.ParticleEngine;
import org.betterLostItems.salts_anti_aliasing.client.render.vulkan.ParticleMotionGeometry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ParticleEngine.class)
public class ParticleEngineMixin {
    @Inject(method = "extract", at = @At("HEAD"))
    private void saltsAntiAliasing$beginParticleFrame(CallbackInfo ci) {
        ParticleMotionGeometry.beginExtraction();
    }

    @Inject(method = "clearParticles", at = @At("HEAD"))
    private void saltsAntiAliasing$clearParticleHistory(CallbackInfo ci) {
        ParticleMotionGeometry.clear();
    }
}
