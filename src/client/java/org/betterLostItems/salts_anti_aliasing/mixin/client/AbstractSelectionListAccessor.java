package org.betterLostItems.salts_anti_aliasing.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

/**
 * Documents abstract selection list accessor behavior for Salt's Anti Aliasing. Mixin bridge code for
 * carefully scoped hooks into Minecraft rendering and options screens.
 */
@Mixin(targets = "net.minecraft.client.gui.components.AbstractSelectionList")
public interface AbstractSelectionListAccessor {
    @Accessor("children")
    List<Object> saltsAntiAliasing$children();
}
