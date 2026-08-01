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
    X200(2.00f),
    X250(2.50f),
    X300(3.00f),
    X400(4.00f),
    X500(5.00f),
    X600(6.00f),
    X700(7.00f),
    X800(8.00f);

    private static final float PERFORMANCE_WARNING_THRESHOLD = 4.00f;

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
     * Returns the percentage shown in configuration controls.
     * @return per-axis render scale as a whole-number percentage
     */
    public int percentage() {
        return Math.round(scaleFactor * 100.0f);
    }

    /**
     * Returns the scene-pixel cost relative to native resolution.
     * @return number of rendered scene pixels per native-resolution pixel
     */
    public float pixelMultiplier() {
        return scaleFactor * scaleFactor;
    }

    /**
     * Reports whether this preset is above the recommended high-quality ceiling.
     * @return true when the UI should show the extreme performance warning
     */
    public boolean requiresPerformanceWarning() {
        return scaleFactor > PERFORMANCE_WARNING_THRESHOLD;
    }

    /**
     * Handles label as part of the anti-aliasing render, configuration, or compatibility flow.
     * @return text component shown to the player
     */
    public String label() {
        String scaleLabel = percentage() + "%";
        return this == X200 ? scaleLabel + " (4x SSAA)" : scaleLabel;
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
