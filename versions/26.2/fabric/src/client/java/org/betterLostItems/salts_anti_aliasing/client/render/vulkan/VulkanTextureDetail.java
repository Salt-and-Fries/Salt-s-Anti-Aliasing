package org.betterLostItems.salts_anti_aliasing.client.render.vulkan;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.pipeline.*;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.shaders.*;

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
                        || config.mode.usesXessQualityControl() || config.mode.usesSgsr2QualityControl() || config.mode.usesFsrQualityControl();
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
                                nativeTemporal && !config.mode.usesXessQualityControl() && !config.mode.usesSgsr2QualityControl())
                        : 0;
    }

    public static void endScene() {
        activeQuarterSteps = 0;
    }

    public static int activeQuarterSteps() {
        return activeQuarterSteps;
    }

    public static RenderPipeline select(RenderPipeline compiled) {
        if (activeQuarterSteps == 0) return compiled;
        RenderPipeline source = compiled;
        if (source == null || source.getShaderDefines().values().containsKey(DEFINE))
            return compiled;
        Identifier fragment = source.getFragmentShader();
        if (fragment == null || !TextureDetailShaderPatch.supportsTerrainShader(fragment.toString()))
            return compiled;
        RenderPipeline variant =
                VARIANTS.computeIfAbsent(source, k -> new HashMap<>())
                        .computeIfAbsent(activeQuarterSteps, k -> createVariant(source, k));
        return variant;
    }

    private static RenderPipeline createVariant(RenderPipeline source, int quarters) {
        var values = new HashMap<>(source.getShaderDefines().values());
        values.put(DEFINE, Float.toString(quarters * .25f));
        var location = source.getLocation();
        return RenderPipelineAccessor.saltsAntiAliasing$create(
                Identifier.fromNamespaceAndPath(
                        location.getNamespace(), location.getPath() + "_salts_mip_" + quarters),
                source.getVertexShader(), source.getFragmentShader(),
                new ShaderDefines(
                        Map.copyOf(values), Set.copyOf(source.getShaderDefines().flags())),
                List.copyOf(source.getBindGroupLayouts()),
                source.getColorTargetStates().clone(),
                source.getDepthStencilState(),
                source.getPolygonMode(),
                source.isCull(),
                source.getVertexFormatBindings().clone(),
                source.getPrimitiveTopology(),
                source.getSortKey());
    }

    public static ShaderSource wrapSource(RenderPipeline pipeline, ShaderSource source) {
        if (!pipeline.getShaderDefines().values().containsKey(DEFINE)) return source;
        return (id, type) -> {
            String text = source.get(id, type);
            return text != null && type == ShaderType.FRAGMENT ? TextureDetailShaderPatch.patch(text) : text;
        };
    }
}
