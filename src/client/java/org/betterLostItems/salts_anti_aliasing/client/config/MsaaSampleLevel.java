package org.betterLostItems.salts_anti_aliasing.client.config;

/**
 * Enumerates msaa sample level values used by the anti-aliasing runtime and configuration UI.
 * Configuration model code that keeps persisted anti-aliasing options normalized before render code
 * consumes them.
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
     * Handles samples as part of the anti-aliasing render, configuration, or compatibility flow.
     * @return number of MSAA samples represented by this level
     */
    public int samples() {
        return samples;
    }

    /**
     * Handles label as part of the anti-aliasing render, configuration, or compatibility flow.
     * @return text component shown to the player
     */
    public String label() {
        return samples + "x";
    }

    /**
     * Handles default level as part of the anti-aliasing render, configuration, or compatibility
     * flow.
     * @return default quality level used when config omits or loses this value
     */
    public static MsaaSampleLevel defaultLevel() {
        return X4;
    }

    /**
     * Clamps the supplied value to an inclusive range before it can affect rendering or persisted configuration.
     * @param level level value supplied by the caller or Minecraft callback
     * @return value clamped to the supported range
     */
    public static MsaaSampleLevel clamp(MsaaSampleLevel level) {
        return level == null ? defaultLevel() : level;
    }

    /**
     * Handles best supported as part of the anti-aliasing render, configuration, or compatibility
     * flow.
     * @param requested requested value supplied by the caller or Minecraft callback
     * @param maxSupportedSamples max supported samples value supplied by the caller or Minecraft
     * callback
     * @return best supported produced by this helper
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
