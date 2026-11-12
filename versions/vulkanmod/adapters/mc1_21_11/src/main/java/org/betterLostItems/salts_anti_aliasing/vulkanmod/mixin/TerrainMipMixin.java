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
        var original = Uniforms.vec1i_uniformMap.get("UseRgss");
        if (original == null) throw new IllegalStateException("Terrain RGSS uniform supplier is missing");
        Uniforms.vec1i_uniformMap.put("UseRgss", () -> (original.get() & 1) | (SaltsVulkanModClient.mipBiasQuarterSteps() << 8));
    }
}
