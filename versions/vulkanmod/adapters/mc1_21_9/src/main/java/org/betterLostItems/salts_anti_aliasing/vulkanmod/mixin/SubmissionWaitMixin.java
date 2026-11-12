package org.betterLostItems.salts_anti_aliasing.vulkanmod.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.*;
import net.vulkanmod.vulkan.Renderer;
import org.betterLostItems.salts_anti_aliasing.vulkanmod.AcquiredImageWait;
import org.lwjgl.vulkan.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = Renderer.class, remap = false)
abstract class SubmissionWaitMixin {
    @WrapOperation(method = {"flushCmds", "submitFrame"}, at = @At(value = "INVOKE", target =
            "Lorg/lwjgl/vulkan/VK10;vkQueueSubmit(Lorg/lwjgl/vulkan/VkQueue;Lorg/lwjgl/vulkan/VkSubmitInfo;J)I"))
    private int salts$acquireWait(VkQueue queue, VkSubmitInfo info, long fence, Operation<Integer> operation) {
        return AcquiredImageWait.submit(info, fixed -> operation.call(queue, fixed, fence));
    }
}
