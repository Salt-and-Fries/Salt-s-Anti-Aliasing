package org.betterLostItems.salts_anti_aliasing.mixin.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;

import org.betterLostItems.salts_anti_aliasing.client.platform.modern.ModernMinecraftHooks;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(BlockEntityRenderDispatcher.class)
public class BlockEntityRenderDispatcherMixin {
    @WrapMethod(method = "submit")
    private void saltsAntiAliasing$scope(
            BlockEntityRenderState state,
            PoseStack pose,
            SubmitNodeCollector collector,
            CameraRenderState camera,
            Operation<Void> original) {
        boolean scoped = ModernMinecraftHooks.beginBlockGeometrySubmission(state);
        try {
            original.call(state, pose, collector, camera);
        } finally {
            if (scoped) ModernMinecraftHooks.endGeometrySubmission();
        }
    }
}
