package org.betterLostItems.salts_anti_aliasing.vulkanmod.mixin;
import net.vulkanmod.vulkan.framebuffer.Framebuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(targets = "net.vulkanmod.vulkan.pass.DefaultMainPass", remap = false)
public interface MainPassAccessor { @Accessor("mainFramebuffer") Framebuffer salts$framebuffer(); }
