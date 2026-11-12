package org.betterLostItems.salts_anti_aliasing.client.render.vulkan;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.commands.RenderPassDescriptor;
import com.mojang.renderpearl.api.pipeline.*;
import net.minecraft.resources.Identifier;
import org.betterLostItems.salts_anti_aliasing.mixin.client.RenderPipelineAccessor;
import org.joml.Vector4fc;
import java.util.*;

/** Preserves blend/write state when a linear DLSS source uses floating-point attachments. */
public final class VulkanAttachmentPipelineVariants {
    private static final Map<RenderPipeline, Map<List<GpuFormat>, RenderPipeline>> CACHE = new WeakHashMap<>();
    private VulkanAttachmentPipelineVariants() {}
    public static CompiledRenderPipeline select(CompiledRenderPipeline compiled,
            List<RenderPassDescriptor.Attachment<Optional<Vector4fc>>> attachments) {
        if (!VulkanSceneDlssController.instance().linearColor()) return compiled;
        RenderPipeline source = VulkanMsaaPipelineVariants.sourceFor(compiled);
        if (source == null || source.getColorTargetStates().size() != attachments.size()) return compiled;
        List<GpuFormat> formats = new ArrayList<>();
        boolean differs = false;
        for (int i = 0; i < attachments.size(); i++) {
            var attachment = attachments.get(i);
            var state = source.getColorTargetStates().get(i);
            if (attachment == null || attachment.textureView() == null || state == null) return compiled;
            GpuFormat format = attachment.textureView().texture().getFormat();
            formats.add(format); differs |= format != state.format();
        }
        if (!differs) return compiled;
        RenderPipeline variant = CACHE.computeIfAbsent(source, k -> new HashMap<>())
                .computeIfAbsent(List.copyOf(formats), f -> create(source, f));
        return RenderSystem.getCompiledPipeline(variant);
    }

    private static RenderPipeline create(RenderPipeline source, List<GpuFormat> formats) {
        var states = new ColorTargetState[formats.size()];
        for (int i = 0; i < states.length; i++) {
            var old = source.getColorTargetStates().get(i);
            states[i] = new ColorTargetState(old.blendFunction(), formats.get(i), old.writeMask());
        }
        String suffix = String.join("_", formats.stream().map(f -> f.name().toLowerCase(Locale.ROOT)).toList());
        var id = source.getLocation();
        return RenderPipelineAccessor.saltsAntiAliasing$create(
                Identifier.fromNamespaceAndPath(id.getNamespace(), id.getPath() + "_salts_format_" + suffix),
                Map.copyOf(source.getShaders()), source.getShaderDefines(), List.copyOf(source.getBindGroupLayouts()),
                states, source.getDepthStencilState(), source.getPolygonMode(), source.isCull(),
                source.getVertexFormatBindings().toArray(com.mojang.renderpearl.api.vertex.VertexFormat[]::new),
                source.getPrimitiveTopology(), source.pushConstantSize(), source.getSortKey());
    }
}
