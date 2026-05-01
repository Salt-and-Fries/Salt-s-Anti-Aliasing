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

    /**
     * Coordinates display name within the anti-aliasing render, configuration, or compatibility flow.
     * @return display name value produced or selected by this code path
     */
    public String displayName() {
        return displayName;
    }

    /**
     * Checks whether uses dedicated upscale pass without mutating configuration or render state.
     * @return uses dedicated upscale pass value produced or selected by this code path
     */
    public boolean usesDedicatedUpscalePass() {
        return dedicatedUpscale;
    }

    /**
     * Checks whether uses history buffers without mutating configuration or render state.
     * @return uses history buffers value produced or selected by this code path
     */
    public boolean usesHistoryBuffers() {
        return historyAware;
    }

    /**
     * Checks whether uses sharpen control without mutating configuration or render state.
     * @return uses sharpen control value produced or selected by this code path
     */
    public boolean usesSharpenControl() {
        return this == NIS_SHARPEN || this == FSR1_RCAS;
    }

    /**
     * Checks whether uses msaa sample control without mutating configuration or render state.
     * @return uses msaa sample control value produced or selected by this code path
     */
    public boolean usesMsaaSampleControl() {
        return this == MSAA;
    }

    /**
     * Checks whether uses ssaa scale control without mutating configuration or render state.
     * @return uses ssaa scale control value produced or selected by this code path
     */
    public boolean usesSsaaScaleControl() {
        return this == SSAA;
    }

    /**
     * Checks whether uses spatial upscale quality control without mutating configuration or render
     * state.
     * @return uses spatial upscale quality control value produced or selected by this code path
     */
    public boolean usesSpatialUpscaleQualityControl() {
        return this == NIS_UPSCALE || this == FSR1_UPSCALE || this == FSR1_RCAS;
    }

    /**
     * Coordinates next within the anti-aliasing render, configuration, or compatibility flow.
     * @return next value produced or selected by this code path
     */
    public AntiAliasingMode next() {
        AntiAliasingMode[] modes = values();
        return modes[(ordinal() + 1) % modes.length];
    }

    /**
     * Coordinates next implemented within the anti-aliasing render, configuration, or compatibility
     * flow.
     * @return next implemented value produced or selected by this code path
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
     * Coordinates implemented modes within the anti-aliasing render, configuration, or compatibility
     * flow.
     * @return implemented modes value produced or selected by this code path
     */
    public static List<AntiAliasingMode> implementedModes() {
        return IMPLEMENTED_MODES;
    }

    /**
     * Clamps the supplied value to the supported range before it can affect rendering or persisted
     * configuration.
     * @param mode requested anti-aliasing mode
     * @return clamp implemented value produced or selected by this code path
     */
    public static AntiAliasingMode clampImplemented(AntiAliasingMode mode) {
        if (mode == null || !IMPLEMENTED_MODES.contains(mode)) {
            return OFF;
        }

        return mode;
    }
}
