package org.betterLostItems.salts_anti_aliasing.mixin.client;

import org.betterLostItems.salts_anti_aliasing.client.render.vulkan.VulkanMsaaState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Lets the Vulkan MSAA controller create multisampled scene textures through Minecraft's texture API.
 */
@Mixin(targets = "com.mojang.blaze3d.vulkan.VulkanGpuTexture")
public abstract class VulkanGpuTextureMixin {
    @ModifyArg(
            method = "<init>",
            at = @At(
                    value = "INVOKE",
                    target = "Lorg/lwjgl/vulkan/VkImageCreateInfo;samples(I)Lorg/lwjgl/vulkan/VkImageCreateInfo;"
            ),
            index = 0
    )
    private int saltsAntiAliasing$useScopedSampleCount(int originalSamples) {
        return VulkanMsaaState.textureSampleCount(originalSamples);
    }
}
