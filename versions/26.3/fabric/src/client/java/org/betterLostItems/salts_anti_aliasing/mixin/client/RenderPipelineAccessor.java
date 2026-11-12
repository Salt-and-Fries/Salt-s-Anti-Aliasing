package org.betterLostItems.salts_anti_aliasing.mixin.client;

import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.ShaderType;
import com.mojang.renderpearl.api.pipeline.PolygonMode;
import com.mojang.renderpearl.api.vertex.VertexFormat;
import net.minecraft.client.renderer.ShaderDefines;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.Collection;
import java.util.Map;

/**
 * Constructs RenderPipeline variants that keep Minecraft's identity-keyed Vulkan cache separated.
 */
@Mixin(RenderPipeline.class)
public interface RenderPipelineAccessor {
    @Invoker("<init>")
    static RenderPipeline saltsAntiAliasing$create(
            Identifier location,
            Map<ShaderType, Identifier> shaders,
            ShaderDefines shaderDefines,
            Collection<BindGroupLayout> bindGroupLayouts,
            ColorTargetState[] colorTargetStates,
            DepthStencilState depthStencilState,
            PolygonMode polygonMode,
            boolean cull,
            VertexFormat[] vertexFormatPerBuffer,
            PrimitiveTopology primitiveTopology,
            int pushConstantSize,
            int sortKey
    ) {
        throw new AssertionError();
    }
}
