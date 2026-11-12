package org.betterLostItems.salts_anti_aliasing.client.debug;

import org.betterLostItems.salts_anti_aliasing.client.config.AntiAliasingMode;

/**
 * Immutable snapshot of edge-debug measurements from the latest analyzed frame.
 */
public record EdgeDebugStats(
        AntiAliasingMode mode,
        float edgeCoverage,
        float harshEdgeRatio,
        float smoothTransitionRatio,
        int qualityScore
) {
    /**
     * Handles unavailable as part of the anti-aliasing render, configuration, or compatibility
     * flow.
     * @param mode anti-aliasing mode requested by UI, hotkey, or loaded config
     * @return empty debug snapshot used before analysis has produced real values
     */
    public static EdgeDebugStats unavailable(AntiAliasingMode mode) {
        return new EdgeDebugStats(mode, 0.0f, 0.0f, 0.0f, -1);
    }

    /**
     * Handles available as part of the anti-aliasing render, configuration, or compatibility flow.
     * @return whether the operation or state is enabled
     */
    public boolean available() {
        return qualityScore >= 0;
    }

    /**
     * Handles verdict as part of the anti-aliasing render, configuration, or compatibility flow.
     * @return human-readable debug verdict for the sampled edge statistics
     */
    public String verdict() {
        if (!available()) {
            return "Analyzing";
        }

        if (qualityScore >= 85) {
            return "Excellent";
        }
        if (qualityScore >= 70) {
            return "Good";
        }
        if (qualityScore >= 55) {
            return "Fair";
        }
        if (qualityScore >= 40) {
            return "Rough";
        }
        return "Harsh";
    }
}
