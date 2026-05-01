package org.betterLostItems.salts_anti_aliasing.mixin.client;

import com.mojang.blaze3d.buffers.GpuBuffer;
import net.minecraft.client.renderer.PostPass;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

/**
 * Contract for post pass accessor behavior so platform-specific code can depend on a small,
 * testable surface. Mixin bridge code that hooks Minecraft internals at narrowly chosen call sites
 * so the renderer can be redirected without forking vanilla classes.
 */
@Mixin(PostPass.class)
public interface PostPassAccessor {
    @Accessor("customUniforms")
    Map<String, GpuBuffer> saltsAntiAliasing$customUniforms();
}
