package org.betterLostItems.salts_anti_aliasing.client.debug;

import org.betterLostItems.salts_anti_aliasing.client.config.AntiAliasingMode;

public record EdgeDebugStats(
        AntiAliasingMode mode,
        float edgeCoverage,
        float harshEdgeRatio,
        float smoothTransitionRatio,
        int qualityScore
) {
    public static EdgeDebugStats unavailable(AntiAliasingMode mode) {
        return new EdgeDebugStats(mode, 0.0f, 0.0f, 0.0f, -1);
    }

    public boolean available() {
        return qualityScore >= 0;
    }

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
