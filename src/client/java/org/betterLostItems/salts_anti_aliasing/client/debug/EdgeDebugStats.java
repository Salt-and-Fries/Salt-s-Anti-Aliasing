package org.betterLostItems.salts_anti_aliasing.client.debug;

import org.betterLostItems.salts_anti_aliasing.client.config.AntiAliasingMode;

/**
 * Documents edge debug stats behavior for Salt's Anti Aliasing. Debug instrumentation for visualizing
 * and measuring aliasing behavior.
 */
public record EdgeDebugStats(
        AntiAliasingMode mode,
        float edgeCoverage,
        float harshEdgeRatio,
        float smoothTransitionRatio,
        int qualityScore
) {
    /**
     * Coordinates unavailable within the anti-aliasing render, configuration, or compatibility flow.
     * @param mode requested anti-aliasing mode
     * @return unavailable value produced or selected by this code path
     */
    public static EdgeDebugStats unavailable(AntiAliasingMode mode) {
        return new EdgeDebugStats(mode, 0.0f, 0.0f, 0.0f, -1);
    }

    /**
     * Coordinates available within the anti-aliasing render, configuration, or compatibility flow.
     * @return available value produced or selected by this code path
     */
    public boolean available() {
        return qualityScore >= 0;
    }

    /**
     * Coordinates verdict within the anti-aliasing render, configuration, or compatibility flow.
     * @return verdict value produced or selected by this code path
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
