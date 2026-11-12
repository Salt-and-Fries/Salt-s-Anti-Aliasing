package org.betterLostItems.salts_anti_aliasing.validation.mixin;
import net.minecraft.client.gui.screens.worldselection.*;
import net.minecraft.client.gui.components.EditBox;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(WorldGenSettingsComponent.class)
public interface WorldGenSettingsAccessor {
    @Accessor("seedEdit") EditBox salts$seed();
    @Accessor("settings") void salts$settings(WorldCreationContext context);
}
