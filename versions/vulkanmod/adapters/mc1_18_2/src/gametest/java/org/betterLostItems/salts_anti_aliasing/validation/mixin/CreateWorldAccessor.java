package org.betterLostItems.salts_anti_aliasing.validation.mixin;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.components.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.*;
@Mixin(CreateWorldScreen.class)
public interface CreateWorldAccessor {
    @Invoker("onCreate") void salts$create();
    @Accessor("nameEdit") EditBox salts$name();
    @Accessor("modeButton") CycleButton<?> salts$mode();
    @Accessor("commands") void salts$commands(boolean allowed);
    @Accessor("commandsChanged") void salts$commandsChanged(boolean changed);
}
