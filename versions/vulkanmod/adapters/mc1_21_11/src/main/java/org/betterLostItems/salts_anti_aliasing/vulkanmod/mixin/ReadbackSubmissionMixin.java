package org.betterLostItems.salts_anti_aliasing.vulkanmod.mixin;

import net.vulkanmod.vulkan.Renderer;
import net.vulkanmod.vulkan.texture.ImageUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ImageUtil.class, remap = false)
abstract class ReadbackSubmissionMixin {
    @Inject(method = "copyImageToBuffer", at = @At("HEAD"))
    private static void salts$submitBeforeReadback(CallbackInfo info) {
        if (Renderer.isRecording()) Renderer.getInstance().flushCmds();
    }
}
