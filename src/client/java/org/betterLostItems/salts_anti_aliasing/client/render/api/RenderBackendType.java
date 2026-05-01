package org.betterLostItems.salts_anti_aliasing.client.render.api;

/**
 * Enumerates render backend type values used by the anti-aliasing runtime and configuration UI.
 * Backend-neutral render API code shared by OpenGL, Vulkan placeholders, and pipeline planning.
 */
public enum RenderBackendType {
    OPENGL("OpenGL"),
    VULKAN("Vulkan");

    private final String displayName;

    RenderBackendType(String displayName) {
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
