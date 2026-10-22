package org.betterLostItems.salts_anti_aliasing.mixin.client;

import com.mojang.renderpearl.frontend.FrontendCommandEncoder;
import com.mojang.renderpearl.backend.api.CommandEncoderBackend;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Exposes the backend command encoder so DLSS can evaluate on a Vulkan command buffer.
 */
@Mixin(FrontendCommandEncoder.class)
public interface CommandEncoderAccessor {
    @Invoker("backend")
    CommandEncoderBackend saltsAntiAliasing$backend();
}
