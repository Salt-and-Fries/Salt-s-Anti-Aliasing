package org.betterLostItems.salts_anti_aliasing.vulkanmod.mixin;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.vulkanmod.vulkan.*;
import net.vulkanmod.vulkan.shader.PipelineState;
import org.betterLostItems.salts_anti_aliasing.vulkanmod.MultisampleWorld;
import org.betterLostItems.salts_anti_aliasing.vulkanmod.legacy.LegacyPipelineState;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** Native keys omit framebuffer formats and rasterization samples. Capture both immutably. */
@Mixin(value = PipelineState.class, remap = false)
abstract class LegacyPipelineStateMixin implements LegacyPipelineState {
    @Unique private Signature salts$signature;
    public Signature salts$signature() { return salts$signature; }
    @Inject(method = "<init>", at = @At("RETURN"))
    private void salts$key(CallbackInfo ci) {
        var framebuffer = org.betterLostItems.salts_anti_aliasing.vulkanmod.legacy.LegacyRenderer.getInstance().salts$boundFramebuffer();
        var msaa = framebuffer instanceof MultisampleWorld multisample ? multisample : null;
        salts$signature = new Signature(framebuffer == null ? VulkanWindowAccessor.salts$format() : framebuffer.format,
                framebuffer == null ? VulkanWindowAccessor.salts$depthFormat() : framebuffer.depthFormat, msaa == null ? 1 : msaa.samples(),
                msaa != null && msaa.alphaToCoverage, msaa != null && msaa.hand);
    }
    @ModifyReturnValue(method = "equals", at = @At("RETURN"))
    private boolean salts$equals(boolean equal, Object other) {
        return equal && other instanceof LegacyPipelineState state && salts$signature.equals(state.salts$signature());
    }
    @ModifyReturnValue(method = "hashCode", at = @At("RETURN"))
    private int salts$hash(int hash) { return 31 * hash + salts$signature.hashCode(); }
}
