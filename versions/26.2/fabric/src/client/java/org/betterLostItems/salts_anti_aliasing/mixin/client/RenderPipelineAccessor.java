package org.betterLostItems.salts_anti_aliasing.mixin.client;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.pipeline.BindGroupLayout;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.PolygonMode;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.ShaderDefines;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;

/**
 * Constructs RenderPipeline variants that keep Minecraft's identity-keyed Vulkan cache separated.
 */
@Mixin(RenderPipeline.class)
public interface RenderPipelineAccessor {
    @Invoker("<init>")
    static RenderPipeline saltsAntiAliasing$create(
            Identifier location,
            Identifier vertexShader,
            Identifier fragmentShader,
            ShaderDefines shaderDefines,
            List<BindGroupLayout> bindGroupLayouts,
            ColorTargetState[] colorTargetStates,
            DepthStencilState depthStencilState,
            PolygonMode polygonMode,
            boolean cull,
            VertexFormat[] vertexFormatPerBuffer,
            PrimitiveTopology primitiveTopology,
            int sortKey
    ) {
        throw new AssertionError();
    }
}
