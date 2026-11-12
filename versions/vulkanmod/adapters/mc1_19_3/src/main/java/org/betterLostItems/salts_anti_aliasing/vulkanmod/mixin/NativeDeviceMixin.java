package org.betterLostItems.salts_anti_aliasing.vulkanmod.mixin;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import net.vulkanmod.vulkan.Vulkan;

import org.betterLostItems.salts_anti_aliasing.vulkanmod.NativeVulkanSetup;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(value = Vulkan.class, remap = false)
abstract class NativeDeviceMixin {
    @WrapOperation(method = "createLogicalDevice", at = @At(value = "INVOKE", target = "Lorg/lwjgl/vulkan/VK10;vkCreateDevice(Lorg/lwjgl/vulkan/VkPhysicalDevice;Lorg/lwjgl/vulkan/VkDeviceCreateInfo;Lorg/lwjgl/vulkan/VkAllocationCallbacks;Lorg/lwjgl/PointerBuffer;)I"))
    private static int salts$requirements(VkPhysicalDevice physical, VkDeviceCreateInfo info, VkAllocationCallbacks allocator, PointerBuffer result, Operation<Integer> original) {
        NativeVulkanSetup.device(MemoryStack.stackGet(), physical, info, VulkanWindowAccessor.salts$surface(), ((QueueFamiliesAccessor)(Object)Vulkan.findQueueFamilies(physical)).salts$graphics());
        return original.call(physical, info, allocator, result);
    }
    @Inject(method = "createLogicalDevice", at = @At("RETURN"))
    private static void salts$ready(CallbackInfo info) {
        NativeVulkanSetup.ready(Vulkan.getDevice(), Vulkan.getGraphicsQueue(), ((QueueFamiliesAccessor)(Object)Vulkan.findQueueFamilies(Vulkan.getDevice().getPhysicalDevice())).salts$graphics());
    }
}
