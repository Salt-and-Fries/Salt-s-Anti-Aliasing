package org.betterLostItems.salts_anti_aliasing.client.config;

import java.util.Locale;

/**
 * Shared quality presets for spatial upscaling paths.
 *
 * <p>The scale and default sharpening values are core tuning data. Minecraft-facing
 * presentation, such as translated labels, belongs in the UI layer.</p>
 */
public enum NisUpscaleQualityPreset {
    QUALITY(0.77f, 0.16f),
    BALANCED(0.67f, 0.22f),
    PERFORMANCE(0.59f, 0.28f),
    ULTRA_PERFORMANCE(0.50f, 0.34f);

    private final float scaleFactor;
    private final float sharpenStrength;

    NisUpscaleQualityPreset(float scaleFactor, float sharpenStrength) {
        this.scaleFactor = scaleFactor;
        this.sharpenStrength = sharpenStrength;
    }

    public float scaleFactor() {
        return scaleFactor;
    }

    public float sharpenStrength() {
        return sharpenStrength;
    }

    /**
     * Translation key used by the client UI layer.
     */
    public String translationKey() {
        return "options.salts_anti_aliasing.nis_upscale_quality." + name().toLowerCase(Locale.ROOT);
    }

    public static NisUpscaleQualityPreset defaultPreset() {
        return BALANCED;
    }

    public static NisUpscaleQualityPreset clamp(NisUpscaleQualityPreset preset) {
        return preset == null ? defaultPreset() : preset;
    }
}
