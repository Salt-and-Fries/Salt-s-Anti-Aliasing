package org.betterLostItems.salts_anti_aliasing.validation.mixin;

import net.vulkanmod.vulkan.Vulkan;
import net.vulkanmod.vulkan.texture.VulkanImage;
import org.betterLostItems.salts_anti_aliasing.vulkanmod.gpu.VulkanContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = VulkanImage.class, remap = false)
abstract class VulkanImageNamesMixin {
    @org.spongepowered.asm.mixin.Unique private static int salts$readers;
    @Inject(method = "readOnlyLayout()V", at = @At("HEAD"))
    private void salts$traceMainReader(CallbackInfo info) {
        var renderer = net.vulkanmod.vulkan.Renderer.getInstance();
        if (renderer == null || renderer.getMainPass() == null || salts$readers >= 4) return;
        var image = (VulkanImage) (Object) this;
        if (image.getCurrentLayout() != 5 && renderer.getMainPass().getMainFramebuffer().getColorAttachment().getId() == image.getId()) {
            salts$readers++;
            new Throwable("SALTS_MAIN_IMAGE_READER layout=" + image.getCurrentLayout()).printStackTrace();
        }
    }
    @Inject(method = "<init>", at = @At("RETURN"))
    private void salts$nameImage(CallbackInfo info) {
        var image = (VulkanImage) (Object) this;
        if (image.getId() == 0 || Vulkan.getAllocator() == 0) return;
        var caller = java.util.Arrays.stream(Thread.currentThread().getStackTrace())
                .filter(frame -> !frame.getClassName().contains("VulkanImage") && !frame.getClassName().equals("java.lang.Thread"))
                .limit(3).map(StackTraceElement::toString).collect(java.util.stream.Collectors.joining(" / "));
        new VulkanContext(Vulkan.getVkDevice(), Vulkan.getAllocator()).debugName(
                org.lwjgl.vulkan.VK10.VK_OBJECT_TYPE_IMAGE, image.getId(),
                "VulkanMod " + image.width + "x" + image.height + " format=" + image.format + " " + caller);
    }
}
