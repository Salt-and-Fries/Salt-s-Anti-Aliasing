package org.betterLostItems.salts_anti_aliasing.validation.mixin;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
@Mixin(CreateWorldScreen.class)
public interface CreateWorldAccessor { @Invoker("onCreate") void salts$create(); }
