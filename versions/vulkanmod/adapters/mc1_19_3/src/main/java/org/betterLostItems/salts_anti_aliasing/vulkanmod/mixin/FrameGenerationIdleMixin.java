package org.betterLostItems.salts_anti_aliasing.vulkanmod.mixin;
import net.vulkanmod.vulkan.*;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import org.lwjgl.vulkan.VkDevice;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
@Mixin(value={Vulkan.class,Drawer.class},remap=false)
abstract class FrameGenerationIdleMixin {
    @WrapOperation(method="*",at=@At(value="INVOKE",target="Lorg/lwjgl/vulkan/VK10;vkDeviceWaitIdle(Lorg/lwjgl/vulkan/VkDevice;)I"))
    private static int salts$presenterBarrier(VkDevice device,Operation<Integer> original) {
        org.betterLostItems.salts_anti_aliasing.client.render.vulkan.fsr.FsrRuntime.instance().waitForPresents();
        return original.call(device);
    }
}
