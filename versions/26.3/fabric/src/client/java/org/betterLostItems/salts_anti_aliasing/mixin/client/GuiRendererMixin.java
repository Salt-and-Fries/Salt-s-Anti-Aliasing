package org.betterLostItems.salts_anti_aliasing.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.RenderPass;

import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.renderer.RenderPipelines;

import org.betterLostItems.salts_anti_aliasing.client.render.vulkan.VulkanSceneFsrController;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;

import java.util.function.Supplier;

/** World overlays must match the HUD-less image or automatic UI extraction masks the scene. */
@Mixin(GuiRenderer.class)
public class GuiRendererMixin {
    @Unique private boolean saltsAntiAliasing$worldOverlayOnly;

    @WrapOperation(
            method = "draw",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/gui/render/GuiRenderer;executeDrawRange(Ljava/util/function/Supplier;Lcom/mojang/blaze3d/pipeline/RenderTarget;Lcom/mojang/renderpearl/api/buffers/GpuBufferSlice;II)V"))
    private void saltsAntiAliasing$mirrorWorldOverlays(
            GuiRenderer renderer,
            Supplier<String> label,
            RenderTarget target,
            GpuBufferSlice transforms,
            int first,
            int last,
            Operation<Void> original) {
        RenderTarget hudless = VulkanSceneFsrController.instance().hudlessWorldOverlayTarget();
        if (hudless != null) {
            saltsAntiAliasing$worldOverlayOnly = true;
            try {
                original.call(renderer, label, hudless, transforms, first, last);
            } finally {
                saltsAntiAliasing$worldOverlayOnly = false;
            }
        }
        original.call(renderer, label, target, transforms, first, last);
    }

    @WrapOperation(
            method = "executeDrawRange",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/gui/render/GuiRenderer;executeDraw(Lnet/minecraft/client/gui/render/GuiRenderer$Draw;Lcom/mojang/renderpearl/api/commands/RenderPass;)V"))
    private void saltsAntiAliasing$filterWorldOverlays(
            GuiRenderer renderer, @Coerce Object draw, RenderPass pass, Operation<Void> original) {
        var pipeline = ((GuiDrawAccessor) draw).saltsAntiAliasing$pipeline();
        if (!saltsAntiAliasing$worldOverlayOnly
                || pipeline == RenderPipelines.VIGNETTE
                || pipeline == RenderPipelines.GUI_NAUSEA_OVERLAY)
            original.call(renderer, draw, pass);
    }
}
