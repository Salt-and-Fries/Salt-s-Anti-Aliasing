package org.betterLostItems.salts_anti_aliasing.vulkanmod.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.*;
import net.vulkanmod.vulkan.Drawer;
import org.betterLostItems.salts_anti_aliasing.vulkanmod.AcquiredImageWait;
import org.lwjgl.vulkan.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = Drawer.class, remap = false)
abstract class SubmissionWaitMixin {
    @Shadow private java.util.ArrayList<Long> renderFinishedSemaphores;
    /** Native code selects by acquired image even though it records and fences by ring frame. */
    @WrapOperation(method = "drawFrame", at = @At(value = "INVOKE", target = "Ljava/util/List;get(I)Ljava/lang/Object;"))
    private Object salts$recordedFrame(java.util.List<?> list,int image,Operation<Object> original) {
        return list == DrawerAccessor.salts$commands() ? original.call(list,Drawer.getCurrentFrame()) : original.call(list,image);
    }

    @WrapOperation(method = "drawFrame", at = @At(value = "INVOKE", target = "Ljava/util/ArrayList;get(I)Ljava/lang/Object;"))
    private Object salts$presentWait(java.util.ArrayList<?> list, int index, Operation<Object> operation) {
        return list == renderFinishedSemaphores
                ? org.betterLostItems.salts_anti_aliasing.vulkanmod.PresentationReadySemaphores.current()
                : operation.call(list, index);
    }

    @WrapOperation(method = "drawFrame", at = @At(value = "INVOKE", target =
            "Lorg/lwjgl/vulkan/VK10;vkQueueSubmit(Lorg/lwjgl/vulkan/VkQueue;Lorg/lwjgl/vulkan/VkSubmitInfo;J)I"))
    private int salts$acquireWait(VkQueue queue, VkSubmitInfo info, long fence, Operation<Integer> operation) {
        return AcquiredImageWait.submit(info, fixed -> operation.call(queue, fixed, fence));
    }
}
