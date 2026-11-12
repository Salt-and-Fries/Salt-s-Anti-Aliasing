package org.betterLostItems.salts_anti_aliasing.vulkanmod.mixin;
import net.vulkanmod.vulkan.*;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** Final layout follows the current swapchain, independent of the retained render pass. */
@Mixin(targets = {"net.vulkanmod.vulkan.passes.DefaultMainPass", "net.vulkanmod.vulkan.passes.LegacyMainPass"}, remap = false)
abstract class SwapchainFinalLayoutMixin {
    @Inject(method = "end", at = @At(value = "INVOKE", target = "Lorg/lwjgl/vulkan/VK10;vkEndCommandBuffer(Lorg/lwjgl/vulkan/VkCommandBuffer;)I"))
    private void salts$presentation(VkCommandBuffer commands, CallbackInfo ci) {
        var chain = Vulkan.getSwapChain();
        try (var stack = MemoryStack.stackPush()) {
            chain.presentLayout(stack, commands, Renderer.getCurrentImage());
        }
        int layout = org.betterLostItems.salts_anti_aliasing.client.render.vulkan.fsr.FsrRuntime.instance().isFrameGenerationSwapchainOwned()
                ? org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL : org.lwjgl.vulkan.KHRSwapchain.VK_IMAGE_LAYOUT_PRESENT_SRC_KHR;
        chain.getColorAttachment().setCurrentLayout(layout);
    }
}
