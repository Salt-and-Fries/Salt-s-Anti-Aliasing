package org.betterLostItems.salts_anti_aliasing.client.render.api;

/**
 * Documents jitter offset behavior for Salt's Anti Aliasing. Backend-neutral rendering API shared by
 * the planner and backend implementations.
 */
public record JitterOffset(float x, float y) {
    /**
     * Coordinates none within the anti-aliasing render, configuration, or compatibility flow.
     * @return none value produced or selected by this code path
     */
    public static JitterOffset none() {
        return new JitterOffset(0.0f, 0.0f);
    }
}
