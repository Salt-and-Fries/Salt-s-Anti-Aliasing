package org.betterLostItems.salts_anti_aliasing.vulkanmod.mixin;

import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(OptionsSubScreen.class)
public interface OptionsScreenAccessor {
    @Accessor("layout") @Mutable void salts$layout(HeaderAndFooterLayout layout);
}
