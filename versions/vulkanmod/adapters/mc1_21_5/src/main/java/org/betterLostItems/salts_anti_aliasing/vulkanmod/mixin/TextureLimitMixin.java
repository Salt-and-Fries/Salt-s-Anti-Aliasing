package org.betterLostItems.salts_anti_aliasing.vulkanmod.mixin;

import net.vulkanmod.render.engine.VkGpuDevice;
import net.vulkanmod.vulkan.device.DeviceManager;
import org.lwjgl.vulkan.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Use the physical image/framebuffer limits when allocating SSAA targets. */
@Mixin(value = VkGpuDevice.class, remap = false)
abstract class TextureLimitMixin {
    @Inject(method = "getMaxTextureSize", at = @At("RETURN"), cancellable = true)
    private void salts$physicalLimit(CallbackInfoReturnable<Integer> result) {
        try (var stack = org.lwjgl.system.MemoryStack.stackPush()) {
            var properties = VkPhysicalDeviceProperties.calloc(stack);
            VK10.vkGetPhysicalDeviceProperties(DeviceManager.physicalDevice, properties);
            var limits = properties.limits();
            result.setReturnValue(Math.min(limits.maxImageDimension2D(), Math.min(limits.maxFramebufferWidth(), limits.maxFramebufferHeight())));
        }
    }
}
