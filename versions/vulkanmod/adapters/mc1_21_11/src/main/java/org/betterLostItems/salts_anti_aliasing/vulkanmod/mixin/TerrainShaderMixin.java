package org.betterLostItems.salts_anti_aliasing.vulkanmod.mixin;

import net.vulkanmod.vulkan.shader.SPIRVUtils;
import org.betterLostItems.salts_anti_aliasing.vulkanmod.gpu.TerrainTextureDetail;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(value = SPIRVUtils.class, remap = false)
abstract class TerrainShaderMixin {
    @ModifyVariable(method = "compileShader", at = @At("HEAD"), argsOnly = true, ordinal = 1)
    private static String salts$terrainSource(String source) { return TerrainTextureDetail.patch(source); }
}
