package org.betterLostItems.salts_anti_aliasing.vulkanmod.mixin;
import net.vulkanmod.vulkan.Pipeline.PipelineState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(value = PipelineState.class, remap = false)
public interface PipelineStateAccessor {
    @Accessor("blendState") net.vulkanmod.vulkan.Pipeline.BlendState salts$blendState();
}
