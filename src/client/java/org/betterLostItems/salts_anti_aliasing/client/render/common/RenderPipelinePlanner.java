package org.betterLostItems.salts_anti_aliasing.client.render.common;

import org.betterLostItems.salts_anti_aliasing.client.config.AntiAliasingConfig;
import org.betterLostItems.salts_anti_aliasing.client.config.AntiAliasingMode;
import org.betterLostItems.salts_anti_aliasing.client.render.api.RenderBackend;
import org.betterLostItems.salts_anti_aliasing.client.render.api.RenderCapability;
import org.betterLostItems.salts_anti_aliasing.client.render.api.RenderPassSpec;
import org.betterLostItems.salts_anti_aliasing.client.render.api.RenderTargetDescriptor;
import org.betterLostItems.salts_anti_aliasing.client.render.api.RenderTargetSizing;
import org.betterLostItems.salts_anti_aliasing.client.render.api.RenderTargetType;
import org.betterLostItems.salts_anti_aliasing.client.render.api.TextureFormat;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class RenderPipelinePlanner {
    public PipelinePlan plan(RenderBackend backend, AntiAliasingConfig config) {
        Map<String, RenderTargetDescriptor> targets = new LinkedHashMap<>();
        List<RenderPassSpec> passes = new ArrayList<>();
        float sceneScale = config.sceneRenderScale();

        targets.put("scene_color", target("scene_color", RenderTargetType.SCENE_COLOR, TextureFormat.RGBA16F,
                config.usesInternalResolutionPath() ? RenderTargetSizing.INTERNAL : RenderTargetSizing.OUTPUT,
                config.usesInternalResolutionPath() ? sceneScale : 1.0f,
                false));
        targets.put("scene_depth", target("scene_depth", RenderTargetType.SCENE_DEPTH, TextureFormat.DEPTH24_STENCIL8,
                config.usesInternalResolutionPath() ? RenderTargetSizing.INTERNAL : RenderTargetSizing.OUTPUT,
                config.usesInternalResolutionPath() ? sceneScale : 1.0f,
                false));

        String currentColor = "scene_color";

        if (config.mode.usesHistoryBuffers()) {
            targets.put("history_color", target("history_color", RenderTargetType.HISTORY_COLOR, TextureFormat.RGBA16F,
                    RenderTargetSizing.INTERNAL, sceneScale, true));
            currentColor = addPass(
                    passes,
                    targets,
                    "taa_resolve",
                    EnumSet.of(RenderCapability.TEMPORAL_AA),
                    List.of(currentColor, "history_color", "scene_depth"),
                    "taa_resolved",
                    RenderTargetType.INTERMEDIATE_COLOR,
                    TextureFormat.RGBA16F,
                    RenderTargetSizing.INTERNAL,
                    sceneScale,
                    false
            );
        }

        if (config.mode == AntiAliasingMode.SSAA) {
            currentColor = addPass(
                    passes,
                    targets,
                    "ssaa_resolve",
                    EnumSet.of(RenderCapability.INTERNAL_RESOLUTION),
                    List.of(currentColor),
                    "ssaa_resolved_color",
                    RenderTargetType.INTERMEDIATE_COLOR,
                    TextureFormat.RGBA16F,
                    RenderTargetSizing.OUTPUT,
                    1.0f,
                    false
            );
        } else if (config.usesInternalResolutionPath() && !config.mode.usesDedicatedUpscalePass()) {
            currentColor = addPass(
                    passes,
                    targets,
                    "baseline_upscale",
                    EnumSet.of(RenderCapability.INTERNAL_RESOLUTION),
                    List.of(currentColor),
                    "upscaled_scene",
                    RenderTargetType.INTERMEDIATE_COLOR,
                    TextureFormat.RGBA16F,
                    RenderTargetSizing.OUTPUT,
                    1.0f,
                    false
            );
        }

        switch (config.mode) {
            case OFF -> {
            }
            case NIS_SHARPEN -> currentColor = addPass(
                    passes,
                    targets,
                    "nis_sharpen",
                    EnumSet.of(RenderCapability.POST_PROCESSING, RenderCapability.SHARPENING),
                    List.of(currentColor),
                    "sharpened_color",
                    RenderTargetType.INTERMEDIATE_COLOR,
                    TextureFormat.RGBA16F,
                    RenderTargetSizing.OUTPUT,
                    1.0f,
                    false
            );
            case FXAA -> currentColor = addPass(
                    passes,
                    targets,
                    "fxaa_resolve",
                    EnumSet.of(RenderCapability.POST_PROCESSING),
                    List.of(currentColor),
                    "fxaa_color",
                    RenderTargetType.INTERMEDIATE_COLOR,
                    TextureFormat.RGBA16F,
                    RenderTargetSizing.OUTPUT,
                    1.0f,
                    false
            );
            case SSAA -> {
            }
            case MSAA -> passes.add(new RenderPassSpec(
                    "msaa_scene_resolve",
                    EnumSet.of(RenderCapability.MULTISAMPLE_AA),
                    List.of(currentColor, "scene_depth"),
                    List.of()
            ));
            case SMAA -> {
                targets.put("smaa_edges", target("smaa_edges", RenderTargetType.AUXILIARY, TextureFormat.RG8,
                        RenderTargetSizing.OUTPUT, 1.0f, false));
                targets.put("smaa_weights", target("smaa_weights", RenderTargetType.AUXILIARY, TextureFormat.RGBA8,
                        RenderTargetSizing.OUTPUT, 1.0f, false));
                passes.add(new RenderPassSpec(
                        "smaa_edge_detect",
                        EnumSet.of(RenderCapability.POST_PROCESSING),
                        List.of(currentColor),
                        List.of("smaa_edges")
                ));
                passes.add(new RenderPassSpec(
                        "smaa_weight_blend",
                        EnumSet.of(RenderCapability.POST_PROCESSING),
                        List.of("smaa_edges"),
                        List.of("smaa_weights")
                ));
                currentColor = addPass(
                        passes,
                        targets,
                        "smaa_neighborhood_blend",
                        EnumSet.of(RenderCapability.POST_PROCESSING),
                        List.of(currentColor, "smaa_weights"),
                        "smaa_color",
                        RenderTargetType.INTERMEDIATE_COLOR,
                        TextureFormat.RGBA16F,
                        RenderTargetSizing.OUTPUT,
                        1.0f,
                        false
                );
            }
            case NIS_UPSCALE -> currentColor = addPass(
                    passes,
                    targets,
                    "nis_upscale",
                    EnumSet.of(RenderCapability.INTERNAL_RESOLUTION, RenderCapability.SPATIAL_UPSCALING),
                    List.of("scene_color"),
                    "nis_upscaled_color",
                    RenderTargetType.INTERMEDIATE_COLOR,
                    TextureFormat.RGBA16F,
                    RenderTargetSizing.OUTPUT,
                    1.0f,
                    false
            );
            case FSR1_UPSCALE -> currentColor = addPass(
                    passes,
                    targets,
                    "fsr1_easu",
                    EnumSet.of(RenderCapability.INTERNAL_RESOLUTION, RenderCapability.SPATIAL_UPSCALING),
                    List.of("scene_color"),
                    "fsr1_upscaled_color",
                    RenderTargetType.INTERMEDIATE_COLOR,
                    TextureFormat.RGBA16F,
                    RenderTargetSizing.OUTPUT,
                    1.0f,
                    false
            );
            case FSR1_RCAS -> {
                String fsrUpscaled = addPass(
                        passes,
                        targets,
                        "fsr1_easu",
                        EnumSet.of(RenderCapability.INTERNAL_RESOLUTION, RenderCapability.SPATIAL_UPSCALING),
                        List.of("scene_color"),
                        "fsr1_upscaled_color",
                        RenderTargetType.INTERMEDIATE_COLOR,
                        TextureFormat.RGBA16F,
                        RenderTargetSizing.OUTPUT,
                        1.0f,
                        false
                );
                currentColor = addPass(
                        passes,
                        targets,
                        "rcas_sharpen",
                        EnumSet.of(RenderCapability.SHARPENING),
                        List.of(fsrUpscaled),
                        "fsr1_rcas_color",
                        RenderTargetType.INTERMEDIATE_COLOR,
                        TextureFormat.RGBA16F,
                        RenderTargetSizing.OUTPUT,
                        1.0f,
                        false
                );
            }
            case TAA -> {
            }
        }

        validateBackendSupport(backend, passes);
        return new PipelinePlan(backend.type(), new ArrayList<>(targets.values()), passes);
    }

    private static RenderTargetDescriptor target(
            String id,
            RenderTargetType type,
            TextureFormat format,
            RenderTargetSizing sizing,
            float scale,
            boolean persistent
    ) {
        return new RenderTargetDescriptor(id, type, format, sizing, scale, persistent);
    }

    private static String addPass(
            List<RenderPassSpec> passes,
            Map<String, RenderTargetDescriptor> targets,
            String id,
            Set<RenderCapability> requiredCapabilities,
            List<String> readTargets,
            String writeTargetId,
            RenderTargetType targetType,
            TextureFormat format,
            RenderTargetSizing sizing,
            float scale,
            boolean persistent
    ) {
        targets.put(writeTargetId, target(writeTargetId, targetType, format, sizing, scale, persistent));
        passes.add(new RenderPassSpec(id, requiredCapabilities, readTargets, List.of(writeTargetId)));
        return writeTargetId;
    }

    private static void validateBackendSupport(RenderBackend backend, List<RenderPassSpec> passes) {
        for (RenderPassSpec pass : passes) {
            if (!backend.supportsAll(pass.requiredCapabilities())) {
                throw new IllegalStateException(
                        "Backend " + backend.type().displayName() + " does not support pass " + pass.id()
                );
            }
        }
    }
}
