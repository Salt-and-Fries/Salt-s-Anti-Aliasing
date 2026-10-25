package org.betterLostItems.salts_anti_aliasing.client.config;

/** Persisted, bounded tuning. Only settings consumed by the selected method are shown in the UI. */
public final class RenderTuning implements Cloneable {
    public DlssModelPreset dlssModel = DlssModelPreset.AUTO;
    public DlssColorInput dlssColorInput = DlssColorInput.AUTO;
    public float dlssParticleAlphaThreshold = .5f;
    public enum MotionVertexBudget {
        VERTICES_262K(262144), VERTICES_1M(1048576), VERTICES_2M(2097152);
        private final int vertices;
        MotionVertexBudget(int vertices) { this.vertices = vertices; }
        public int vertices() { return vertices; }
    }
    public MotionVertexBudget motionVertexBudget = MotionVertexBudget.VERTICES_1M;
    public enum MipBiasMode {
        OFF,
        AUTOMATIC,
        CUSTOM
    }

    public MipBiasMode mipBiasMode = MipBiasMode.OFF;
    public float mipBias = 0.0f;
    public float mipBiasOffset = 0.5f;

    public enum SmaaQuality {
        LOW,
        MEDIUM,
        HIGH,
        ULTRA
    }

    public enum SpatialAa {
        OFF,
        FXAA,
        SMAA
    }

    public enum HandAa {
        OFF(1), MSAA_2X(2), MSAA_4X(4), MSAA_8X(8);
        private final int samples;
        HandAa(int samples) { this.samples = samples; }
        public int samples() { return samples; }
    }

    public HandAa upscalerHandAa = HandAa.MSAA_4X;
    public boolean upscalerHandAlphaToCoverage = true;

    public enum SsaaFilter {
        AREA,
        TENT
    }

    public enum HistoryFilter {
        BILINEAR,
        CATMULL_ROM
    }

    public enum Sharpener {
        ADAPTIVE_CONTRAST,
        UNSHARP
    }

    public enum DebugView {
        NONE,
        EDGES,
        HISTORY_WEIGHT,
        MOTION_VECTORS,
        REACTIVE_MASK,
        DEPTH_CONFIDENCE,
        LUMA_CONFIDENCE,
        CLIPPING_CONFIDENCE,
        MOTION_CONFIDENCE
    }

    public float fxaaSubpixel = 0.5f;
    public float fxaaEdgeThreshold = 0.125f;
    public float fxaaMinContrast = 0.0312f;
    public SmaaQuality smaaQuality = SmaaQuality.ULTRA;
    public float smaaThreshold = 0.05f;
    public SpatialAa spatialAa = SpatialAa.SMAA;
    public SsaaFilter ssaaFilter = SsaaFilter.AREA;
    public boolean ssaaLinearLight = true;
    public float taaHistoryWeight = 0.90f;
    public float taaLumaRejection = 0.35f;
    public float taaDepthRejection = 1.0f;
    public float taaVarianceGamma = 1.5f;
    public int taaJitterPhases = 16;
    public HistoryFilter taaHistoryFilter = HistoryFilter.CATMULL_ROM;
    public boolean entityMotion = true;
    public float fsrCompositionStrength = .5f;
    public float fsrTransparencyThreshold = .05f;
    public float fsrReactiveScale = 5.0f;
    public float reactiveStrength = 1.0f;
    public float reactiveThreshold = 0.06f;
    public float reactiveMaximum = 0.9f;
    public Sharpener sharpener = Sharpener.ADAPTIVE_CONTRAST;
    public float sharpenLimit = 0.03f;
    public DebugView debugView = DebugView.NONE;

    public RenderTuning copy() {
        try {
            return (RenderTuning) super.clone();
        } catch (CloneNotSupportedException impossible) {
            throw new AssertionError(impossible);
        }
    }

    public void sanitize() {
        if (dlssColorInput == null) dlssColorInput = DlssColorInput.AUTO;
        if (motionVertexBudget == null) motionVertexBudget = MotionVertexBudget.VERTICES_1M;
        dlssParticleAlphaThreshold = finite(dlssParticleAlphaThreshold, .1f, 1, .5f);
        if (dlssModel == null) dlssModel = DlssModelPreset.AUTO;
        if (upscalerHandAa == null) upscalerHandAa = HandAa.MSAA_4X;
        if (mipBiasMode == null) mipBiasMode = MipBiasMode.OFF;
        mipBias = finite(mipBias, -3, 1, 0);
        mipBiasOffset = finite(mipBiasOffset, -1, 1, .5f);
        fxaaSubpixel = finite(fxaaSubpixel, 0, 1, 0.5f);
        fxaaEdgeThreshold = finite(fxaaEdgeThreshold, 0.0312f, 0.333f, 0.125f);
        fxaaMinContrast = finite(fxaaMinContrast, 0, 0.1f, 0.0312f);
        smaaThreshold = finite(smaaThreshold, 0.025f, 0.3f, 0.05f);
        taaHistoryWeight = finite(taaHistoryWeight, 0, 0.98f, 0.9f);
        taaLumaRejection = finite(taaLumaRejection, 0, 2, 0.35f);
        taaDepthRejection = finite(taaDepthRejection, 0.1f, 4, 1);
        taaVarianceGamma = finite(taaVarianceGamma, 0.5f, 3, 1.5f);
        taaJitterPhases = taaJitterPhases <= 8 ? 8 : taaJitterPhases <= 16 ? 16 : 32;
        fsrCompositionStrength = finite(fsrCompositionStrength, 0, 1, .5f);
        fsrTransparencyThreshold = finite(fsrTransparencyThreshold, .01f, .2f, .05f);
        fsrReactiveScale = finite(fsrReactiveScale, 0, 10, 5);
        reactiveStrength = finite(reactiveStrength, 0, 2, 1);
        reactiveThreshold = finite(reactiveThreshold, 0.01f, 0.25f, 0.06f);
        reactiveMaximum = finite(reactiveMaximum, 0, 1, 0.9f);
        sharpenLimit = finite(sharpenLimit, 0, 0.15f, 0.03f);
        if (smaaQuality == null) smaaQuality = SmaaQuality.ULTRA;
        if (spatialAa == null) spatialAa = SpatialAa.SMAA;
        if (ssaaFilter == null) ssaaFilter = SsaaFilter.AREA;
        if (taaHistoryFilter == null) taaHistoryFilter = HistoryFilter.CATMULL_ROM;
        if (sharpener == null) sharpener = Sharpener.ADAPTIVE_CONTRAST;
        if (debugView == null) debugView = DebugView.NONE;
    }

    private static float finite(float value, float min, float max, float fallback) {
        return Float.isFinite(value) ? Math.max(min, Math.min(max, value)) : fallback;
    }
}
