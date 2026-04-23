package org.betterLostItems.salts_anti_aliasing.client.config;

public enum RenderBackendPreference {
    AUTO("Auto"),
    OPENGL("OpenGL"),
    VULKAN("Vulkan");

    private final String displayName;

    RenderBackendPreference(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
