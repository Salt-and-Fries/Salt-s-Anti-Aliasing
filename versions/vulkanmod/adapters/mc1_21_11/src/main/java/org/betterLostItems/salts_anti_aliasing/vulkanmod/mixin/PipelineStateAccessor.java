package org.betterLostItems.salts_anti_aliasing.vulkanmod.mixin;

import net.vulkanmod.vulkan.framebuffer.RenderPass;
import net.vulkanmod.vulkan.shader.PipelineState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = PipelineState.class, remap = false)
public interface PipelineStateAccessor {
    @Accessor("renderPass") RenderPass salts$renderPass();
    @Accessor("blendState_i") int salts$blendState();
}
