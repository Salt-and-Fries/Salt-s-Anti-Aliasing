package org.betterLostItems.salts_anti_aliasing.client.gui;

import net.minecraft.client.gui.components.*;
import net.minecraft.network.chat.Component;

import org.betterLostItems.salts_anti_aliasing.client.config.*;
import org.betterLostItems.salts_anti_aliasing.client.render.common.RenderRuntime;

import java.util.*;
import java.util.function.*;

/** Controls are selected by the active method, including its chosen spatial pre-AA method. */
final class MethodTuningControls {
    private MethodTuningControls() {}

    static void add(OptionsList list, RenderRuntime runtime, boolean advanced) {
        AntiAliasingConfig config = runtime.configSnapshot();
        AntiAliasingMode mode = config.mode;
        // The validated Streamline 2.14.1 / DLSS 310.9.1 runtime supports K/M/L on all RTX GPUs.
        // Older RTX cards run M/L more slowly; they must not be incorrectly filtered out.
        if (advanced && mode.usesDlssQualityControl()) {
            cycle(list, runtime, "dlss_model", DlssModelPreset.values(),
                    t -> t.dlssModel, (t, v) -> t.dlssModel = v);
            cycle(list, runtime, "dlss_color_input", DlssColorInput.values(),
                    t -> t.dlssColorInput, (t, v) -> t.dlssColorInput = v);
        }
        if (mode.usesDlssQualityControl() || mode.usesFsrQualityControl()
                || mode.usesSpatialUpscaleQualityControl()) {
            cycle(list, runtime, "upscaler_hand_aa", RenderTuning.HandAa.values(),
                    t -> t.upscalerHandAa, (t, v) -> t.upscalerHandAa = v);
            if (advanced && config.tuning.upscalerHandAa != RenderTuning.HandAa.OFF) {
                toggle(list, runtime, "hand_alpha_to_coverage", t -> t.upscalerHandAlphaToCoverage,
                        (t, v) -> t.upscalerHandAlphaToCoverage = v);
            }
        }
        if (mode == AntiAliasingMode.SSAA) {
            cycle(
                    list,
                    runtime,
                    "ssaa_filter",
                    RenderTuning.SsaaFilter.values(),
                    t -> t.ssaaFilter,
                    (t, v) -> t.ssaaFilter = v);
            toggle(
                    list,
                    runtime,
                    "ssaa_linear_light",
                    t -> t.ssaaLinearLight,
                    (t, v) -> t.ssaaLinearLight = v);
        }
        if (mode == AntiAliasingMode.SMAA) smaa(list, runtime);
        if (mode == AntiAliasingMode.NIS_UPSCALE || mode == AntiAliasingMode.FSR1_UPSCALE) {
            cycle(
                    list,
                    runtime,
                    "spatial_aa",
                    RenderTuning.SpatialAa.values(),
                    t -> t.spatialAa,
                    (t, v) -> t.spatialAa = v);
            if (config.tuning.spatialAa == RenderTuning.SpatialAa.SMAA) smaa(list, runtime);
            if (advanced) {
                AntiAliasingMode pre =
                        config.tuning.spatialAa == RenderTuning.SpatialAa.FXAA
                                ? AntiAliasingMode.FXAA
                                : config.tuning.spatialAa == RenderTuning.SpatialAa.SMAA
                                        ? AntiAliasingMode.SMAA
                                        : AntiAliasingMode.OFF;
                for (var setting : NumericTuningSetting.forMode(pre, true))
                    add(list, new TuningSliderWidget(runtime, setting));
            }
        }
        if (mode.usesHistoryBuffers()) {
            if (advanced) cycle(list, runtime, "motion_vertex_budget", RenderTuning.MotionVertexBudget.values(),
                    t -> t.motionVertexBudget, (t, v) -> t.motionVertexBudget = v);
            toggle(
                    list,
                    runtime,
                    "entity_motion",
                    t -> t.entityMotion,
                    (t, v) -> t.entityMotion = v);
        }
        if (mode == AntiAliasingMode.TAA && advanced) {
            cycle(
                    list,
                    runtime,
                    "taa_history_filter",
                    RenderTuning.HistoryFilter.values(),
                    t -> t.taaHistoryFilter,
                    (t, v) -> t.taaHistoryFilter = v);
            choice(
                    list,
                    "taa_jitter_phases",
                    List.of(8, 16, 32),
                    () -> runtime.configSnapshot().tuning.taaJitterPhases,
                    v -> runtime.editTuning(t -> t.taaJitterPhases = v));
        }
        for (var setting : NumericTuningSetting.forMode(mode, advanced))
            add(list, new TuningSliderWidget(runtime, setting));
        if (advanced && !mode.usesNativeFsrSharpening()) {
            cycle(
                    list,
                    runtime,
                    "sharpener",
                    RenderTuning.Sharpener.values(),
                    t -> t.sharpener,
                    (t, v) -> t.sharpener = v);
            add(list, new TuningSliderWidget(runtime, NumericTuningSetting.SHARPEN_LIMIT));
        }
        if (advanced
                && (mode == AntiAliasingMode.DLSS_SUPER_RESOLUTION
                        || mode.usesFsrQualityControl()
                        || mode.usesSpatialUpscaleQualityControl())) {
            cycle(
                    list,
                    runtime,
                    "mip_bias_mode",
                    RenderTuning.MipBiasMode.values(),
                    t -> t.mipBiasMode,
                    (t, v) -> t.mipBiasMode = v);
            if (config.tuning.mipBiasMode == RenderTuning.MipBiasMode.CUSTOM)
                add(list, new TuningSliderWidget(runtime, NumericTuningSetting.MIP_BIAS));
            if (config.tuning.mipBiasMode == RenderTuning.MipBiasMode.AUTOMATIC)
                add(list, new TuningSliderWidget(runtime, NumericTuningSetting.MIP_BIAS_OFFSET));
        }
        if (mode == AntiAliasingMode.FSR3_SUPER_RESOLUTION || mode.usesFsrFrameGeneration()) {
            Button fg =
                    Button.builder(
                                    label(
                                            "frame_generation",
                                            Component.translatable(
                                                    mode.usesFsrFrameGeneration()
                                                            ? "options.on"
                                                            : "options.off")),
                                    b -> {
                                        runtime.setMode(
                                                mode.usesFsrFrameGeneration()
                                                        ? AntiAliasingMode.FSR3_SUPER_RESOLUTION
                                                        : AntiAliasingMode
                                                                .FSR3_SUPER_RESOLUTION_FRAME_GENERATION);
                                    })
                            .tooltip(tip("frame_generation"))
                            .build();
            fg.active =
                    mode.usesFsrFrameGeneration()
                            || runtime.canSelectMode(
                                    AntiAliasingMode.FSR3_SUPER_RESOLUTION_FRAME_GENERATION);
            add(list, fg);
        }
        if (advanced) {
            var views =
                    new ArrayList<RenderTuning.DebugView>(
                            List.of(RenderTuning.DebugView.NONE, RenderTuning.DebugView.EDGES));
            if (mode == AntiAliasingMode.TAA)
                views.addAll(
                        List.of(
                                RenderTuning.DebugView.HISTORY_WEIGHT,
                                RenderTuning.DebugView.DEPTH_CONFIDENCE,
                                RenderTuning.DebugView.LUMA_CONFIDENCE,
                                RenderTuning.DebugView.CLIPPING_CONFIDENCE,
                                RenderTuning.DebugView.MOTION_CONFIDENCE));
            if (mode.usesHistoryBuffers()) views.add(RenderTuning.DebugView.MOTION_VECTORS);
            if (mode.usesFsrQualityControl())
                views.add(RenderTuning.DebugView.REACTIVE_MASK);
            choice(
                    list,
                    "debug_view",
                    views,
                    () -> runtime.configSnapshot().tuning.debugView,
                    v ->
                            runtime.editSettings(
                                    c -> {
                                        c.tuning.debugView = v;
                                        c.debugViewsEnabled = v == RenderTuning.DebugView.EDGES;
                                    }));
            choice(
                    list,
                    "record_metrics",
                    List.of(false, true),
                    () -> runtime.configSnapshot().recordMetrics,
                    v -> runtime.editSettings(c -> c.recordMetrics = v));
        }
    }

