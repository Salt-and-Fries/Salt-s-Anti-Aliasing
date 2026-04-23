package org.betterLostItems.salts_anti_aliasing.client.config;

public enum MsaaSampleLevel {
    X2(2),
    X4(4),
    X8(8),
    X16(16);

    private final int samples;

    MsaaSampleLevel(int samples) {
        this.samples = samples;
    }

    public int samples() {
        return samples;
    }

    public String label() {
        return samples + "x";
    }

    public static MsaaSampleLevel defaultLevel() {
        return X4;
    }

    public static MsaaSampleLevel clamp(MsaaSampleLevel level) {
        return level == null ? defaultLevel() : level;
    }

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
