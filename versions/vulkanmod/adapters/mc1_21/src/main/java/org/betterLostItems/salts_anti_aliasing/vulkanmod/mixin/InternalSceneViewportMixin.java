package org.betterLostItems.salts_anti_aliasing.vulkanmod.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.*;
import com.llamalad7.mixinextras.sugar.Local;
import net.vulkanmod.gl.VkGlFramebuffer;
import net.vulkanmod.vulkan.Renderer;
import org.betterLostItems.salts_anti_aliasing.vulkanmod.SaltsVulkanModClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Our scene uses the native Vulkan orientation; old GL emulation flips other offscreen targets. */
@Mixin(value = VkGlFramebuffer.class, remap = false)
abstract class InternalSceneViewportMixin {
    @WrapOperation(method = "beginRendering(Lnet/vulkanmod/gl/VkGlFramebuffer;)V", at = @At(value = "INVOKE", target =
            "Lnet/vulkanmod/vulkan/Renderer;setInvertedViewport(IIII)V"))
    private static void salts$sceneOrientation(int x, int y, int width, int height, Operation<Void> original,
                                               @Local(argsOnly = true) VkGlFramebuffer target) {
        if (SaltsVulkanModClient.internalFramebuffer(target.getFramebuffer())) Renderer.setViewport(x, y, width, height);
        else original.call(x, y, width, height);
    }
    @WrapOperation(method = "beginRendering(Lnet/vulkanmod/gl/VkGlFramebuffer;)V", at = @At(value = "INVOKE", target =
            "Lnet/vulkanmod/vulkan/VRenderSystem;disableCull()V"))
    private static void salts$preserveSceneCulling(Operation<Void> original, @Local(argsOnly = true) VkGlFramebuffer target) {
        if (!SaltsVulkanModClient.internalFramebuffer(target.getFramebuffer())) original.call();
    }
}
