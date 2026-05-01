package org.betterLostItems.salts_anti_aliasing.mixin.client;

import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.PostPass;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

/**
 * Documents post chain accessor behavior for Salt's Anti Aliasing. Mixin bridge code for carefully
 * scoped hooks into Minecraft rendering and options screens.
 */
@Mixin(PostChain.class)
public interface PostChainAccessor {
    @Accessor("passes")
    List<PostPass> saltsAntiAliasing$passes();
}
