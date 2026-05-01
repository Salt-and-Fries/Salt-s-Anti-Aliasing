package org.betterLostItems.salts_anti_aliasing.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

/**
 * Contract for abstract selection list accessor behavior so platform-specific code can depend on a
 * small, testable surface. Mixin bridge code that hooks Minecraft internals at narrowly chosen call
 * sites so the renderer can be redirected without forking vanilla classes.
 */
@Mixin(targets = "net.minecraft.client.gui.components.AbstractSelectionList")
public interface AbstractSelectionListAccessor {
    @Accessor("children")
    List<Object> saltsAntiAliasing$children();
}
