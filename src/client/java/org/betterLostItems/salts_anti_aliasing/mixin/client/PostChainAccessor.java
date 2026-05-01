package org.betterLostItems.salts_anti_aliasing.mixin.client;

import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.PostPass;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

/**
 * Contract for post chain accessor behavior so platform-specific code can depend on a small,
 * testable surface. Mixin bridge code that hooks Minecraft internals at narrowly chosen call sites
 * so the renderer can be redirected without forking vanilla classes.
 */
@Mixin(PostChain.class)
public interface PostChainAccessor {
    @Accessor("passes")
    List<PostPass> saltsAntiAliasing$passes();
}
