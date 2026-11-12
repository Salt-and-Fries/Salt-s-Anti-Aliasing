package org.betterLostItems.salts_anti_aliasing.client.config;

import java.util.Locale;

/**
 * User-facing DLSS Super Resolution quality presets. The native Streamline bridge maps these
 * directly to sl::DLSSMode values and queries NVIDIA's optimal render size for the active output.
 */
public enum DlssQualityPreset {
    DLAA("DLAA (Native)", 5),
    QUALITY("Quality", 0),
    BALANCED("Balanced", 1),
    PERFORMANCE("Performance", 2),
    ULTRA_PERFORMANCE("Ultra Performance", 3),
    AUTO("Auto", 4);

    private final String displayName;
    private final int nativeId;

    DlssQualityPreset(String displayName, int nativeId) {
        this.displayName = displayName;
        this.nativeId = nativeId;
    }

    /** Stable bridge ID, independent of the slider's display order. */
    public int nativeId() { return nativeId; }

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
