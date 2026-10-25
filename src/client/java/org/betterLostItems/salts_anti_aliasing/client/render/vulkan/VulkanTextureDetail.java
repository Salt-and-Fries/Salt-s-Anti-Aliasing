package org.betterLostItems.salts_anti_aliasing.client.render.vulkan;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.pipeline.*;

import net.minecraft.client.renderer.ShaderDefines;
import net.minecraft.resources.Identifier;

import org.betterLostItems.salts_anti_aliasing.client.config.*;
import org.betterLostItems.salts_anti_aliasing.mixin.client.RenderPipelineAccessor;

import java.util.*;

/** Bounded, immutable terrain pipeline variants for optional temporal-upscaler MIP bias. */
public final class VulkanTextureDetail {
    private static final String DEFINE = "SALTS_MIP_BIAS";
    private static final Map<RenderPipeline, Map<Integer, RenderPipeline>> VARIANTS =
            new WeakHashMap<>();
    private static int activeQuarterSteps;

    private VulkanTextureDetail() {}

    public static void beginScene(AntiAliasingConfig config, int renderWidth, int displayWidth) {
        boolean nativeTemporal =
                config.mode == AntiAliasingMode.DLSS_SUPER_RESOLUTION
                        || config.mode.usesFsrQualityControl();
        boolean spatial = config.mode.usesSpatialUpscaleQualityControl();
        var t = config.tuning;
        activeQuarterSteps =
                (nativeTemporal || spatial) && t.mipBiasMode != RenderTuning.MipBiasMode.OFF
                        ? TextureDetailShaderPatch.quarterSteps(
                                t.mipBiasMode == RenderTuning.MipBiasMode.AUTOMATIC,
                                t.mipBias,
                                t.mipBiasOffset,
                                renderWidth,
                                displayWidth,
                                nativeTemporal)
                        : 0;
    }

    public static void endScene() {
        activeQuarterSteps = 0;
    }

    public static int activeQuarterSteps() {
        return activeQuarterSteps;
    }

    public static CompiledRenderPipeline select(CompiledRenderPipeline compiled) {
        if (activeQuarterSteps == 0) return compiled;
        RenderPipeline source = VulkanMsaaPipelineVariants.sourceFor(compiled);
        if (source == null || source.getShaderDefines().values().containsKey(DEFINE))
            return compiled;
        Identifier fragment = source.getShaders().get(ShaderType.FRAGMENT);
        if (fragment == null || !fragment.equals(Identifier.withDefaultNamespace("core/terrain")))
            return compiled;
        RenderPipeline variant =
                VARIANTS.computeIfAbsent(source, k -> new HashMap<>())
                        .computeIfAbsent(activeQuarterSteps, k -> createVariant(source, k));
        return RenderSystem.getCompiledPipeline(variant);
    }

    private static RenderPipeline createVariant(RenderPipeline source, int quarters) {
        var values = new HashMap<>(source.getShaderDefines().values());
        values.put(DEFINE, Float.toString(quarters * .25f));
        var location = source.getLocation();
        return RenderPipelineAccessor.saltsAntiAliasing$create(
                Identifier.fromNamespaceAndPath(
                        location.getNamespace(), location.getPath() + "_salts_mip_" + quarters),
                Map.copyOf(source.getShaders()),
                new ShaderDefines(
                        Map.copyOf(values), Set.copyOf(source.getShaderDefines().flags())),
                List.copyOf(source.getBindGroupLayouts()),
                source.getColorTargetStates().toArray(ColorTargetState[]::new),
                source.getDepthStencilState(),
                source.getPolygonMode(),
                source.isCull(),
                source.getVertexFormatBindings()
                        .toArray(com.mojang.renderpearl.api.vertex.VertexFormat[]::new),
                source.getPrimitiveTopology(),
                source.pushConstantSize(),
                source.getSortKey());
    }

    public static ShaderSource wrapSource(RenderPipeline pipeline, ShaderSource source) {
        if (!pipeline.getShaderDefines().values().containsKey(DEFINE)) return source;
        return new ShaderSource() {
            public String getShader(Identifier id, ShaderType type) {
                String text = source.getShader(id, type);
                return text != null && type == ShaderType.FRAGMENT
                        ? TextureDetailShaderPatch.patch(text)
                        : text;
            }

            public CachedIncludeSource getInclude(Identifier id) {
                return source.getInclude(id);
            }

            public void
                    close() {} // The shader manager owns the borrowed source and include memory.
        };
    }
}
