package org.betterLostItems.salts_anti_aliasing.client.config;

import com.google.gson.annotations.SerializedName;

/**
 * Mutable configuration object persisted to disk and copied before render code reads it, keeping
 * live edits isolated from stored defaults.
 */
public final class AntiAliasingConfig {
    public static final int CURRENT_CONFIG_VERSION = 2;
    public static final float MIN_SHARPEN_STRENGTH = 0.0f;
    public static final float MAX_SHARPEN_STRENGTH = 1.0f;
    public static final float DEFAULT_SHARPEN_STRENGTH = 0.0f;
    public static final boolean DEFAULT_MSAA_ALPHA_TO_COVERAGE = false;
    private static final float LEGACY_DEFAULT_SHARPEN_STRENGTH = 0.25f;

    public Integer configVersion;
    public AntiAliasingMode mode = AntiAliasingMode.OFF;
    public QualityPreset qualityPreset = QualityPreset.MEDIUM;
    public float sharpenStrength = DEFAULT_SHARPEN_STRENGTH;
    public MsaaSampleLevel msaaSampleLevel = MsaaSampleLevel.defaultLevel();
    public boolean msaaAlphaToCoverage = DEFAULT_MSAA_ALPHA_TO_COVERAGE;
    public SsaaScaleLevel ssaaScaleLevel = SsaaScaleLevel.defaultLevel();
    public NisUpscaleQualityPreset nisUpscaleQualityPreset = NisUpscaleQualityPreset.defaultPreset();
    public DlssQualityPreset dlssQualityPreset = DlssQualityPreset.defaultPreset();
    public FsrQualityPreset fsrQualityPreset = FsrQualityPreset.defaultPreset();
    public float internalResolutionScale = 1.0f;
    @SerializedName("fsrSharpness")
    private Float legacyFsrSharpness;
    public boolean keepHudAtNativeResolution = true;
    public boolean debugViewsEnabled = false;
    public boolean recordMetrics = false;
    public String dlssBridgePath = "";
    public String dlssPluginPath = "";
    public String dlssLogPath = "";
    public int dlssApplicationId = 0;
    public String fsrBridgePath = "";
    public String fsrRuntimePath = "";
    public String fsrLogPath = "";

    /**
     * Creates an independent mutable copy so callers can inspect or edit configuration without
     * mutating the live instance unexpectedly.
     * @return an independent copy of the current object
     */
    public AntiAliasingConfig copy() {
        AntiAliasingConfig copy = new AntiAliasingConfig();
        copy.configVersion = configVersion;
        copy.mode = mode;
        copy.qualityPreset = qualityPreset;
        copy.sharpenStrength = sharpenStrength;
        copy.msaaSampleLevel = msaaSampleLevel;
        copy.msaaAlphaToCoverage = msaaAlphaToCoverage;
        copy.ssaaScaleLevel = ssaaScaleLevel;
        copy.nisUpscaleQualityPreset = nisUpscaleQualityPreset;
        copy.dlssQualityPreset = dlssQualityPreset;
        copy.fsrQualityPreset = fsrQualityPreset;
        copy.internalResolutionScale = internalResolutionScale;
        copy.keepHudAtNativeResolution = keepHudAtNativeResolution;
        copy.debugViewsEnabled = debugViewsEnabled;
        copy.recordMetrics = recordMetrics;
        copy.dlssBridgePath = dlssBridgePath;
        copy.dlssPluginPath = dlssPluginPath;
        copy.dlssLogPath = dlssLogPath;
        copy.dlssApplicationId = dlssApplicationId;
        copy.fsrBridgePath = fsrBridgePath;
        copy.fsrRuntimePath = fsrRuntimePath;
        copy.fsrLogPath = fsrLogPath;
        return copy;
    }

