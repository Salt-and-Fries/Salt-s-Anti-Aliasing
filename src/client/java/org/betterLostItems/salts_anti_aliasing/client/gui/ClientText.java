package org.betterLostItems.salts_anti_aliasing.client.gui;

import net.minecraft.network.chat.Component;
import org.betterLostItems.salts_anti_aliasing.client.config.AntiAliasingMode;
import org.betterLostItems.salts_anti_aliasing.client.config.NisUpscaleQualityPreset;

/**
 * Minecraft-specific text adapter for core config values.
 *
 * <p>Core enums expose translation keys as strings so they can remain loader- and
 * version-neutral. This small adapter is the only place that turns those keys into
 * Minecraft {@link Component} objects for the modern Fabric client UI.</p>
 */
public final class ClientText {
    /**
     * Creates a client text with the collaborators or initial state supplied by the caller.
     */
    private ClientText() {
    }

    /**
     * Coordinates label within the anti-aliasing render, configuration, or compatibility flow.
     * @param mode requested anti-aliasing mode
     * @return label value produced or selected by this code path
     */
    public static Component label(AntiAliasingMode mode) {
        return Component.translatable(mode.translationKey());
    }

    /**
     * Coordinates summary within the anti-aliasing render, configuration, or compatibility flow.
     * @param mode requested anti-aliasing mode
     * @return summary value produced or selected by this code path
     */
    public static Component summary(AntiAliasingMode mode) {
        return Component.translatable(mode.translationKey() + ".summary");
    }

    /**
     * Coordinates label within the anti-aliasing render, configuration, or compatibility flow.
     * @param preset quality preset selected by config or UI
     * @return label value produced or selected by this code path
     */
    public static Component label(NisUpscaleQualityPreset preset) {
        return Component.translatable(preset.translationKey());
    }
}
