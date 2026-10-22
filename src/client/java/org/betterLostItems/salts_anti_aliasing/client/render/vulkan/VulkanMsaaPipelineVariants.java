package org.betterLostItems.salts_anti_aliasing.client.render.vulkan;

import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.CompiledRenderPipeline;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.vertex.VertexFormat;
import net.minecraft.client.renderer.ShaderDefines;
import net.minecraft.resources.Identifier;
import org.betterLostItems.salts_anti_aliasing.client.metrics.MetricsDiagnostics;
import org.betterLostItems.salts_anti_aliasing.mixin.client.RenderPipelineAccessor;

import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Creates stable RenderPipeline identities for MSAA sample-count variants.
 */
public final class VulkanMsaaPipelineVariants {
    private static final String ALPHA_CUTOUT_DEFINE = "ALPHA_CUTOUT";
    private static final Map<RenderPipeline, Map<VariantKey, RenderPipeline>> VARIANTS = new IdentityHashMap<>();
    private static final Map<CompiledRenderPipeline, RenderPipeline> SOURCES =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<String, VariantKey> NATIVE_VARIANTS = new ConcurrentHashMap<>();

    private VulkanMsaaPipelineVariants() {
    }

    public static CompiledRenderPipeline rememberSource(RenderPipeline source, CompiledRenderPipeline compiled) {
        if (compiled != null) {
            SOURCES.put(compiled, source);
        }
        return compiled;
    }

    public static CompiledRenderPipeline compiledVariant(
            CompiledRenderPipeline compiled, int samples, boolean alphaToCoverageRequested
    ) {
        if (samples <= 1) {
            return compiled;
        }
        RenderPipeline source = SOURCES.get(compiled);
        if (source == null) {
            throw new IllegalStateException("Missing source for multisampled render pipeline " + compiled);
        }
        boolean alphaToCoverage = usesAlphaToCoverage(source, samples, alphaToCoverageRequested);
        return RenderSystem.getCompiledPipeline(variant(source, samples, alphaToCoverage));
    }

    /** Reads immutable variant metadata on the asynchronous pipeline compilation thread. */
    public static int nativeSampleCount(String pipelineName, int originalSamples) {
        VariantKey key = NATIVE_VARIANTS.get(pipelineName);
        return key == null ? originalSamples : key.samples();
    }

    public static boolean nativeAlphaToCoverage(String pipelineName) {
        VariantKey key = NATIVE_VARIANTS.get(pipelineName);
        return key != null && key.alphaToCoverage();
    }

    public static RenderPipeline variant(RenderPipeline pipeline, int samples, boolean alphaToCoverage) {
        int sanitizedSamples = Math.max(1, samples);
        if (sanitizedSamples <= 1) {
            return pipeline;
        }

        VariantKey key = new VariantKey(sanitizedSamples, alphaToCoverage);
        return VARIANTS
                .computeIfAbsent(pipeline, ignored -> new HashMap<>())
                .computeIfAbsent(
                        key,
                        variantKey -> createVariant(
                                pipeline,
                                variantKey.samples(),
                                variantKey.alphaToCoverage()
                        )
                );
    }

    public static boolean usesAlphaToCoverage(
            RenderPipeline pipeline,
            int samples,
            boolean requested
    ) {
        boolean hasAlphaCutout = pipeline.getShaderDefines().values().containsKey(ALPHA_CUTOUT_DEFINE);
        boolean hasBlendedColorTarget = pipeline.getColorTargetStates().stream()
                .filter(state -> state != null)
                .anyMatch(state -> state.blendFunction().isPresent());
        return VulkanAlphaToCoveragePolicy.shouldEnable(
                requested,
                samples,
                hasAlphaCutout,
                hasBlendedColorTarget
        );
    }

    private static RenderPipeline createVariant(
            RenderPipeline pipeline,
            int samples,
            boolean alphaToCoverage
    ) {
        Identifier variantLocation = variantLocation(pipeline.getLocation(), samples, alphaToCoverage);
        NATIVE_VARIANTS.put(variantLocation.toString(), new VariantKey(samples, alphaToCoverage));
        RenderPipeline variant = RenderPipelineAccessor.saltsAntiAliasing$create(
                variantLocation,
                Map.copyOf(pipeline.getShaders()),
                copyShaderDefines(pipeline.getShaderDefines()),
                List.copyOf(pipeline.getBindGroupLayouts()),
                copyColorTargetStates(pipeline.getColorTargetStates()),
                pipeline.getDepthStencilState(),
                pipeline.getPolygonMode(),
                pipeline.isCull(),
                copyVertexFormats(pipeline.getVertexFormatBindings()),
                pipeline.getPrimitiveTopology(),
                pipeline.pushConstantSize(),
                pipeline.getSortKey()
        );
        MetricsDiagnostics.info("msaa.pipeline", "pipeline_variant_created", MetricsDiagnostics.fields(
                "sourcePipeline", pipeline.getLocation().toString(),
                "variantPipeline", variantLocation.toString(),
                "samples", samples,
                "alphaToCoverage", alphaToCoverage,
                "primitiveTopology", pipeline.getPrimitiveTopology(),
                "colorTargetCount", pipeline.getColorTargetStates().size(),
                "hasDepthStencilState", pipeline.getDepthStencilState() != null,
                "cullEnabled", pipeline.isCull()
        ));
        return variant;
    }

    private static Identifier variantLocation(
            Identifier location,
            int samples,
            boolean alphaToCoverage
    ) {
        return Identifier.fromNamespaceAndPath(
                location.getNamespace(),
                location.getPath()
                        + "_salts_msaa_"
                        + samples
                        + "x_"
                        + (alphaToCoverage ? "atoc" : "binary")
        );
    }

    private static ShaderDefines copyShaderDefines(ShaderDefines defines) {
        return new ShaderDefines(Map.copyOf(defines.values()), Set.copyOf(defines.flags()));
    }

    private static ColorTargetState[] copyColorTargetStates(List<ColorTargetState> states) {
        return states.toArray(ColorTargetState[]::new);
    }

    private static VertexFormat[] copyVertexFormats(List<VertexFormat> formats) {
        return formats.toArray(VertexFormat[]::new);
    }

    private record VariantKey(int samples, boolean alphaToCoverage) {
    }
}
