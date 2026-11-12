package org.betterLostItems.salts_anti_aliasing.vulkanmod.mixin;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.vulkanmod.vulkan.shader.layout.Vec1f;
import org.betterLostItems.salts_anti_aliasing.vulkanmod.SaltsVulkanModClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
@Mixin(value = Vec1f.class, remap = false)
abstract class TerrainMipMixin {
    @ModifyExpressionValue(method = "lambda$setSupplier$0", at = @At(value = "FIELD", target = "Lnet/vulkanmod/vulkan/VRenderSystem;alphaCutout:F"))
    private static float salts$bias(float alpha) { return alpha + SaltsVulkanModClient.mipBiasQuarterSteps() * 2f; }
}
