package org.betterLostItems.salts_anti_aliasing.client.render.api;

/**
 * Immutable value object carrying history state data between render-planning and runtime code.
 * Backend-neutral render API code shared by OpenGL, Vulkan placeholders, and pipeline planning.
 */
public record HistoryState(boolean valid, int accumulatedFrames) {
    /**
     * Handles invalid as part of the anti-aliasing render, configuration, or compatibility flow.
     * @return history state that forces temporal consumers to ignore previous-frame data
     */
    public static HistoryState invalid() {
        return new HistoryState(false, 0);
    }
}
