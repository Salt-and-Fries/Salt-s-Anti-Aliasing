package org.betterLostItems.salts_anti_aliasing.vulkanmod.mixin;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.ParticleRenderType;
import org.betterLostItems.salts_anti_aliasing.vulkanmod.motion.ParticleMotionGeometry;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SingleQuadParticle.class)
abstract class SingleQuadParticleMixin {
    @Shadow public abstract float getQuadSize(float tick);
    @Shadow protected abstract float getU0();
    @Shadow protected abstract float getU1();
    @Shadow protected abstract float getV0();
    @Shadow protected abstract float getV1();

    @Inject(method = "render", at = @At("RETURN"))
    private void salts$captureVulkanModBillboard(VertexConsumer vertices, net.minecraft.client.Camera camera,
                                                 float tick, CallbackInfo info) {
        // VulkanMod's optimized renderer uses a private, unmapped quad routine. Preserve
        // precise captures from vanilla/custom routines, and cover its normal billboards here.
        if (!ParticleMotionGeometry.needsFallback(this)) return;
        var particle = (ParticleAccessor) this;
        var rotation = new Quaternionf(camera.rotation());
        rotation.rotateZ(net.minecraft.util.Mth.lerp(tick, particle.salts$previousRoll(), particle.salts$roll()));
        var position = camera.getPosition();
        salts$capture(vertices, rotation,
                (float) (net.minecraft.util.Mth.lerp(tick, particle.salts$xo(), particle.salts$x()) - position.x),
                (float) (net.minecraft.util.Mth.lerp(tick, particle.salts$yo(), particle.salts$y()) - position.y),
                (float) (net.minecraft.util.Mth.lerp(tick, particle.salts$zo(), particle.salts$z()) - position.z), tick, info);
    }

    private void salts$capture(VertexConsumer vertices, Quaternionf rotation, float x, float y, float z, float tick, CallbackInfo info) {
        var type = ((net.minecraft.client.particle.Particle) (Object) this).getRenderType();
        var layer = type == ParticleRenderType.TERRAIN_SHEET ? ParticleMotionGeometry.TERRAIN
                : type == ParticleRenderType.PARTICLE_SHEET_OPAQUE ? ParticleMotionGeometry.OPAQUE
                : type == ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT ? ParticleMotionGeometry.TRANSLUCENT : null;
        if (layer != null) ParticleMotionGeometry.capture(this, layer, x, y, z,
                rotation.x, rotation.y, rotation.z, rotation.w, getQuadSize(tick),
                getU0(), getU1(), getV0(), getV1(), Math.round(((ParticleAccessor) this).salts$alpha() * 255) << 24);
    }
}
