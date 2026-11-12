package org.betterLostItems.salts_anti_aliasing.vulkanmod.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.*;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.item.ItemDisplayContext;
import org.betterLostItems.salts_anti_aliasing.vulkanmod.motion.EntityMotionGeometry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import java.util.List;

@Mixin(net.minecraft.client.renderer.feature.ItemFeatureRenderer.class)
abstract class ItemMotionMixin {
    @WrapOperation(method = "render", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/renderer/entity/ItemRenderer;renderItem(Lnet/minecraft/world/item/ItemDisplayContext;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II[ILjava/util/List;Lnet/minecraft/client/renderer/RenderType;Lnet/minecraft/client/renderer/item/ItemStackRenderState$FoilType;)V", ordinal = 0))
    private void salts$capture(ItemDisplayContext display, PoseStack pose, MultiBufferSource buffers,
                              int light, int overlay, int[] tints, List<BakedQuad> quads, RenderType type,
                              ItemStackRenderState.FoilType foil, Operation<Void> operation,
                              @Local SubmitNodeStorage.ItemSubmit submit) {
        MultiBufferSource capture = layer -> {
            var consumer = buffers.getBuffer(layer);
            return layer == type ? EntityMotionGeometry.captureSubmitted(submit, "item", layer, consumer) : consumer;
        };
        operation.call(display, pose, capture, light, overlay, tints, quads, type, foil);
    }
}
