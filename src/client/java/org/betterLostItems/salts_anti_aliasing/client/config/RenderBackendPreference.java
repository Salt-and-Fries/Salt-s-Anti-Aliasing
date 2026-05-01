package org.betterLostItems.salts_anti_aliasing.client.config;

/**
 * Documents render backend preference behavior for Salt's Anti Aliasing. Configuration model code that
 * keeps saved settings valid before render code reads them.
 */
public enum RenderBackendPreference {
    AUTO("Auto"),
    OPENGL("OpenGL"),
    VULKAN("Vulkan");

    private final String displayName;

    RenderBackendPreference(String displayName) {
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
