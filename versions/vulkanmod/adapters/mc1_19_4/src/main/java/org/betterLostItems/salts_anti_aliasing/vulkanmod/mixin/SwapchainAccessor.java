package org.betterLostItems.salts_anti_aliasing.vulkanmod.mixin;

import net.vulkanmod.vulkan.SwapChain;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = SwapChain.class, remap = false)
public interface SwapchainAccessor {
    @Accessor("currentLayout") int[] salts$layouts();
}
