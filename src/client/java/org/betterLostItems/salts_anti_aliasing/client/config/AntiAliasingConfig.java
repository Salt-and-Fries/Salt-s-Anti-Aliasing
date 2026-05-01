package org.betterLostItems.salts_anti_aliasing.client.config;

/**
 * Mutable persisted configuration for anti-aliasing, upscale, sharpening, metrics, and backend
 * preferences.
 */
public final class AntiAliasingConfig {
    public static final float MIN_SHARPEN_STRENGTH = 0.0f;
    public static final float MAX_SHARPEN_STRENGTH = 0.65f;
    public static final float DEFAULT_SHARPEN_STRENGTH = 0.25f;

    public AntiAliasingMode mode = AntiAliasingMode.OFF;
    public QualityPreset qualityPreset = QualityPreset.MEDIUM;
    public RenderBackendPreference preferredBackend = RenderBackendPreference.AUTO;
    public float sharpenStrength = DEFAULT_SHARPEN_STRENGTH;
    public MsaaSampleLevel msaaSampleLevel = MsaaSampleLevel.defaultLevel();
    public SsaaScaleLevel ssaaScaleLevel = SsaaScaleLevel.defaultLevel();
    public NisUpscaleQualityPreset nisUpscaleQualityPreset = NisUpscaleQualityPreset.defaultPreset();
    public float internalResolutionScale = 1.0f;
    public boolean keepHudAtNativeResolution = true;
    public boolean debugViewsEnabled = false;
    public boolean recordMetrics = false;
    public boolean allowExperimentalVulkan = false;

    /**
     * Coordinates copy within the anti-aliasing render, configuration, or compatibility flow.
     * @return copy value produced or selected by this code path
     */
    public AntiAliasingConfig copy() {
        AntiAliasingConfig copy = new AntiAliasingConfig();
        copy.mode = mode;
        copy.qualityPreset = qualityPreset;
        copy.preferredBackend = preferredBackend;
        copy.sharpenStrength = sharpenStrength;
        copy.msaaSampleLevel = msaaSampleLevel;
        copy.ssaaScaleLevel = ssaaScaleLevel;
        copy.nisUpscaleQualityPreset = nisUpscaleQualityPreset;
        copy.internalResolutionScale = internalResolutionScale;
        copy.keepHudAtNativeResolution = keepHudAtNativeResolution;
        copy.debugViewsEnabled = debugViewsEnabled;
        copy.recordMetrics = recordMetrics;
        copy.allowExperimentalVulkan = allowExperimentalVulkan;
        return copy;
    }

    /**
     * Coordinates sanitize within the anti-aliasing render, configuration, or compatibility flow.
     */
    public void sanitize() {
        mode = AntiAliasingMode.clampImplemented(mode);
        if (qualityPreset == null) {
            qualityPreset = QualityPreset.MEDIUM;
        }
        if (preferredBackend == null) {
            preferredBackend = RenderBackendPreference.AUTO;
        }

        msaaSampleLevel = MsaaSampleLevel.clamp(msaaSampleLevel);
        ssaaScaleLevel = SsaaScaleLevel.clamp(ssaaScaleLevel);
        nisUpscaleQualityPreset = NisUpscaleQualityPreset.clamp(nisUpscaleQualityPreset);
        keepHudAtNativeResolution = true;
        sharpenStrength = clamp(sharpenStrength, MIN_SHARPEN_STRENGTH, MAX_SHARPEN_STRENGTH);
        internalResolutionScale = clamp(internalResolutionScale, 0.5f, 1.0f);
    }

    /**
     * Checks whether uses internal resolution path without mutating configuration or render state.
     * @return uses internal resolution path value produced or selected by this code path
     */
    public boolean usesInternalResolutionPath() {
        return mode == AntiAliasingMode.SSAA || mode.usesDedicatedUpscalePass();
    }

    /**
     * Coordinates scene render scale within the anti-aliasing render, configuration, or compatibility
     * flow.
     * @return scene render scale value produced or selected by this code path
     */
    public float sceneRenderScale() {
        return switch (mode) {
            case SSAA -> ssaaScaleLevel.scaleFactor();
            case NIS_UPSCALE, FSR1_UPSCALE, FSR1_RCAS -> nisUpscaleQualityPreset.scaleFactor();
            default -> internalResolutionScale;
        };
    }

    /**
     * Clamps the supplied value to the supported range before it can affect rendering or persisted
     * configuration.
     * @param value value being transformed or clamped
     * @param min inclusive lower bound
     * @param max inclusive upper bound
     * @return clamp value produced or selected by this code path
     */
    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
