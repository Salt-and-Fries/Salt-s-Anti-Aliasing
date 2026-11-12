package org.betterLostItems.salts_anti_aliasing.vulkanmod.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.vulkanmod.vulkan.Vulkan;
import net.vulkanmod.vulkan.DeviceManager;
import net.vulkanmod.vulkan.queue.Queue;
import org.betterLostItems.salts_anti_aliasing.vulkanmod.NativeVulkanSetup;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = DeviceManager.class, remap = false)
abstract class NativeDeviceMixin {
    @WrapOperation(method = "createLogicalDevice", at = @At(value = "INVOKE", target =
            "Lorg/lwjgl/vulkan/VK10;vkCreateDevice(Lorg/lwjgl/vulkan/VkPhysicalDevice;Lorg/lwjgl/vulkan/VkDeviceCreateInfo;Lorg/lwjgl/vulkan/VkAllocationCallbacks;Lorg/lwjgl/PointerBuffer;)I"))
    private static int salts$deviceRequirements(VkPhysicalDevice physical, VkDeviceCreateInfo info,
                                               VkAllocationCallbacks allocator, PointerBuffer result, Operation<Integer> original) {
        NativeVulkanSetup.device(MemoryStack.stackGet(), physical, info, Vulkan.getSurface(), Queue.findQueueFamilies(physical).graphicsFamily);
        return original.call(physical, info, allocator, result);
    }

    @Inject(method = "createLogicalDevice", at = @At("RETURN"))
    private static void salts$deviceReady(CallbackInfo info) {
        NativeVulkanSetup.ready(DeviceManager.device, DeviceManager.getGraphicsQueue().queue(), Queue.getQueueFamilies().graphicsFamily);
    }
    @Inject(method = "destroy", at = @At(value = "INVOKE", target =
            "Lorg/lwjgl/vulkan/VK10;vkDestroyDevice(Lorg/lwjgl/vulkan/VkDevice;Lorg/lwjgl/vulkan/VkAllocationCallbacks;)V"))
    private static void salts$closeAfterQueueOwners(CallbackInfo info) {
        org.betterLostItems.salts_anti_aliasing.vulkanmod.gpu.NativeResourceLifetimes.close();
    }

}
