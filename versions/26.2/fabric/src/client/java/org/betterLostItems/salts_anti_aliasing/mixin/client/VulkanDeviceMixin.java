package org.betterLostItems.salts_anti_aliasing.mixin.client;

import com.mojang.blaze3d.shaders.ShaderSource;
import java.util.Set;
import com.mojang.blaze3d.vulkan.VulkanDevice;
import com.mojang.blaze3d.vulkan.VulkanInstance;
import com.mojang.blaze3d.vulkan.VulkanPhysicalDevice;
import com.mojang.blaze3d.vulkan.checkpoints.CheckpointExtension;
import org.betterLostItems.salts_anti_aliasing.client.render.vulkan.dlss.DlssVulkanDeviceRegistry;
import org.betterLostItems.salts_anti_aliasing.client.render.vulkan.VulkanNativeDeviceRegistry;
import org.lwjgl.vulkan.VkDevice;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;


/**
 * Captures the physical-device handle Minecraft does not keep on VulkanDevice itself.
 */
@Mixin(targets = "com.mojang.blaze3d.vulkan.VulkanDevice")
public abstract class VulkanDeviceMixin {
    @Inject(method = "close", at = @At("HEAD"))
    private void saltsAntiAliasing$releasePassBuffers(CallbackInfo callbackInfo) {
        org.betterLostItems.salts_anti_aliasing.client.render.vulkan.VulkanPassResources.release((VulkanDevice)(Object)this);
    }
    @Inject(method = "<init>", at = @At("RETURN"))
    private void saltsAntiAliasing$captureDlssVulkanHandles(
            ShaderSource shaderSource,
            VulkanInstance instance,
            VulkanPhysicalDevice physicalDevice,
            Set<String> enabledExtensions,
            VkDevice vkDevice,
            long vma,
            CheckpointExtension checkpointExtension,
            CallbackInfo callbackInfo
    ) {
        VulkanDevice device = (VulkanDevice) (Object) this;
        DlssVulkanDeviceRegistry.capture(device, physicalDevice);
        VulkanNativeDeviceRegistry.capture(device, physicalDevice);
    }
}
