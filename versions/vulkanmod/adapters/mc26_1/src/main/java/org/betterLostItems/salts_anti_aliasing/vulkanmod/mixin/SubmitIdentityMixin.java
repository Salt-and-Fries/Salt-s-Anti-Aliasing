package org.betterLostItems.salts_anti_aliasing.vulkanmod.mixin;

import org.betterLostItems.salts_anti_aliasing.vulkanmod.motion.EntityMotionGeometry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = {"net.minecraft.client.renderer.SubmitNodeStorage$ModelSubmit",
        "net.minecraft.client.renderer.SubmitNodeStorage$ModelPartSubmit",
        "net.minecraft.client.renderer.SubmitNodeStorage$MovingBlockSubmit",
        "net.minecraft.client.renderer.SubmitNodeStorage$BlockModelSubmit",
        "net.minecraft.client.renderer.SubmitNodeStorage$ItemSubmit",
        "net.minecraft.client.renderer.SubmitNodeStorage$CustomGeometrySubmit"})
abstract class SubmitIdentityMixin {
    @Inject(method = "<init>", at = @At("RETURN"))
    private void salts$identity(CallbackInfo info) { EntityMotionGeometry.tagSubmission(this); }
}
