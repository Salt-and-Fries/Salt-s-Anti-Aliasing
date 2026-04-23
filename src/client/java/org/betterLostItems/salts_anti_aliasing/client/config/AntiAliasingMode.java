package org.betterLostItems.salts_anti_aliasing.client.config;

import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Locale;

public enum AntiAliasingMode {
    OFF("Off", false, false),
    FXAA("FXAA", false, false),
    MSAA("MSAA", false, false),
    SSAA("SSAA", false, false),
    SMAA("SMAA", false, false),
    TAA("TAA", false, true),
    NIS_SHARPEN("NIS Sharpen", false, false),
    NIS_UPSCALE("NIS Upscale", true, false),
    FSR1_UPSCALE("FSR1 Upscale", true, false),
    FSR1_RCAS("FSR1 + RCAS", true, false);

    private static final List<AntiAliasingMode> IMPLEMENTED_MODES = List.of(
            OFF,
            NIS_SHARPEN,
            FXAA,
            MSAA,
            SSAA,
            SMAA,
            NIS_UPSCALE,
            FSR1_UPSCALE,
            FSR1_RCAS,
            TAA
    );

    private final String displayName;
    private final boolean dedicatedUpscale;
    private final boolean historyAware;

    AntiAliasingMode(String displayName, boolean dedicatedUpscale, boolean historyAware) {
        this.displayName = displayName;
        this.dedicatedUpscale = dedicatedUpscale;
        this.historyAware = historyAware;
    }

    public String displayName() {
        return displayName;
    }

    public boolean usesDedicatedUpscalePass() {
        return dedicatedUpscale;
    }

    public boolean usesHistoryBuffers() {
        return historyAware;
    }

    public boolean usesSharpenControl() {
        return this == NIS_SHARPEN || this == FSR1_RCAS;
    }

    public boolean usesMsaaSampleControl() {
        return this == MSAA;
    }

    public boolean usesSsaaScaleControl() {
        return this == SSAA;
    }

    public boolean usesSpatialUpscaleQualityControl() {
        return this == NIS_UPSCALE || this == FSR1_UPSCALE || this == FSR1_RCAS;
    }

    public AntiAliasingMode next() {
        AntiAliasingMode[] modes = values();
        return modes[(ordinal() + 1) % modes.length];
    }

    public AntiAliasingMode nextImplemented() {
        int currentIndex = IMPLEMENTED_MODES.indexOf(clampImplemented(this));
        return IMPLEMENTED_MODES.get((currentIndex + 1) % IMPLEMENTED_MODES.size());
    }

    public Component label() {
        return Component.translatable(translationKey());
    }

    public String translationKey() {
        return "options.salts_anti_aliasing.mode." + name().toLowerCase(Locale.ROOT);
    }

    public Component tooltipSummary() {
        return Component.translatable(translationKey() + ".summary");
    }

    public static List<AntiAliasingMode> implementedModes() {
        return IMPLEMENTED_MODES;
    }

    public static AntiAliasingMode clampImplemented(AntiAliasingMode mode) {
        if (mode == null || !IMPLEMENTED_MODES.contains(mode)) {
            return OFF;
        }

        return mode;
    }
}
