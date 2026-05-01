package org.betterLostItems.salts_anti_aliasing.client.config;

/**
 * Documents quality preset behavior for Salt's Anti Aliasing. Configuration model code that keeps
 * saved settings valid before render code reads them.
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
     * Coordinates display name within the anti-aliasing render, configuration, or compatibility flow.
     * @return display name value produced or selected by this code path
     */
    public String displayName() {
        return displayName;
    }
}
