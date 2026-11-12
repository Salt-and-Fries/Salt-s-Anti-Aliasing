package org.betterLostItems.salts_anti_aliasing.client.config;

import java.util.Locale;

/** Intel's public quality identifiers. Ratios are planning estimates; rendering queries the SDK. */
public enum XessQualityPreset {
    NATIVE_AA(106, 1.0f),
    ULTRA_QUALITY_PLUS(105, 1.0f / 1.3f),
    ULTRA_QUALITY(104, 1.0f / 1.5f),
    QUALITY(103, 1.0f / 1.7f),
    BALANCED(102, 0.5f),
    PERFORMANCE(101, 1.0f / 2.3f),
    ULTRA_PERFORMANCE(100, 1.0f / 3.0f);

    private final int sdkValue;
    private final float scale;
    XessQualityPreset(int sdkValue, float scale) { this.sdkValue = sdkValue; this.scale = scale; }
    public int sdkValue() { return sdkValue; }
    public float scaleFactor() { return scale; }
    public String translationKey() { return "options.salts_anti_aliasing.xess_quality." + name().toLowerCase(Locale.ROOT); }
    public static XessQualityPreset defaultPreset() { return QUALITY; }
    public static XessQualityPreset clamp(XessQualityPreset value) { return value == null ? defaultPreset() : value; }
}
