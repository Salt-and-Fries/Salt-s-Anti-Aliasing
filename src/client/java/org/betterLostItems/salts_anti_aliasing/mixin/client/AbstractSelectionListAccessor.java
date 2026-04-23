package org.betterLostItems.salts_anti_aliasing.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

@Mixin(targets = "net.minecraft.client.gui.components.AbstractSelectionList")
public interface AbstractSelectionListAccessor {
    @Accessor("children")
    List<Object> saltsAntiAliasing$children();
}
