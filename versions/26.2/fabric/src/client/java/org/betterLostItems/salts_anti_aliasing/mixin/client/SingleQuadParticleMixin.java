package org.betterLostItems.salts_anti_aliasing.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import org.betterLostItems.salts_anti_aliasing.client.render.vulkan.ParticleMotionGeometry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(SingleQuadParticle.class)
public class SingleQuadParticleMixin {
    @WrapOperation(method = "extractRotatedQuad(Lnet/minecraft/client/renderer/state/level/QuadParticleRenderState;Lorg/joml/Quaternionf;FFFF)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/state/level/QuadParticleRenderState;add(Lnet/minecraft/client/particle/SingleQuadParticle$Layer;FFFFFFFFFFFFII)V"))
    private void saltsAntiAliasing$captureParticle(QuadParticleRenderState state, SingleQuadParticle.Layer layer,
            float x, float y, float z, float qx, float qy, float qz, float qw, float size,
            float u0, float u1, float v0, float v1, int color, int light, Operation<Void> original) {
        original.call(state, layer, x, y, z, qx, qy, qz, qw, size, u0, u1, v0, v1, color, light);
        ParticleMotionGeometry.capture(this, layer, x, y, z, qx, qy, qz, qw, size, u0, u1, v0, v1, color);
    }
}
