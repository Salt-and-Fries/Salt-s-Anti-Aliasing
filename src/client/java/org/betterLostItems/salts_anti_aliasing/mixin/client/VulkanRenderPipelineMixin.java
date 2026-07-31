package org.betterLostItems.salts_anti_aliasing.mixin.client;

import org.betterLostItems.salts_anti_aliasing.client.render.vulkan.VulkanMsaaState;
import org.lwjgl.vulkan.VkPipelineMultisampleStateCreateInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;

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

    @Redirect(
            method = "compile",
            at = @At(
                    value = "INVOKE",
                    target = "Lorg/lwjgl/vulkan/VkPipelineMultisampleStateCreateInfo;sampleShadingEnable(Z)Lorg/lwjgl/vulkan/VkPipelineMultisampleStateCreateInfo;"
            )
    )
    private static VkPipelineMultisampleStateCreateInfo saltsAntiAliasing$configureAlphaToCoverage(
            VkPipelineMultisampleStateCreateInfo multisampleState,
            boolean sampleShadingEnabled
    ) {
        return multisampleState
                .sampleShadingEnable(sampleShadingEnabled)
                .alphaToCoverageEnable(VulkanMsaaState.pipelineAlphaToCoverageEnabled());
    }
}
