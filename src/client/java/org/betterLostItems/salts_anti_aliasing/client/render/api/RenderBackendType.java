package org.betterLostItems.salts_anti_aliasing.client.render.api;

/**
 * Documents render backend type behavior for Salt's Anti Aliasing. Backend-neutral rendering API
 * shared by the planner and backend implementations.
 */
public enum RenderBackendType {
    OPENGL("OpenGL"),
    VULKAN("Vulkan");

    private final String displayName;

    RenderBackendType(String displayName) {
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
