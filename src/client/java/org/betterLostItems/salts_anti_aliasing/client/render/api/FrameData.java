package org.betterLostItems.salts_anti_aliasing.client.render.api;

/**
 * Documents frame data behavior for Salt's Anti Aliasing. Backend-neutral rendering API shared by the
 * planner and backend implementations.
 */
public record FrameData(
        RenderResolution sceneResolution,
        RenderResolution outputResolution,
        JitterOffset jitter,
        HistoryState historyState,
        float exposure
) {
    /**
     * Coordinates bootstrap within the anti-aliasing render, configuration, or compatibility flow.
     * @param outputResolution output resolution supplied by Minecraft or the caller
     * @return bootstrap value produced or selected by this code path
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
