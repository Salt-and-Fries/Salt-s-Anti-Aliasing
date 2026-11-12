package org.betterLostItems.salts_anti_aliasing.vulkanmod.mixin;
import net.vulkanmod.vulkan.Vulkan;
import org.betterLostItems.salts_anti_aliasing.vulkanmod.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(value = Vulkan.class, remap = false)
abstract class VulkanLifecycleMixin {
    @Inject(method = "cleanUp", at = @At("HEAD"))
    private static void salts$close(CallbackInfo ci) { Vulkan.waitIdle(); SaltsVulkanModClient.close(); }
    @Inject(method = "cleanUp", at = @At(value = "INVOKE", target = "Lorg/lwjgl/vulkan/VK10;vkDestroyDevice(Lorg/lwjgl/vulkan/VkDevice;Lorg/lwjgl/vulkan/VkAllocationCallbacks;)V"))
    private static void salts$sdk(CallbackInfo ci) { NativeVulkanSetup.shutdown(); }
}
