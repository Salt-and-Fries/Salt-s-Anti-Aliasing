package org.betterLostItems.salts_anti_aliasing.mixin.client;

import com.mojang.blaze3d.GpuFormat;
import org.betterLostItems.salts_anti_aliasing.client.render.vulkan.VulkanMsaaState;
import org.betterLostItems.salts_anti_aliasing.client.render.vulkan.VulkanSampledTexture;
import org.betterLostItems.salts_anti_aliasing.client.render.vulkan.VulkanStorageTextureUsage;
import com.mojang.blaze3d.vulkan.VulkanDevice;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Lets the Vulkan MSAA controller create multisampled scene textures through Minecraft's texture API.
 */
@Mixin(targets = "com.mojang.blaze3d.vulkan.VulkanGpuTexture")
public abstract class VulkanGpuTextureMixin implements VulkanSampledTexture {
    @Unique
    private int saltsAntiAliasing$sampleCount = 1;

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

    @Redirect(
            method = "<init>",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/vulkan/VulkanConst;textureUsageToVk(ILcom/mojang/blaze3d/GpuFormat;)I"
            )
    )
    private int saltsAntiAliasing$includeStorageTextureUsage(int usage, GpuFormat format) {
        return VulkanStorageTextureUsage.toVulkanUsage(usage, format);
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void saltsAntiAliasing$rememberSampleCount(
            VulkanDevice device,
            int usage,
            String label,
            GpuFormat format,
            int width,
            int height,
            int depthOrLayers,
            int mipLevels,
            CallbackInfo callbackInfo
    ) {
        saltsAntiAliasing$sampleCount = VulkanMsaaState.currentTextureSampleCount();
    }

    @Override
    public int saltsAntiAliasing$sampleCount() {
        return saltsAntiAliasing$sampleCount;
    }
}
