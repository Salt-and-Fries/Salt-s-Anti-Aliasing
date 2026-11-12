package org.betterLostItems.salts_anti_aliasing.client.config;

import java.util.Locale;

/** Scene scales used by Qualcomm's sample, plus native-resolution filtering. */
public enum SgsrQualityPreset {
    NATIVE(1.0f), QUALITY(1.0f / 1.5f), BALANCED(1.0f / 1.7f), PERFORMANCE(0.5f);
    private final float scale;
    SgsrQualityPreset(float scale) { this.scale = scale; }
    public float scaleFactor() { return scale; }
    public String translationKey() { return "options.salts_anti_aliasing.sgsr_quality." + name().toLowerCase(Locale.ROOT); }
    public static SgsrQualityPreset defaultPreset() { return QUALITY; }
    public static SgsrQualityPreset clamp(SgsrQualityPreset value) { return value == null ? defaultPreset() : value; }
    public int width(int output) { return Math.max(1, Math.round(output * scale)); }
}
