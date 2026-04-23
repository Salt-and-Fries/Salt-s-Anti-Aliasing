package org.betterLostItems.salts_anti_aliasing.client.render.api;

public enum RenderBackendType {
    OPENGL("OpenGL"),
    VULKAN("Vulkan");

    private final String displayName;

    RenderBackendType(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
