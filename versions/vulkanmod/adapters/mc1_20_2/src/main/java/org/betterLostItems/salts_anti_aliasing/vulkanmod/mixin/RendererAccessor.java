package org.betterLostItems.salts_anti_aliasing.vulkanmod.mixin;

import net.vulkanmod.vulkan.Renderer;
import net.vulkanmod.vulkan.shader.Pipeline;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = Renderer.class, remap = false)
public interface RendererAccessor {
    @Accessor("boundFramebuffer") net.vulkanmod.vulkan.framebuffer.Framebuffer salts$boundFramebuffer();
}
