package org.betterLostItems.salts_anti_aliasing.client.config;

/**
 * Mutable configuration object persisted to disk and copied before render code reads it, keeping
 * live edits isolated from stored defaults.
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
     * Creates an independent mutable copy so callers can inspect or edit configuration without
     * mutating the live instance unexpectedly.
     * @return an independent copy of the current object
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
     * Normalizes deserialized or edited values so invalid config cannot leak into render-target
     * sizing or pass planning.
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
     * Reports whether the current mode needs a separate internal-resolution render target before
     * presenting to the native output.
     * @return whether this object requires the described render path
     */
    public boolean usesInternalResolutionPath() {
        return mode == AntiAliasingMode.SSAA || mode.usesDedicatedUpscalePass();
    }

    /**
     * Returns the resolution multiplier used for the 3D scene before final resolve or upscale
     * passes run.
     * @return render-scale multiplier used for the 3D scene
     */
    public float sceneRenderScale() {
        return switch (mode) {
            case SSAA -> ssaaScaleLevel.scaleFactor();
            case NIS_UPSCALE, FSR1_UPSCALE, FSR1_RCAS -> nisUpscaleQualityPreset.scaleFactor();
            default -> internalResolutionScale;
        };
    }

    /**
     * Clamps the supplied value to an inclusive range before it can affect rendering or persisted configuration.
     * @param value value supplied by the caller or Minecraft callback
     * @param min min value supplied by the caller or Minecraft callback
     * @param max max value supplied by the caller or Minecraft callback
     * @return value clamped to the supported range
     */
    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
