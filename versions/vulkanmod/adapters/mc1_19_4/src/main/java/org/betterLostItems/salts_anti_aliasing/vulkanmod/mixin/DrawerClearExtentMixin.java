package org.betterLostItems.salts_anti_aliasing.vulkanmod.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.*;
import net.vulkanmod.vulkan.Drawer;
import org.lwjgl.vulkan.VkExtent2D;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = Drawer.class, remap = false)
abstract class DrawerClearExtentMixin {
    @WrapOperation(method = "clearAttachments", at = @At(value = "INVOKE", target =
            "Lnet/vulkanmod/vulkan/Vulkan;getSwapchainExtent()Lorg/lwjgl/vulkan/VkExtent2D;"))
    private static VkExtent2D salts$attachmentExtent(Operation<VkExtent2D> original) {
        var framebuffer = ((DrawerAccessor) Drawer.getInstance()).salts$boundFramebuffer();
        return framebuffer == null ? original.call() : VkExtent2D.calloc(org.lwjgl.system.MemoryStack.stackGet())
                .set(framebuffer.width, framebuffer.height);
    }
}
