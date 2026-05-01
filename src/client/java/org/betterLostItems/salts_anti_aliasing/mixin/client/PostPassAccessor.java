package org.betterLostItems.salts_anti_aliasing.mixin.client;

import com.mojang.blaze3d.buffers.GpuBuffer;
import net.minecraft.client.renderer.PostPass;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

/**
 * Documents post pass accessor behavior for Salt's Anti Aliasing. Mixin bridge code for carefully
 * scoped hooks into Minecraft rendering and options screens.
 */
@Mixin(PostPass.class)
public interface PostPassAccessor {
    @Accessor("customUniforms")
    Map<String, GpuBuffer> saltsAntiAliasing$customUniforms();
}
