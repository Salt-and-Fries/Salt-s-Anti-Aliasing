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

/**
 * Converts the shared config model into a backend-neutral render plan.
 *
 * <p>This planner should remain free of Minecraft mixin descriptors and concrete GPU command
 * details. It describes the passes and logical targets a mode needs; the active version/backend
 * adapter decides how those concepts become real render targets and post-chain invocations.</p>
 */
public final class RenderPipelinePlanner {
    /**
     * Builds a pass/target plan for the active backend and sanitized config.
     */
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
            // Temporal modes need persistent history that survives between frames.
            targets.put("history_color", target("history_color", RenderTargetType.HISTORY_COLOR, TextureFormat.RGBA16F,
                    RenderTargetSizing.INTERNAL, sceneScale, true));
            if (config.mode == AntiAliasingMode.TAA) {
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
        }

        if (config.mode == AntiAliasingMode.SSAA) {
            // SSAA resolves a high-resolution scene back to the native output.
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

        // Each case below describes the logical effect chain. The backend decides how to execute it.
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
            case SMAA, SMAA_NIS_SHARPEN -> {
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
            case DLSS_SUPER_RESOLUTION -> {
                targets.put("motion_vectors", target("motion_vectors", RenderTargetType.MOTION_VECTOR, TextureFormat.RG16F,
                        RenderTargetSizing.INTERNAL, sceneScale, true));
                currentColor = addPass(
                        passes,
                        targets,
                        "dlss_super_resolution",
                        EnumSet.of(
                                RenderCapability.INTERNAL_RESOLUTION,
                                RenderCapability.SPATIAL_UPSCALING,
                                RenderCapability.TEMPORAL_AA,
                                RenderCapability.VENDOR_UPSCALING
                        ),
                        List.of("scene_color", "scene_depth", "motion_vectors", "history_color"),
                        "dlss_upscaled_color",
                        RenderTargetType.INTERMEDIATE_COLOR,
                        TextureFormat.RGBA16F,
                        RenderTargetSizing.OUTPUT,
                        1.0f,
                        false
                );
                if (config.mode == AntiAliasingMode.SMAA_NIS_SHARPEN) {
                    currentColor = addPass(
                            passes,
                            targets,
                            "nis_sharpen",
                            EnumSet.of(RenderCapability.POST_PROCESSING, RenderCapability.SHARPENING),
                            List.of(currentColor),
                            "smaa_sharpened_color",
                            RenderTargetType.INTERMEDIATE_COLOR,
                            TextureFormat.RGBA16F,
                            RenderTargetSizing.OUTPUT,
                            1.0f,
                            false
                    );
                }
            }
            case FSR2_SUPER_RESOLUTION, FSR3_SUPER_RESOLUTION, FSR3_SUPER_RESOLUTION_FRAME_GENERATION -> {
                targets.put("motion_vectors", target("motion_vectors", RenderTargetType.MOTION_VECTOR, TextureFormat.RG16F,
                        RenderTargetSizing.INTERNAL, sceneScale, true));
                targets.put("opaque_scene_color", target("opaque_scene_color", RenderTargetType.INTERMEDIATE_COLOR,
                        TextureFormat.RGBA16F, RenderTargetSizing.INTERNAL, sceneScale, false));
                targets.put("reactive_mask", target("reactive_mask", RenderTargetType.AUXILIARY, TextureFormat.R8,
                        RenderTargetSizing.INTERNAL, sceneScale, false));
                targets.put("transparency_composition_mask", target("transparency_composition_mask", RenderTargetType.AUXILIARY,
                        TextureFormat.R8, RenderTargetSizing.INTERNAL, sceneScale, false));
                EnumSet<RenderCapability> fsrCapabilities = EnumSet.of(
                        RenderCapability.INTERNAL_RESOLUTION,
                        RenderCapability.SPATIAL_UPSCALING,
                        RenderCapability.TEMPORAL_AA,
                        RenderCapability.FSR_UPSCALING
                );
                if (config.mode.usesFsrFrameGeneration()) {
                    fsrCapabilities.add(RenderCapability.FSR_FRAME_GENERATION);
                }
                currentColor = addPass(
                        passes,
                        targets,
                        config.mode.usesFsrFrameGeneration() ? "fsr3_super_resolution_frame_generation" : "fsr_super_resolution",
                        fsrCapabilities,
                        List.of(
                                "scene_color",
                                "scene_depth",
                                "motion_vectors",
                                "reactive_mask",
                                "transparency_composition_mask",
                                "history_color"
                        ),
                        config.mode.usesFsrFrameGeneration() ? "fsr3_frame_generation_color" : "fsr_upscaled_color",
                        RenderTargetType.INTERMEDIATE_COLOR,
                        TextureFormat.RGBA16F,
                        RenderTargetSizing.OUTPUT,
                        1.0f,
                        false
                );
            }
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

    /**
     * Handles target as part of the anti-aliasing render, configuration, or compatibility flow.
     * @param id id value supplied by the caller or Minecraft callback
     * @param type type value supplied by the caller or Minecraft callback
     * @param format format value supplied by the caller or Minecraft callback
     * @param sizing sizing value supplied by the caller or Minecraft callback
     * @param scale scale value supplied by the caller or Minecraft callback
     * @param persistent persistent value supplied by the caller or Minecraft callback
     * @return render target descriptor for the requested logical target
     */
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

    /**
     * Handles add pass as part of the anti-aliasing render, configuration, or compatibility flow.
     * @param passes passes value supplied by the caller or Minecraft callback
     * @param targets targets value supplied by the caller or Minecraft callback
     * @param id id value supplied by the caller or Minecraft callback
     * @param requiredCapabilities required capabilities value supplied by the caller or Minecraft
     * callback
     * @param readTargets read targets value supplied by the caller or Minecraft callback
     * @param writeTargetId write target id value supplied by the caller or Minecraft callback
     * @param targetType target type value supplied by the caller or Minecraft callback
     * @param format format value supplied by the caller or Minecraft callback
     * @param sizing sizing value supplied by the caller or Minecraft callback
     * @param scale scale value supplied by the caller or Minecraft callback
     * @param persistent persistent value supplied by the caller or Minecraft callback
     * @return updated pass list after appending the requested pass
     */
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

    /**
     * Coordinates validate backend support within the anti-aliasing render, configuration, or compatibility flow.
     * @param backend backend value supplied by the caller or Minecraft callback
     * @param passes passes value supplied by the caller or Minecraft callback
     */
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
