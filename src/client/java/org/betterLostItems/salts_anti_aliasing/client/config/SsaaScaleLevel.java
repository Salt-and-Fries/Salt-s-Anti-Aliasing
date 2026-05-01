package org.betterLostItems.salts_anti_aliasing.client.config;

/**
 * Enumerates ssaa scale level values used by the anti-aliasing runtime and configuration UI.
 * Configuration model code that keeps persisted anti-aliasing options normalized before render code
 * consumes them.
 */
public enum SsaaScaleLevel {
    X125(1.25f),
    X150(1.50f),
    X175(1.75f),
    X200(2.00f);

    private final float scaleFactor;

    SsaaScaleLevel(float scaleFactor) {
        this.scaleFactor = scaleFactor;
    }

    /**
     * Handles scale factor as part of the anti-aliasing render, configuration, or compatibility
     * flow.
     * @return scene resolution multiplier represented by this preset
     */
    public float scaleFactor() {
        return scaleFactor;
    }

    /**
     * Handles label as part of the anti-aliasing render, configuration, or compatibility flow.
     * @return text component shown to the player
     */
    public String label() {
        return Math.round(scaleFactor * 100.0f) + "%";
    }

    /**
     * Handles default level as part of the anti-aliasing render, configuration, or compatibility
     * flow.
     * @return default quality level used when config omits or loses this value
     */
    public static SsaaScaleLevel defaultLevel() {
        return X125;
    }

    /**
     * Clamps the supplied value to an inclusive range before it can affect rendering or persisted configuration.
     * @param level level value supplied by the caller or Minecraft callback
     * @return value clamped to the supported range
     */
    public static SsaaScaleLevel clamp(SsaaScaleLevel level) {
        return level == null ? defaultLevel() : level;
    }
}
