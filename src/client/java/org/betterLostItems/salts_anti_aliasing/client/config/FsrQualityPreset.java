package org.betterLostItems.salts_anti_aliasing.client.config;

import java.util.Locale;

/**
 * User-facing AMD FSR temporal upscaling quality presets.
 */
public enum FsrQualityPreset {
    NATIVE_AA("Native AA", 1.0f),
    QUALITY("Quality", 1.0f / 1.5f),
    BALANCED("Balanced", 1.0f / 1.7f),
    PERFORMANCE("Performance", 0.5f),
    ULTRA_PERFORMANCE("Ultra Performance", 1.0f / 3.0f);

    private final String displayName;
    private final float scaleFactor;

    FsrQualityPreset(String displayName, float scaleFactor) {
        this.displayName = displayName;
        this.scaleFactor = scaleFactor;
    }

    public String displayName() {
        return displayName;
    }

    public float scaleFactor() {
        return scaleFactor;
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
