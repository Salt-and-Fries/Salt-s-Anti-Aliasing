package org.betterLostItems.salts_anti_aliasing.client.config;

import java.util.Locale;

/**
 * User-facing AMD FSR temporal upscaling quality presets.
 */
public enum FsrQualityPreset {
    NATIVE_AA("Native AA", 1.0f, 8),
    QUALITY("Quality", 1.0f / 1.5f, 18),
    BALANCED("Balanced", 1.0f / 1.7f, 23),
    PERFORMANCE("Performance", 0.5f, 32),
    ULTRA_PERFORMANCE("Ultra Performance", 1.0f / 3.0f, 72);

    private final String displayName;
    private final float scaleFactor;
    private final int jitterPhaseCount;

    FsrQualityPreset(String displayName, float scaleFactor, int jitterPhaseCount) {
        this.displayName = displayName;
        this.scaleFactor = scaleFactor;
        this.jitterPhaseCount = jitterPhaseCount;
    }

    public String displayName() {
        return displayName;
    }

    public float scaleFactor() {
        return scaleFactor;
    }

    /**
     * Returns the FidelityFX-recommended temporal sample sequence length for this preset.
     */
    public int jitterPhaseCount() {
        return jitterPhaseCount;
    }

    public String translationKey() {
        return "options.salts_anti_aliasing.fsr_quality." + name().toLowerCase(Locale.ROOT);
    }

    public static FsrQualityPreset defaultPreset() {
        return QUALITY;
    }

    public static FsrQualityPreset clamp(FsrQualityPreset preset) {
        return preset == null ? defaultPreset() : preset;
    }
}
