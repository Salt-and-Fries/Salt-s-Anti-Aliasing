package org.betterLostItems.salts_anti_aliasing.client.config;

/**
 * Documents ssaa scale level behavior for Salt's Anti Aliasing. Configuration model code that keeps
 * saved settings valid before render code reads them.
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
     * Coordinates scale factor within the anti-aliasing render, configuration, or compatibility flow.
     * @return scale factor value produced or selected by this code path
     */
    public float scaleFactor() {
        return scaleFactor;
    }

    /**
     * Coordinates label within the anti-aliasing render, configuration, or compatibility flow.
     * @return label value produced or selected by this code path
     */
    public String label() {
        return Math.round(scaleFactor * 100.0f) + "%";
    }

    /**
     * Coordinates default level within the anti-aliasing render, configuration, or compatibility flow.
     * @return default level value produced or selected by this code path
     */
    public static SsaaScaleLevel defaultLevel() {
        return X125;
    }

    /**
     * Clamps the supplied value to the supported range before it can affect rendering or persisted
     * configuration.
     * @param level level supplied by Minecraft or the caller
     * @return clamp value produced or selected by this code path
     */
    public static SsaaScaleLevel clamp(SsaaScaleLevel level) {
        return level == null ? defaultLevel() : level;
    }
}
