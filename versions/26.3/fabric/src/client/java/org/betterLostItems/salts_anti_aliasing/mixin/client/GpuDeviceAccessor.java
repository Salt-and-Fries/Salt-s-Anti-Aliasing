package org.betterLostItems.salts_anti_aliasing.mixin.client;

import com.mojang.renderpearl.frontend.FrontendGpuDevice;
import com.mojang.renderpearl.backend.api.GpuDeviceBackend;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes Minecraft's active GPU backend so Vulkan-specific work can verify and use Vulkan internals.
 */
@Mixin(FrontendGpuDevice.class)
public interface GpuDeviceAccessor {
    @Accessor("backend")
    GpuDeviceBackend saltsAntiAliasing$backend();
}
