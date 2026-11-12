package org.betterLostItems.salts_anti_aliasing.vulkanmod.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.*;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Screenshot;
import net.vulkanmod.vulkan.Renderer;
import net.vulkanmod.gl.GlTexture;
import net.vulkanmod.vulkan.texture.VTextureSelector;
import net.vulkanmod.vulkan.texture.VulkanImage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = Screenshot.class, priority = 1100)
abstract class ScreenshotSubmissionMixin {
    @WrapMethod(method = "takeScreenshot")
    private static NativeImage salts$preserveTextureBindings(RenderTarget target, Operation<NativeImage> original) {
        var bound = GlTexture.getBoundTexture();
        var textures = new VulkanImage[VTextureSelector.SIZE];
        for (int i = 0; i < textures.length; i++) textures[i] = VTextureSelector.getBoundTexture(i);
        try { return original.call(target); }
        finally {
            // Auto world icons are captured before the HUD. Leaving the swapchain bound
            // makes the next textured draw treat its color attachment as a sampled image.
            GlTexture.bindTexture(bound == null ? 0 : ((GlTextureAccessor) bound).salts$id());
            for (int i = 0; i < textures.length; i++)
                if (VTextureSelector.getBoundTexture(i) != textures[i]) VTextureSelector.bindTexture(i, textures[i]);
        }
    }
    @WrapOperation(method = "takeScreenshot", at = @At(value = "INVOKE", target =
            "Lnet/vulkanmod/vulkan/Renderer;flushCmds()V", remap = false))
    private static void salts$singleReadbackSubmission(Renderer renderer, Operation<Void> original) {
        // downloadTexture performs the flush while the main-pass restart is suspended.
    }
}
