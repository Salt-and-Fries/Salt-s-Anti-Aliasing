package org.betterLostItems.salts_anti_aliasing.client.config;

import java.util.Locale;

/**
 * Version-independent spatial upscale quality presets.
 *
 * <p>The preset stores only render-scale and sharpening numbers. Minecraft text
 * is created by the client UI adapter so this enum can be reused by other jar
 * families without pulling Minecraft UI classes into the shared config model.</p>
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

    /**
     * Handles scale factor as part of the anti-aliasing render, configuration, or compatibility
     * flow.
     * @return scene resolution multiplier represented by this preset
     */
    public float scaleFactor() {
        return scaleFactor;
    }

    /**
     * Handles sharpen strength as part of the anti-aliasing render, configuration, or compatibility
     * flow.
     * @return sharpening strength associated with the selected preset
     */
    public float sharpenStrength() {
        return sharpenStrength;
    }

    /**
     * Handles translation key as part of the anti-aliasing render, configuration, or compatibility
     * flow.
     * @return translation key consumed by Minecraft language resources
     */
    public String translationKey() {
        return "options.salts_anti_aliasing.nis_upscale_quality." + name().toLowerCase(Locale.ROOT);
    }

    /**
     * Handles default preset as part of the anti-aliasing render, configuration, or compatibility
     * flow.
     * @return default upscale preset used when config omits or loses this value
     */
    public static NisUpscaleQualityPreset defaultPreset() {
        return BALANCED;
    }

    /**
     * Clamps the supplied value to an inclusive range before it can affect rendering or persisted configuration.
     * @param preset quality preset selected by the user or loaded from config
     * @return value clamped to the supported range
     */
    public static NisUpscaleQualityPreset clamp(NisUpscaleQualityPreset preset) {
        return preset == null ? defaultPreset() : preset;
    }
}
