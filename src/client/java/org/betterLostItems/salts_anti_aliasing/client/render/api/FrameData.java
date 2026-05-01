package org.betterLostItems.salts_anti_aliasing.client.render.api;

/**
 * Immutable value object carrying frame data between render-planning and runtime code.
 * Backend-neutral render API code shared by OpenGL, Vulkan placeholders, and pipeline planning.
 */
public record FrameData(
        RenderResolution sceneResolution,
        RenderResolution outputResolution,
        JitterOffset jitter,
        HistoryState historyState,
        float exposure
) {
    /**
     * Builds the runtime from disk configuration and backend capabilities, then prepares the first
     * render pipeline before gameplay begins.
     * @param outputResolution output resolution value supplied by the caller or Minecraft callback
     * @return initial frame data with neutral jitter and invalid history
     */
    public static FrameData bootstrap(RenderResolution outputResolution) {
        return new FrameData(
                outputResolution,
                outputResolution,
                JitterOffset.none(),
                HistoryState.invalid(),
                1.0f
        );
    }
}
