package org.betterLostItems.salts_anti_aliasing.client.gui;

import org.betterLostItems.salts_anti_aliasing.client.gui.legacy.*;

import net.minecraft.network.chat.Component;
import org.betterLostItems.salts_anti_aliasing.client.config.AntiAliasingMode;
import org.betterLostItems.salts_anti_aliasing.client.config.DlssQualityPreset;
import org.betterLostItems.salts_anti_aliasing.client.config.FsrQualityPreset;
import org.betterLostItems.salts_anti_aliasing.client.config.NisUpscaleQualityPreset;
import org.betterLostItems.salts_anti_aliasing.client.config.SsaaScaleLevel;

/**
 * Minecraft-specific text adapter for core config values.
 *
 * <p>Core enums expose translation keys as strings so they can remain loader- and
 * version-neutral. This small adapter is the only place that turns those keys into
 * Minecraft {@link Component} objects for the Fabric client UI.</p>
 */
public final class ClientText {
    private static final String SSAA_TOOLTIP_KEY = "options.salts_anti_aliasing.ssaa_scale.tooltip";
    private static final String SSAA_WARNING_LABEL_KEY = "options.salts_anti_aliasing.ssaa_scale.warning_label";
    private static final String SSAA_WARNING_KEY = "options.salts_anti_aliasing.ssaa_scale.warning";

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
        return org.betterLostItems.salts_anti_aliasing.client.gui.legacy.LegacyComponents.translatable(mode.selectionMode().translationKey());
    }

    /**
     * Handles summary as part of the anti-aliasing render, configuration, or compatibility flow.
     * @param mode anti-aliasing mode requested by UI, hotkey, or loaded config
     * @return short text component summarizing the current setting
     */
    public static Component summary(AntiAliasingMode mode) {
        return org.betterLostItems.salts_anti_aliasing.client.gui.legacy.LegacyComponents.translatable(mode.translationKey() + ".summary");
    }

    /**
     * Handles label as part of the anti-aliasing render, configuration, or compatibility flow.
     * @param preset quality preset selected by the user or loaded from config
     * @return text component shown to the player
     */
    public static Component label(NisUpscaleQualityPreset preset) {
        return org.betterLostItems.salts_anti_aliasing.client.gui.legacy.LegacyComponents.translatable(preset.translationKey());
    }

    /**
     * Handles label as part of the anti-aliasing render, configuration, or compatibility flow.
     * @param preset DLSS quality preset selected by the user or loaded from config
     * @return text component shown to the player
     */
    public static Component label(DlssQualityPreset preset) {
        return org.betterLostItems.salts_anti_aliasing.client.gui.legacy.LegacyComponents.translatable(preset.translationKey());
    }

    public static Component label(org.betterLostItems.salts_anti_aliasing.client.config.XessQualityPreset preset) {
        return org.betterLostItems.salts_anti_aliasing.client.gui.legacy.LegacyComponents.translatable(preset.translationKey());
    }

    public static Component label(org.betterLostItems.salts_anti_aliasing.client.config.SgsrQualityPreset preset) {
        return org.betterLostItems.salts_anti_aliasing.client.gui.legacy.LegacyComponents.translatable(preset.translationKey());
    }

    public static Component label(FsrQualityPreset preset) {
        return org.betterLostItems.salts_anti_aliasing.client.gui.legacy.LegacyComponents.translatable(preset.translationKey());
    }

    /**
     * Handles label as part of the anti-aliasing render, configuration, or compatibility flow.
     * @param level SSAA scale selected by the user or loaded from config
     * @return text component shown to the player
     */
    public static Component label(SsaaScaleLevel level) {
        Component label = org.betterLostItems.salts_anti_aliasing.client.gui.legacy.LegacyComponents.literal(level.label());
        return level.requiresPerformanceWarning()
                ? org.betterLostItems.salts_anti_aliasing.client.gui.legacy.LegacyComponents.translatable(SSAA_WARNING_LABEL_KEY, label)
                : label;
    }

    /**
     * Builds the SSAA tooltip, including a selected-value warning above 400%.
     * @param level SSAA scale selected by the user or loaded from config
     * @return localized tooltip for the selected scale
     */
    public static Component ssaaScaleTooltip(SsaaScaleLevel level) {
        Component tooltip = org.betterLostItems.salts_anti_aliasing.client.gui.legacy.LegacyComponents.translatable(SSAA_TOOLTIP_KEY);
        if (!level.requiresPerformanceWarning()) {
            return tooltip;
        }

        return tooltip.copy()
                .append(org.betterLostItems.salts_anti_aliasing.client.gui.legacy.LegacyComponents.literal("\n\n"))
                .append(org.betterLostItems.salts_anti_aliasing.client.gui.legacy.LegacyComponents.translatable(
                        SSAA_WARNING_KEY,
                        level.percentage() + "%",
                        Math.round(level.pixelMultiplier()) + "x"
                ));
    }
}
