package org.betterLostItems.salts_anti_aliasing.client.config;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.ToDoubleFunction;

/** Shared UI ranges and applicability for the fields consumed by the renderers. */
public enum NumericTuningSetting {
    DLSS_PARTICLE_ALPHA_THRESHOLD(.1, 1, .05, t -> t.dlssParticleAlphaThreshold, (t, v) -> t.dlssParticleAlphaThreshold = v),
    MIP_BIAS(-3, 1, .25, t -> t.mipBias, (t, v) -> t.mipBias = v),
    MIP_BIAS_OFFSET(-1, 1, .25, t -> t.mipBiasOffset, (t, v) -> t.mipBiasOffset = v),
    FXAA_SUBPIXEL(0, 1, .01, t -> t.fxaaSubpixel, (t, v) -> t.fxaaSubpixel = v),
    FXAA_EDGE_THRESHOLD(
            .0312, .333, .005, t -> t.fxaaEdgeThreshold, (t, v) -> t.fxaaEdgeThreshold = v),
    FXAA_MIN_CONTRAST(0, .1, .002, t -> t.fxaaMinContrast, (t, v) -> t.fxaaMinContrast = v),
    SMAA_THRESHOLD(.025, .3, .005, t -> t.smaaThreshold, (t, v) -> t.smaaThreshold = v),
    TAA_HISTORY_WEIGHT(0, .98, .01, t -> t.taaHistoryWeight, (t, v) -> t.taaHistoryWeight = v),
    TAA_LUMA_REJECTION(0, 2, .05, t -> t.taaLumaRejection, (t, v) -> t.taaLumaRejection = v),
    TAA_DEPTH_REJECTION(.1, 4, .1, t -> t.taaDepthRejection, (t, v) -> t.taaDepthRejection = v),
    TAA_VARIANCE_GAMMA(.5, 3, .05, t -> t.taaVarianceGamma, (t, v) -> t.taaVarianceGamma = v),
    FSR_COMPOSITION_STRENGTH(
            0, 1, .05, t -> t.fsrCompositionStrength, (t, v) -> t.fsrCompositionStrength = v),
    FSR_TRANSPARENCY_THRESHOLD(
            .01,
            .2,
            .01,
            t -> t.fsrTransparencyThreshold,
            (t, v) -> t.fsrTransparencyThreshold = v),
    FSR_REACTIVE_SCALE(0, 10, .25, t -> t.fsrReactiveScale, (t, v) -> t.fsrReactiveScale = v),
    REACTIVE_STRENGTH(0, 2, .05, t -> t.reactiveStrength, (t, v) -> t.reactiveStrength = v),
    REACTIVE_THRESHOLD(.01, .25, .01, t -> t.reactiveThreshold, (t, v) -> t.reactiveThreshold = v),
    REACTIVE_MAXIMUM(0, 1, .01, t -> t.reactiveMaximum, (t, v) -> t.reactiveMaximum = v),
    SHARPEN_LIMIT(0, .15, .005, t -> t.sharpenLimit, (t, v) -> t.sharpenLimit = v);
    public final double min, max, step;
    private final ToDoubleFunction<RenderTuning> getter;
    private final BiConsumer<RenderTuning, Float> setter;

    NumericTuningSetting(
            double min,
            double max,
            double step,
            ToDoubleFunction<RenderTuning> getter,
            BiConsumer<RenderTuning, Float> setter) {
        this.min = min;
        this.max = max;
        this.step = step;
        this.getter = getter;
        this.setter = setter;
    }

    public String key() {
        return "options.salts_anti_aliasing.tuning." + name().toLowerCase(java.util.Locale.ROOT);
    }

    public double get(RenderTuning t) {
        return getter.applyAsDouble(t);
    }

    public void reset(RenderTuning target, RenderTuning defaults) {
        setter.accept(target, (float) getter.applyAsDouble(defaults));
    }

    public void set(RenderTuning t, double value) {
        setter.accept(
                t,
                (float) Math.max(min, Math.min(max, Math.rint((value - min) / step) * step + min)));
    }

    public static List<NumericTuningSetting> forMode(AntiAliasingMode mode, boolean advanced) {
        return switch (mode) {
            case FXAA ->
                    advanced
                            ? List.of(FXAA_SUBPIXEL, FXAA_EDGE_THRESHOLD, FXAA_MIN_CONTRAST)
                            : List.of(FXAA_SUBPIXEL);
            case SMAA -> advanced ? List.of(SMAA_THRESHOLD) : List.of();
            case TAA ->
                    advanced
                            ? List.of(
                                    TAA_HISTORY_WEIGHT,
                                    TAA_LUMA_REJECTION,
                                    TAA_DEPTH_REJECTION,
                                    TAA_VARIANCE_GAMMA)
                            : List.of(TAA_HISTORY_WEIGHT);
            // K/M/L do not support the legacy BiasCurrentColor input.
            case DLSS_SUPER_RESOLUTION -> advanced ? List.of(DLSS_PARTICLE_ALPHA_THRESHOLD) : List.of();
            case FSR2_SUPER_RESOLUTION,
                            FSR3_SUPER_RESOLUTION,
                            FSR3_SUPER_RESOLUTION_FRAME_GENERATION ->
                    advanced
                            ? List.of(
                                    REACTIVE_STRENGTH,
                                    REACTIVE_THRESHOLD,
                                    REACTIVE_MAXIMUM,
                                    FSR_COMPOSITION_STRENGTH,
                                    FSR_TRANSPARENCY_THRESHOLD,
                                    FSR_REACTIVE_SCALE)
                            : List.of();
            default -> List.of();
        };
    }
}
