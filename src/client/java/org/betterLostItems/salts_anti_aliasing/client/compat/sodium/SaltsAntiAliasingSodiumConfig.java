package org.betterLostItems.salts_anti_aliasing.client.compat.sodium;
import net.caffeinemc.mods.sodium.api.config.ConfigEntryPoint;
import net.caffeinemc.mods.sodium.api.config.structure.ConfigBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.betterLostItems.salts_anti_aliasing.client.gui.AntiAliasingConfigScreen;
/** Sodium links to the same screen instead of maintaining a second set of settings. */
public final class SaltsAntiAliasingSodiumConfig implements ConfigEntryPoint {
    @Override public void registerConfigLate(ConfigBuilder builder){
        builder.registerOwnModOptions().addPage(builder.createExternalPage()
                .setName(Component.translatable("screen.salts_anti_aliasing.config"))
                .setScreenConsumer(parent->Minecraft.getInstance().setScreenAndShow(new AntiAliasingConfigScreen(parent))));
    }
}
