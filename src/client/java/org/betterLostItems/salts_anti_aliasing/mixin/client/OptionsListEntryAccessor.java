package org.betterLostItems.salts_anti_aliasing.mixin.client;

import net.minecraft.client.OptionInstance;
import net.minecraft.client.gui.components.AbstractWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(targets = "net.minecraft.client.gui.components.OptionsList$Entry")
public interface OptionsListEntryAccessor {
    @Invoker("findOption")
    AbstractWidget saltsAntiAliasing$findOption(OptionInstance<?> optionInstance);
}
