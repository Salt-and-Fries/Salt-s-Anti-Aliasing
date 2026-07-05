package org.betterLostItems.salts_anti_aliasing.client.config;

import java.util.List;
import java.util.Locale;

/**
 * Version-independent list of anti-aliasing and scaling modes.
 *
 * <p>This enum is deliberately plain data: no Minecraft UI classes, no Fabric classes,
 * and no renderer-specific handles. Every version jar should be able to share these
 * mode semantics and then map them to its own render adapter.</p>
 */
public enum AntiAliasingMode {
    OFF("Off", false, false),
    FXAA("FXAA", false, false),
    MSAA("MSAA", false, false),
    SSAA("SSAA", false, false),
    SMAA("SMAA", false, false),
    TAA("TAA", false, true),
    NIS_SHARPEN("NIS Sharpen", false, false),
    NIS_UPSCALE("NIS Upscale", true, false),
    DLSS_SUPER_RESOLUTION("DLSS Super Resolution", true, true),
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
            DLSS_SUPER_RESOLUTION,
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

    /**
     * Handles display name as part of the anti-aliasing render, configuration, or compatibility
     * flow.
     * @return display label shown in configuration UI and debug text
     */
    public String displayName() {
        return displayName;
    }

    /**
     * Checks uses dedicated upscale pass without mutating runtime or configuration state.
     * @return whether this object requires the described render path
     */
    public boolean usesDedicatedUpscalePass() {
        return dedicatedUpscale;
    }

    /**
     * Checks uses history buffers without mutating runtime or configuration state.
     * @return whether this object requires the described render path
     */
    public boolean usesHistoryBuffers() {
        return historyAware;
    }

    /**
     * Checks uses sharpen control without mutating runtime or configuration state.
     * @return whether this object requires the described render path
     */
    public boolean usesSharpenControl() {
        return this == NIS_SHARPEN || this == FSR1_RCAS;
    }

    /**
     * Checks uses msaa sample control without mutating runtime or configuration state.
     * @return whether this object requires the described render path
     */
    public boolean usesMsaaSampleControl() {
        return this == MSAA;
    }

    /**
     * Checks uses ssaa scale control without mutating runtime or configuration state.
     * @return whether this object requires the described render path
     */
    public boolean usesSsaaScaleControl() {
        return this == SSAA;
    }

    /**
     * Checks uses spatial upscale quality control without mutating runtime or configuration state.
     * @return whether this object requires the described render path
     */
    public boolean usesSpatialUpscaleQualityControl() {
        return this == NIS_UPSCALE || this == FSR1_UPSCALE || this == FSR1_RCAS;
    }

    /**
     * Checks uses dlss quality control without mutating runtime or configuration state.
     * @return whether this object requires the described render path
     */
    public boolean usesDlssQualityControl() {
        return this == DLSS_SUPER_RESOLUTION;
    }

    /**
     * Handles next as part of the anti-aliasing render, configuration, or compatibility flow.
     * @return next mode in declared enum order
     */
    public AntiAliasingMode next() {
        AntiAliasingMode[] modes = values();
        return modes[(ordinal() + 1) % modes.length];
    }

    /**
     * Handles next implemented as part of the anti-aliasing render, configuration, or compatibility
     * flow.
     * @return next mode that is implemented by this build
     */
    public AntiAliasingMode nextImplemented() {
        int currentIndex = IMPLEMENTED_MODES.indexOf(clampImplemented(this));
        return IMPLEMENTED_MODES.get((currentIndex + 1) % IMPLEMENTED_MODES.size());
    }

    /**
     * Translation key used by the client UI layer.
     */
    public String translationKey() {
        return "options.salts_anti_aliasing.mode." + name().toLowerCase(Locale.ROOT);
    }

    /**
     * Coordinates implemented modes within the anti-aliasing render, configuration, or compatibility flow.
     * @return ordered list of modes exposed by this build
     */
    public static List<AntiAliasingMode> implementedModes() {
        return IMPLEMENTED_MODES;
    }

    /**
     * Clamps implemented to the supported range before it can affect rendering.
     * @param mode anti-aliasing mode requested by UI, hotkey, or loaded config
     * @return implemented mode closest to the requested value
     */
    public static AntiAliasingMode clampImplemented(AntiAliasingMode mode) {
        if (mode == null || !IMPLEMENTED_MODES.contains(mode)) {
            return OFF;
        }

        return mode;
    }
}
