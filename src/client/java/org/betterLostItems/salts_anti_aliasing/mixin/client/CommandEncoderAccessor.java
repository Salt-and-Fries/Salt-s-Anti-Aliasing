package org.betterLostItems.salts_anti_aliasing.mixin.client;

import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.CommandEncoderBackend;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Exposes the backend command encoder so DLSS can evaluate on a Vulkan command buffer.
 */
@Mixin(CommandEncoder.class)
public interface CommandEncoderAccessor {
    @Invoker("backend")
    CommandEncoderBackend saltsAntiAliasing$backend();
}
