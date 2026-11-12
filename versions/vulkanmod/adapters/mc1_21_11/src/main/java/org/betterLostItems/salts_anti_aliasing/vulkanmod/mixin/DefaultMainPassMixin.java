package org.betterLostItems.salts_anti_aliasing.vulkanmod.mixin;

import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.opengl.GlTexture;
import net.vulkanmod.render.engine.VkGpuTexture;
import org.betterLostItems.salts_anti_aliasing.vulkanmod.SaltsVulkanModClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** VulkanMod routes both terrain and main-color render passes through this fast path. */
@Mixin(targets = "net.vulkanmod.vulkan.pass.DefaultMainPass", remap = false)
abstract class DefaultMainPassMixin {
    @ModifyArg(method = "end", at = @At(value = "INVOKE", target =
            "Lnet/vulkanmod/vulkan/texture/VulkanImage;transitionImageLayout(Lorg/lwjgl/system/MemoryStack;Lorg/lwjgl/vulkan/VkCommandBuffer;I)V"), index = 2)
    private int salts$proxyPresentLayout(int layout) {
        return org.betterLostItems.salts_anti_aliasing.client.render.vulkan.fsr.FsrRuntime.instance().isFrameGenerationSwapchainOwned()
                ? org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL : layout;
    }
    @Inject(method = "rebindMainTarget", at = @At("HEAD"), cancellable = true)
    private void salts$bindInternalScene(CallbackInfo info) {
        var scene = SaltsVulkanModClient.sceneOverride();
        if (scene == null) return;
        var color = VkGpuTexture.fromGlTexture((GlTexture) scene.getColorTexture());
        var depth = VkGpuTexture.fromGlTexture((GlTexture) scene.getDepthTexture());
        color.getFbo(depth).bind();
        GlStateManager._viewport(0, 0, scene.width, scene.height);
        info.cancel();
    }
}
