package org.betterLostItems.salts_anti_aliasing.validation.mixin;
import org.betterLostItems.salts_anti_aliasing.validation.TerrainProjectionChecks;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
@Mixin(targets = "net.vulkanmod.render.chunk.WorldRenderer", remap = false)
abstract class TerrainProjectionMixin {
    @ModifyArg(method = "*", at = @At(value = "INVOKE", target =
            "Lnet/vulkanmod/vulkan/VRenderSystem;applyMVP(Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;)V"), index = 1)
    private Matrix4f salts$verifyTerrainProjection(Matrix4f projection) {
        TerrainProjectionChecks.observe(projection);
        return projection;
    }
}