    /**
     * Normalizes deserialized or edited values so invalid config cannot leak into render-target
     * sizing or pass planning.
     */
    public void sanitize() {
        migrateLegacyConfig();
        mode = AntiAliasingMode.clampImplemented(mode);
        if (qualityPreset == null) {
            qualityPreset = QualityPreset.MEDIUM;
        }
        msaaSampleLevel = MsaaSampleLevel.clamp(msaaSampleLevel);
        ssaaScaleLevel = SsaaScaleLevel.clamp(ssaaScaleLevel);
        nisUpscaleQualityPreset = NisUpscaleQualityPreset.clamp(nisUpscaleQualityPreset);
        dlssQualityPreset = DlssQualityPreset.clamp(dlssQualityPreset);
        fsrQualityPreset = FsrQualityPreset.clamp(fsrQualityPreset);
        keepHudAtNativeResolution = true;
        sharpenStrength = clamp(sharpenStrength, MIN_SHARPEN_STRENGTH, MAX_SHARPEN_STRENGTH);
        internalResolutionScale = clamp(internalResolutionScale, 0.5f, 1.0f);
        dlssBridgePath = sanitizePath(dlssBridgePath);
        dlssPluginPath = sanitizePath(dlssPluginPath);
        dlssLogPath = sanitizePath(dlssLogPath);
        dlssApplicationId = Math.max(0, dlssApplicationId);
        fsrBridgePath = sanitizePath(fsrBridgePath);
        fsrRuntimePath = sanitizePath(fsrRuntimePath);
        fsrLogPath = sanitizePath(fsrLogPath);
        legacyFsrSharpness = null;
        configVersion = CURRENT_CONFIG_VERSION;
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
            case DLSS_SUPER_RESOLUTION -> dlssQualityPreset == DlssQualityPreset.ULTRA_PERFORMANCE ? 0.33f : 0.5f;
            case FSR2_SUPER_RESOLUTION, FSR3_SUPER_RESOLUTION, FSR3_SUPER_RESOLUTION_FRAME_GENERATION ->
                    fsrQualityPreset.scaleFactor();
            case NIS_UPSCALE, FSR1_UPSCALE, FSR1_RCAS -> nisUpscaleQualityPreset.scaleFactor();
            default -> internalResolutionScale;
        };
    }

    boolean needsMigration() {
        return configVersion == null || configVersion < CURRENT_CONFIG_VERSION;
    }

    private void migrateLegacyConfig() {
        if (!needsMigration()) {
            mode = AntiAliasingMode.migrateLegacy(mode);
            return;
        }

        AntiAliasingMode legacyMode = mode;
        if (legacyMode != null && legacyMode.usesFsrQualityControl()) {
            sharpenStrength = legacyFsrSharpness == null
                    ? DEFAULT_SHARPEN_STRENGTH
                    : legacyFsrSharpness;
        } else if (legacyMode != AntiAliasingMode.NIS_SHARPEN
                && legacyMode != AntiAliasingMode.SMAA_NIS_SHARPEN
                && legacyMode != AntiAliasingMode.FSR1_RCAS
                && Float.compare(sharpenStrength, LEGACY_DEFAULT_SHARPEN_STRENGTH) == 0) {
            // The old untouched 25% general default was inactive for these modes, not a universal choice.
            sharpenStrength = DEFAULT_SHARPEN_STRENGTH;
        }

        mode = AntiAliasingMode.migrateLegacy(legacyMode);
    }

    /**
     * Clamps the supplied value to an inclusive range before it can affect rendering or persisted configuration.
     * @param value value supplied by the caller or Minecraft callback
     * @param min min value supplied by the caller or Minecraft callback
     * @param max max value supplied by the caller or Minecraft callback
     * @return value clamped to the supported range
     */
    private static float clamp(float value, float min, float max) {
        if (Float.isNaN(value)) {
            return min;
        }
        return Math.max(min, Math.min(max, value));
    }

    private static String sanitizePath(String value) {
        return value == null ? "" : value.trim();
    }
}
