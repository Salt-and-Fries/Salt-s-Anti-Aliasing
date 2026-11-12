package org.betterLostItems.salts_anti_aliasing.vulkanmod.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.vulkanmod.vulkan.Vulkan;
import org.betterLostItems.salts_anti_aliasing.vulkanmod.NativeVulkanSetup;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkAllocationCallbacks;
import org.lwjgl.vulkan.VkInstanceCreateInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = Vulkan.class, remap = false)
abstract class NativeInstanceMixin {
    @WrapOperation(method = "createInstance", at = @At(value = "INVOKE", target =
            "Lorg/lwjgl/vulkan/VK10;vkCreateInstance(Lorg/lwjgl/vulkan/VkInstanceCreateInfo;Lorg/lwjgl/vulkan/VkAllocationCallbacks;Lorg/lwjgl/PointerBuffer;)I"))
    private static int salts$instanceRequirements(VkInstanceCreateInfo info, VkAllocationCallbacks allocator,
                                                 PointerBuffer result, Operation<Integer> original) {
        NativeVulkanSetup.instance(MemoryStack.stackGet(), info);
        return original.call(info, allocator, result);
    }
}
