package org.betterLostItems.salts_anti_aliasing.vulkanmod.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.vulkanmod.vulkan.Renderer;
import net.vulkanmod.vulkan.framebuffer.Framebuffer;
import net.vulkanmod.vulkan.framebuffer.RenderPass;
import org.betterLostItems.salts_anti_aliasing.vulkanmod.SaltsVulkanModClient;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = Renderer.class, remap = false)
abstract class MultisampleRendererMixin {
    @WrapMethod(method = "beginRenderPass")
    private boolean salts$multisample(RenderPass pass, Framebuffer framebuffer, Operation<Boolean> operation) {
        var world = SaltsVulkanModClient.multisample(framebuffer);
        return world == null ? operation.call(pass, framebuffer) : operation.call(world.pass, world);
    }
}
