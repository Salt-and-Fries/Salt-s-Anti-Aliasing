package org.betterLostItems.salts_anti_aliasing.client.render.api;

/**
 * Documents history state behavior for Salt's Anti Aliasing. Backend-neutral rendering API shared by
 * the planner and backend implementations.
 */
public record HistoryState(boolean valid, int accumulatedFrames) {
    /**
     * Coordinates invalid within the anti-aliasing render, configuration, or compatibility flow.
     * @return invalid value produced or selected by this code path
     */
    public static HistoryState invalid() {
        return new HistoryState(false, 0);
    }
}
