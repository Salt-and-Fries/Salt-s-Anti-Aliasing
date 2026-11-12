package org.betterLostItems.salts_anti_aliasing.vulkanmod.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import net.vulkanmod.vulkan.Vulkan;
import org.lwjgl.vulkan.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Use the physical image/framebuffer limits when allocating SSAA targets. */
@Mixin(RenderSystem.class)
abstract class TextureLimitMixin {
    @Inject(method = "maxSupportedTextureSize", at = @At("RETURN"), cancellable = true)
    private static void salts$physicalLimit(CallbackInfoReturnable<Integer> result) {
        if (Vulkan.getDevice() == null) return;
        try (var stack = org.lwjgl.system.MemoryStack.stackPush()) {
            var properties = VkPhysicalDeviceProperties.calloc(stack);
            VK10.vkGetPhysicalDeviceProperties(Vulkan.getDevice().getPhysicalDevice(), properties);
            var limits = properties.limits();
            result.setReturnValue(Math.min(limits.maxImageDimension2D(), Math.min(limits.maxFramebufferWidth(), limits.maxFramebufferHeight())));
        }
    }
}
