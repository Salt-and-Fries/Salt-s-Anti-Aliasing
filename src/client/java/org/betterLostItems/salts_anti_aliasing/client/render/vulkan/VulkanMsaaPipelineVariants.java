package org.betterLostItems.salts_anti_aliasing.client.render.vulkan;

import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.ShaderDefines;
import net.minecraft.resources.Identifier;
import org.betterLostItems.salts_anti_aliasing.mixin.client.RenderPipelineAccessor;

import java.util.Arrays;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Creates stable RenderPipeline identities for MSAA sample-count variants.
 */
public final class VulkanMsaaPipelineVariants {
    private static final String ALPHA_CUTOUT_DEFINE = "ALPHA_CUTOUT";
    private static final Map<RenderPipeline, Map<Integer, RenderPipeline>> VARIANTS = new IdentityHashMap<>();

    private VulkanMsaaPipelineVariants() {
    }

    public static RenderPipeline variant(RenderPipeline pipeline, int samples) {
        int sanitizedSamples = Math.max(1, samples);
        if (sanitizedSamples <= 1) {
            return pipeline;
        }

        return VARIANTS
                .computeIfAbsent(pipeline, ignored -> new HashMap<>())
                .computeIfAbsent(sanitizedSamples, sampleCount -> createVariant(pipeline, sampleCount));
    }

    private static RenderPipeline createVariant(RenderPipeline pipeline, int samples) {
        return RenderPipelineAccessor.saltsAntiAliasing$create(
                variantLocation(pipeline.getLocation(), samples),
                pipeline.getVertexShader(),
                pipeline.getFragmentShader(),
                copyShaderDefines(pipeline.getShaderDefines()),
                List.copyOf(pipeline.getBindGroupLayouts()),
                copyColorTargetStates(pipeline.getColorTargetStates()),
                pipeline.getDepthStencilState(),
                pipeline.getPolygonMode(),
                pipeline.isCull(),
                copyVertexFormats(pipeline.getVertexFormatBindings()),
                pipeline.getPrimitiveTopology(),
                pipeline.getSortKey()
        );
    }

    public static boolean usesAlphaToCoverage(RenderPipeline pipeline, int samples) {
        boolean hasAlphaCutout = pipeline.getShaderDefines().values().containsKey(ALPHA_CUTOUT_DEFINE);
        boolean hasBlendedColorTarget = Arrays.stream(pipeline.getColorTargetStates())
                .filter(state -> state != null)
                .anyMatch(state -> state.blendFunction().isPresent());
        return VulkanAlphaToCoveragePolicy.shouldEnable(
                samples,
                hasAlphaCutout,
                hasBlendedColorTarget
        );
    }

    private static Identifier variantLocation(Identifier location, int samples) {
        return Identifier.fromNamespaceAndPath(
                location.getNamespace(),
                location.getPath() + "_salts_msaa_" + samples + "x"
        );
    }

    private static ShaderDefines copyShaderDefines(ShaderDefines defines) {
        // Preserve each material's original cutoff. Minecraft's mipmaps retain small non-zero
        // alpha values to preserve coverage around that cutoff; lowering it to zero makes those
        // nominally transparent texels become visible alpha-to-coverage samples at long range.
        return new ShaderDefines(
                VulkanAlphaToCoveragePolicy.preserveShaderDefines(defines.values()),
                Set.copyOf(defines.flags())
        );
    }

    private static ColorTargetState[] copyColorTargetStates(ColorTargetState[] states) {
        return Arrays.copyOf(states, states.length);
    }

    private static VertexFormat[] copyVertexFormats(VertexFormat[] formats) {
        return Arrays.copyOf(formats, formats.length);
    }
}
