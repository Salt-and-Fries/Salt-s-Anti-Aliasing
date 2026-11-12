package org.betterLostItems.salts_anti_aliasing.vulkanmod.mixin;

import net.vulkanmod.vulkan.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = Drawer.class, remap = false)
public interface DrawerAccessor {
    @org.spongepowered.asm.mixin.gen.Invoker("recreateSwapChain") void salts$recreateSwapChain();
    @Accessor("boundFramebuffer") Framebuffer salts$boundFramebuffer();
    @Accessor("boundFramebuffer") void salts$boundFramebuffer(Framebuffer framebuffer);
}
