package org.betterLostItems.salts_anti_aliasing.client.gui;

import net.minecraft.network.chat.Component;
import org.betterLostItems.salts_anti_aliasing.client.config.AntiAliasingMode;
import org.betterLostItems.salts_anti_aliasing.client.config.DlssQualityPreset;
import org.betterLostItems.salts_anti_aliasing.client.config.NisUpscaleQualityPreset;

/**
 * Minecraft-specific text adapter for core config values.
 *
 * <p>Core enums expose translation keys as strings so they can remain loader- and
 * version-neutral. This small adapter is the only place that turns those keys into
 * Minecraft {@link Component} objects for the Fabric client UI.</p>
 */
public final class ClientText {
    /**
     * Creates a client text instance with the collaborators or initial state supplied by the
     * caller.
     */
    private ClientText() {
    }

    /**
     * Handles label as part of the anti-aliasing render, configuration, or compatibility flow.
     * @param mode anti-aliasing mode requested by UI, hotkey, or loaded config
     * @return text component shown to the player
     */
    public static Component label(AntiAliasingMode mode) {
        return Component.translatable(mode.translationKey());
    }

    /**
     * Handles summary as part of the anti-aliasing render, configuration, or compatibility flow.
     * @param mode anti-aliasing mode requested by UI, hotkey, or loaded config
     * @return short text component summarizing the current setting
     */
    public static Component summary(AntiAliasingMode mode) {
        return Component.translatable(mode.translationKey() + ".summary");
    }

    /**
     * Handles label as part of the anti-aliasing render, configuration, or compatibility flow.
     * @param preset quality preset selected by the user or loaded from config
     * @return text component shown to the player
     */
    public static Component label(NisUpscaleQualityPreset preset) {
        return Component.translatable(preset.translationKey());
    }

    /**
     * Handles label as part of the anti-aliasing render, configuration, or compatibility flow.
     * @param preset DLSS quality preset selected by the user or loaded from config
     * @return text component shown to the player
     */
    public static Component label(DlssQualityPreset preset) {
        return Component.translatable(preset.translationKey());
    }
}
