package org.betterLostItems.salts_anti_aliasing.client.render.api;

public record FrameData(
        RenderResolution sceneResolution,
        RenderResolution outputResolution,
        JitterOffset jitter,
        HistoryState historyState,
        float exposure
) {
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
