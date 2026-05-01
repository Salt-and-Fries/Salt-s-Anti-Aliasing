package org.betterLostItems.salts_anti_aliasing.client.config;

/**
 * Names broad quality presets that can be expanded into mode-specific render settings without
 * exposing every low-level knob.
 */
public enum QualityPreset {
    LOW("Low"),
    MEDIUM("Medium"),
    HIGH("High"),
    ULTRA("Ultra");

    private final String displayName;

    QualityPreset(String displayName) {
        this.displayName = displayName;
    }

    /**
     * Handles display name as part of the anti-aliasing render, configuration, or compatibility
     * flow.
     * @return display label shown in configuration UI and debug text
     */
    public String displayName() {
        return displayName;
    }
}
