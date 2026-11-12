package org.betterLostItems.salts_anti_aliasing.client.compat.sodium;
import net.caffeinemc.mods.sodium.api.config.ConfigEntryPoint;
import net.caffeinemc.mods.sodium.api.config.structure.ConfigBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.betterLostItems.salts_anti_aliasing.SaltsAntiAliasing;
import org.betterLostItems.salts_anti_aliasing.client.SaltsAntiAliasingClient;
import org.betterLostItems.salts_anti_aliasing.client.gui.AntiAliasingConfigScreen;
/** Sodium links to the same screen instead of maintaining a second set of settings. */
public final class SaltsAntiAliasingSodiumConfig implements ConfigEntryPoint {
    @Override public void registerConfigLate(ConfigBuilder builder){
        builder.registerOwnModOptions().addPage(builder.createOptionPage()
                .setName(Component.translatable("screen.salts_anti_aliasing.config"))
                .addOption(builder.createExternalButtonOption(
                                Identifier.fromNamespaceAndPath(SaltsAntiAliasing.MOD_ID, "settings"))
                        .setName(Component.translatable("screen.salts_anti_aliasing.config"))
                        .setTooltip(Component.translatable("screen.salts_anti_aliasing.config.tooltip")
                                .append("\n\n")
                                .append(Component.translatable("screen.salts_anti_aliasing.config.vulkan_required")))
                        .setEnabledProvider(state -> SaltsAntiAliasingClient.runtime().isVulkanActive())
                        .setScreenConsumer(parent -> {
                            // Guard keyboard activation too: Sodium 0.9's external button
                            // checks enabled for mouse clicks, but not its key handler.
                            if (SaltsAntiAliasingClient.runtime().isVulkanActive()) {
                                Minecraft.getInstance().setScreenAndShow(new AntiAliasingConfigScreen(parent));
                            }
                        })));
    }
}
