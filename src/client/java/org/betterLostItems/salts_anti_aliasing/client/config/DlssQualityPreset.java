package org.betterLostItems.salts_anti_aliasing.client.config;

import java.util.Locale;

/**
 * User-facing DLSS Super Resolution quality presets. The native Streamline bridge maps these
 * directly to sl::DLSSMode values and queries NVIDIA's optimal render size for the active output.
 */
public enum DlssQualityPreset {
    QUALITY("Quality"),
    BALANCED("Balanced"),
    PERFORMANCE("Performance"),
    ULTRA_PERFORMANCE("Ultra Performance"),
    AUTO("Auto");

    private final String displayName;

    DlssQualityPreset(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    public String translationKey() {
        return "options.salts_anti_aliasing.dlss_quality." + name().toLowerCase(Locale.ROOT);
    }

    public static DlssQualityPreset defaultPreset() {
        return QUALITY;
    }

    public static DlssQualityPreset clamp(DlssQualityPreset preset) {
        return preset == null ? defaultPreset() : preset;
    }
}
