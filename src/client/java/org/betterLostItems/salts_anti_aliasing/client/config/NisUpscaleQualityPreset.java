package org.betterLostItems.salts_anti_aliasing.client.config;

import net.minecraft.network.chat.Component;

import java.util.Locale;

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

    public Component label() {
        return Component.translatable(translationKey());
    }

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
