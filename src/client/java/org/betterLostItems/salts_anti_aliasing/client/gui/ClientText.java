package org.betterLostItems.salts_anti_aliasing.client.gui;

import net.minecraft.network.chat.Component;
import org.betterLostItems.salts_anti_aliasing.client.config.AntiAliasingMode;
import org.betterLostItems.salts_anti_aliasing.client.config.NisUpscaleQualityPreset;

/**
 * Minecraft-specific text adapter for core config values.
 *
 * <p>Core enums expose translation keys as strings so they can remain loader- and
 * version-neutral. This small adapter is the only place that turns those keys into
 * Minecraft {@link Component} objects for the Fabric client UI.</p>
 */
public final class ClientText {
    private ClientText() {
    }

    public static Component label(AntiAliasingMode mode) {
        return Component.translatable(mode.translationKey());
    }

    public static Component summary(AntiAliasingMode mode) {
        return Component.translatable(mode.translationKey() + ".summary");
    }

    public static Component label(NisUpscaleQualityPreset preset) {
        return Component.translatable(preset.translationKey());
    }
}
