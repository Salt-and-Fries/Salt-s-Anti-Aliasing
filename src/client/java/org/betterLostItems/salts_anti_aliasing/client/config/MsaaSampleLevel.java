package org.betterLostItems.salts_anti_aliasing.client.config;

/**
 * Documents msaa sample level behavior for Salt's Anti Aliasing. Configuration model code that keeps
 * saved settings valid before render code reads them.
 */
public enum MsaaSampleLevel {
    X2(2),
    X4(4),
    X8(8),
    X16(16);

    private final int samples;

    MsaaSampleLevel(int samples) {
        this.samples = samples;
    }

    /**
     * Coordinates samples within the anti-aliasing render, configuration, or compatibility flow.
     * @return samples value produced or selected by this code path
     */
    public int samples() {
        return samples;
    }

    /**
     * Coordinates label within the anti-aliasing render, configuration, or compatibility flow.
     * @return label value produced or selected by this code path
     */
    public String label() {
        return samples + "x";
    }

    /**
     * Coordinates default level within the anti-aliasing render, configuration, or compatibility flow.
     * @return default level value produced or selected by this code path
     */
    public static MsaaSampleLevel defaultLevel() {
        return X4;
    }

    /**
     * Clamps the supplied value to the supported range before it can affect rendering or persisted
     * configuration.
     * @param level level supplied by Minecraft or the caller
     * @return clamp value produced or selected by this code path
     */
    public static MsaaSampleLevel clamp(MsaaSampleLevel level) {
        return level == null ? defaultLevel() : level;
    }

    /**
     * Coordinates best supported within the anti-aliasing render, configuration, or compatibility
     * flow.
     * @param requested requested supplied by Minecraft or the caller
     * @param maxSupportedSamples max supported samples supplied by Minecraft or the caller
     * @return best supported value produced or selected by this code path
     */
    public static MsaaSampleLevel bestSupported(MsaaSampleLevel requested, int maxSupportedSamples) {
        MsaaSampleLevel clampedRequested = clamp(requested);
        MsaaSampleLevel bestLevel = null;

        for (MsaaSampleLevel level : values()) {
            if (level.samples <= maxSupportedSamples) {
                bestLevel = level;
            }
            if (level == clampedRequested) {
                break;
            }
        }

        return bestLevel;
    }
}
