package org.betterLostItems.salts_anti_aliasing.vulkanmod.mixin;

import net.vulkanmod.vulkan.Vulkan;
import org.betterLostItems.salts_anti_aliasing.vulkanmod.SaltsVulkanModClient;
import org.betterLostItems.salts_anti_aliasing.vulkanmod.NativeVulkanSetup;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Vulkan.class, remap = false)
abstract class VulkanLifecycleMixin {
    @Inject(method = "cleanUp", at = @At("HEAD"))
    private static void salts$closeBeforeDevice(CallbackInfo info) { SaltsVulkanModClient.close();
        org.betterLostItems.salts_anti_aliasing.vulkanmod.legacy.VulkanVboLifetimes.close();
        net.minecraft.client.Minecraft.getInstance().getMainRenderTarget().destroyBuffers(); }

    // Native cleanup drains deferred frees but does not close every live texture or pipeline.
    @Inject(method = "cleanUp", at = @At(value = "INVOKE", target = "Lorg/lwjgl/util/vma/Vma;vmaDestroyAllocator(J)V"))
    private static void salts$closeRemainingNativeObjects(CallbackInfo info) {
        org.betterLostItems.salts_anti_aliasing.vulkanmod.gpu.NativeResourceLifetimes.closeBeforeAllocator();
    }

    @Inject(method = "cleanUp", at = @At(value = "INVOKE", target = "Lnet/vulkanmod/vulkan/device/DeviceManager;destroy()V"))
    private static void salts$closeSdkAfterSwapchain(CallbackInfo info) { NativeVulkanSetup.shutdown(); }
}
