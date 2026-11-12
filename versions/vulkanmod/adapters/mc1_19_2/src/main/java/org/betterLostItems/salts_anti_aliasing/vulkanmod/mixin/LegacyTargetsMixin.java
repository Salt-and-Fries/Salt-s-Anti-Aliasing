package org.betterLostItems.salts_anti_aliasing.vulkanmod.mixin;
import com.mojang.blaze3d.pipeline.RenderTarget;
import net.vulkanmod.vulkan.Framebuffer;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.betterLostItems.salts_anti_aliasing.vulkanmod.legacy.*;
/** Supply real attachments for the texture-target path omitted by this VulkanMod release. */
@Mixin(value = RenderTarget.class, priority = 1100)
abstract class LegacyTargetsMixin implements LegacyTargetAccess {
    @Shadow public int width, height, viewWidth, viewHeight;
    @Unique private Framebuffer salts$target;
    public Framebuffer salts$framebuffer() { return salts$target; }
    @Inject(method = {"createBuffers", "resize"}, at = @At("HEAD"), cancellable = true)
    private void salts$create(int width, int height, boolean osx, CallbackInfo ci) {
        if ((Object) this instanceof com.mojang.blaze3d.pipeline.MainTarget) {
            this.width = this.viewWidth = width; this.height = this.viewHeight = height;
            ci.cancel();
            return;
        }
        if (salts$target != null) { net.vulkanmod.vulkan.Vulkan.waitIdle(); salts$target.cleanUp(); }
        salts$target = new Framebuffer(width, height, 37, false);
        this.width = this.viewWidth = width; this.height = this.viewHeight = height;
        ci.cancel();
    }
    @Inject(method = "destroyBuffers", at = @At("HEAD"), cancellable = true)
    private void salts$destroy(CallbackInfo ci) {
        if (salts$target != null) { net.vulkanmod.vulkan.Vulkan.waitIdle(); salts$target.cleanUp(); salts$target = null; }
        ci.cancel();
    }
    @Inject(method = "bindWrite", at = @At("HEAD"), cancellable = true)
    private void salts$bind(boolean viewport, CallbackInfo ci) {
        if (salts$target != null) {
            LegacyRenderer.getInstance().beginRendering(null, salts$target);
            if (viewport) { LegacyRenderer.setViewport(0, 0, width, height); LegacyRenderer.setScissor(0, 0, width, height); }
        }
        ci.cancel();
    }
}