    private static void smaa(OptionsList list, RenderRuntime runtime) {
        cycle(
                list,
                runtime,
                "smaa_quality",
                RenderTuning.SmaaQuality.values(),
                t -> t.smaaQuality,
                (t, v) -> t.smaaQuality = v);
    }

    private static void toggle(
            OptionsList l,
            RenderRuntime r,
            String k,
            Predicate<RenderTuning> get,
            BiConsumer<RenderTuning, Boolean> set) {
        choice(
                l,
                k,
                List.of(false, true),
                () -> get.test(r.configSnapshot().tuning),
                v -> r.editTuning(t -> set.accept(t, v)));
    }

    private static <E extends Enum<E>> void cycle(
            OptionsList l,
            RenderRuntime r,
            String k,
            E[] values,
            Function<RenderTuning, E> get,
            BiConsumer<RenderTuning, E> set) {
        choice(
                l,
                k,
                List.of(values),
                () -> get.apply(r.configSnapshot().tuning),
                v -> r.editTuning(t -> set.accept(t, v)));
    }

    private static <T> void choice(
            OptionsList l, String k, List<T> values, Supplier<T> get, Consumer<T> set) {
        Button b =
                Button.builder(
                                label(k, value(get.get())),
                                button -> {
                                    set.accept(
                                            values.get(
                                                    (values.indexOf(get.get()) + 1)
                                                            % values.size()));
                                    button.setMessage(label(k, value(get.get())));
                                })
                        .tooltip(tip(k))
                        .build();
        add(l, b);
    }

    private static Component value(Object v) {
        if (v instanceof Boolean b) return Component.translatable(b ? "options.on" : "options.off");
        if (v instanceof Enum<?> e)
            return Component.translatable(
                    "options.salts_anti_aliasing.value." + e.name().toLowerCase(Locale.ROOT));
        return Component.literal(v.toString());
    }

    private static Component label(String k, Component v) {
        return Component.translatable("options.salts_anti_aliasing.tuning." + k)
                .append(": ")
                .append(v);
    }

    private static Tooltip tip(String k) {
        return Tooltip.create(
                Component.translatable("options.salts_anti_aliasing.tuning." + k + ".tooltip"));
    }

    private static void add(OptionsList list, AbstractWidget widget) {
        widget.setWidth(list.getRowWidth());
        list.addBig(widget);
    }
}
