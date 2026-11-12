package org.betterLostItems.salts_anti_aliasing.vulkanmod.mixin;

import net.vulkanmod.vulkan.shader.Uniforms;
import org.betterLostItems.salts_anti_aliasing.vulkanmod.SaltsVulkanModClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Uniforms.class, remap = false)
abstract class TerrainMipMixin {
    @Inject(method = "setupDefaultUniforms", at = @At("RETURN"))
    private static void salts$terrainBias(CallbackInfo info) {
        var original = Uniforms.vec1f_uniformMap.get("AlphaCutout");
        if (original == null) throw new IllegalStateException("Terrain alpha-cutout uniform supplier is missing");
        Uniforms.vec1f_uniformMap.put("AlphaCutout", () -> original.get() + SaltsVulkanModClient.mipBiasQuarterSteps() * 2.0f);
    }
}
