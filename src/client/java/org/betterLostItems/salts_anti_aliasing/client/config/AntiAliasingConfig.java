package org.betterLostItems.salts_anti_aliasing.client.config;

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

    public boolean usesInternalResolutionPath() {
        return mode == AntiAliasingMode.SSAA || mode.usesDedicatedUpscalePass();
    }

    public float sceneRenderScale() {
        return switch (mode) {
            case SSAA -> ssaaScaleLevel.scaleFactor();
            case NIS_UPSCALE, FSR1_UPSCALE, FSR1_RCAS -> nisUpscaleQualityPreset.scaleFactor();
            default -> internalResolutionScale;
        };
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
