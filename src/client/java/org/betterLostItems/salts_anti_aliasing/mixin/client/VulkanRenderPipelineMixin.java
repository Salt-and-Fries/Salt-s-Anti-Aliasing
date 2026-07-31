package org.betterLostItems.salts_anti_aliasing.mixin.client;

import org.betterLostItems.salts_anti_aliasing.client.render.vulkan.VulkanMsaaState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Makes Vulkan render pipelines use the active MSAA sample count while world rendering is redirected.
 */
@Mixin(targets = "com.mojang.blaze3d.vulkan.VulkanRenderPipeline")
public abstract class VulkanRenderPipelineMixin {
    @ModifyArg(
            method = "compile",
            at = @At(
                    value = "INVOKE",
                    target = "Lorg/lwjgl/vulkan/VkPipelineMultisampleStateCreateInfo;rasterizationSamples(I)Lorg/lwjgl/vulkan/VkPipelineMultisampleStateCreateInfo;"
            ),
            index = 0
    )
    private static int saltsAntiAliasing$useScopedPipelineSampleCount(int originalSamples) {
        return VulkanMsaaState.pipelineSampleCount(originalSamples);
    }
}
