package org.betterLostItems.salts_anti_aliasing.vulkanmod.mixin;

import java.util.List;
import net.vulkanmod.vulkan.shader.Pipeline;
import net.vulkanmod.vulkan.texture.VTextureSelector;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A descriptor cache change must not skip transitions for later samplers. */
@Mixin(value = Pipeline.class, remap = false)
abstract class TextureSamplerLayoutMixin {
    @Shadow @Final private List<Pipeline.Sampler> samplers;

    @Inject(method = {
            "bindDescriptorSets(Lorg/lwjgl/vulkan/VkCommandBuffer;I)V",
            "bindDescriptorSets(Lorg/lwjgl/vulkan/VkCommandBuffer;Lnet/vulkanmod/vulkan/memory/UniformBuffers;I)V"
    }, at = @At("HEAD"))
    private void salts$prepareEverySampler(CallbackInfo info) {
        // VulkanMod 0.3.1 stops its sampler loop at the first changed texture.
        // Light maps and overlays can therefore remain in TRANSFER_DST after upload.
        for (var sampler : samplers) VTextureSelector.getTexture(sampler.name()).readOnlyLayout();
    }
}
