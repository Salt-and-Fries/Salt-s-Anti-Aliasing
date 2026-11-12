package org.betterLostItems.salts_anti_aliasing.vulkanmod.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.*;
import net.vulkanmod.render.engine.VkGpuDevice;
import net.vulkanmod.interfaces.shader.ExtendedRenderPipeline;
import net.vulkanmod.vulkan.shader.GraphicsPipeline;
import org.betterLostItems.salts_anti_aliasing.vulkanmod.gpu.RenderPipelineLifetimes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** These VulkanMod releases otherwise overwrite the native pipeline on shader reload. */
@Mixin(value = VkGpuDevice.class, remap = false)
abstract class RenderPipelineLifecycleMixin {
    @WrapOperation(method = "*", require = 1, at = @At(value = "INVOKE", target =
            "Lnet/vulkanmod/interfaces/shader/ExtendedRenderPipeline;setPipeline(Lnet/vulkanmod/vulkan/shader/GraphicsPipeline;)V"))
    private void salts$trackPipeline(ExtendedRenderPipeline owner, GraphicsPipeline pipeline, Operation<Void> original) {
        RenderPipelineLifetimes.replaced(owner, pipeline, pipeline::scheduleCleanUp, pipeline::cleanUp);
        original.call(owner, pipeline);
    }
}
