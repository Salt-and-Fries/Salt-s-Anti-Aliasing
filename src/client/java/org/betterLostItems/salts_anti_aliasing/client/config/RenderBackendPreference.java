package org.betterLostItems.salts_anti_aliasing.client.config;

/**
 * Enumerates render backend preference values used by the anti-aliasing runtime and configuration
 * UI. Configuration model code that keeps persisted anti-aliasing options normalized before render
 * code consumes them.
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
     * Handles display name as part of the anti-aliasing render, configuration, or compatibility
     * flow.
     * @return display label shown in configuration UI and debug text
     */
    public String displayName() {
        return displayName;
    }
}
