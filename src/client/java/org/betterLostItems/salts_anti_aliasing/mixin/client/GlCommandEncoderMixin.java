package org.betterLostItems.salts_anti_aliasing.mixin.client;

import com.mojang.blaze3d.opengl.DirectStateAccess;
import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.opengl.GlTextureView;
import com.mojang.blaze3d.textures.GpuTexture;
import org.betterLostItems.salts_anti_aliasing.client.platform.modern.ModernMinecraftHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.function.Supplier;

/**
 * Documents gl command encoder mixin behavior for Salt's Anti Aliasing. Mixin bridge code for
 * carefully scoped hooks into Minecraft rendering and options screens.
 */
@Mixin(targets = "com.mojang.blaze3d.opengl.GlCommandEncoder")
public abstract class GlCommandEncoderMixin {
    /**
     * Sends Minecraft's main scene render pass into the multisampled FBO when MSAA mode is active.
     */
    @Redirect(
            method = "createRenderPass(Ljava/util/function/Supplier;Lcom/mojang/blaze3d/textures/GpuTextureView;Ljava/util/OptionalInt;Lcom/mojang/blaze3d/textures/GpuTextureView;Ljava/util/OptionalDouble;)Lcom/mojang/blaze3d/systems/RenderPass;",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/opengl/GlTextureView;getFbo(Lcom/mojang/blaze3d/opengl/DirectStateAccess;Lcom/mojang/blaze3d/textures/GpuTexture;)I"
            )
    )
    private int saltsAntiAliasing$redirectSceneFramebuffer(
            GlTextureView colorView,
            DirectStateAccess directStateAccess,
            GpuTexture depthTexture,
            Supplier<String> labelSupplier,
            com.mojang.blaze3d.textures.GpuTextureView originalColorView,
            OptionalInt clearColor,
            com.mojang.blaze3d.textures.GpuTextureView originalDepthView,
            OptionalDouble clearDepth
    ) {
        int originalFramebufferId = colorView.getFbo(directStateAccess, depthTexture);
        Integer overrideFramebufferId = ModernMinecraftHooks.overrideFramebuffer(colorView, depthTexture, originalFramebufferId);
        return overrideFramebufferId != null ? overrideFramebufferId : originalFramebufferId;
    }

    /**
     * Sends Minecraft's main scene render pass into the multisampled FBO on the
     * older modern encoder path used by 1.21.8-1.21.9.
     *
     * <p>Those versions created the framebuffer from the underlying
     * {@link GlTexture} instead of the {@link GlTextureView}. Keeping this as a
     * second optional redirect lets MSAA keep working across the whole modern jar
     * range without making the renderer ask Minecraft which minor version is
     * active.</p>
     */
    @Redirect(
            method = "createRenderPass(Ljava/util/function/Supplier;Lcom/mojang/blaze3d/textures/GpuTextureView;Ljava/util/OptionalInt;Lcom/mojang/blaze3d/textures/GpuTextureView;Ljava/util/OptionalDouble;)Lcom/mojang/blaze3d/systems/RenderPass;",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/opengl/GlTexture;getFbo(Lcom/mojang/blaze3d/opengl/DirectStateAccess;Lcom/mojang/blaze3d/textures/GpuTexture;)I"
            ),
            require = 0
    )
    private int saltsAntiAliasing$redirectSceneFramebufferFromTexture(
            GlTexture colorTexture,
            DirectStateAccess directStateAccess,
            GpuTexture depthTexture,
            Supplier<String> labelSupplier,
            com.mojang.blaze3d.textures.GpuTextureView originalColorView,
            OptionalInt clearColor,
            com.mojang.blaze3d.textures.GpuTextureView originalDepthView,
            OptionalDouble clearDepth
    ) {
        int originalFramebufferId = colorTexture.getFbo(directStateAccess, depthTexture);
        Integer overrideFramebufferId = ModernMinecraftHooks.overrideFramebuffer(colorTexture, depthTexture, originalFramebufferId);
        return overrideFramebufferId != null ? overrideFramebufferId : originalFramebufferId;
    }

    /**
     * Mirrors color clears into the multisampled scene FBO.
     */
    @Inject(method = "clearColorTexture", at = @At("HEAD"))
    private void saltsAntiAliasing$mirrorColorClearToMsaa(GpuTexture colorTexture, int clearColor, CallbackInfo callbackInfo) {
        ModernMinecraftHooks.mirrorColorClearToMsaa(colorTexture, clearColor);
    }

    /**
     * Mirrors depth clears into the multisampled scene FBO.
     */
    @Inject(method = "clearDepthTexture", at = @At("HEAD"))
    private void saltsAntiAliasing$mirrorDepthClearToMsaa(GpuTexture depthTexture, double clearDepth, CallbackInfo callbackInfo) {
        ModernMinecraftHooks.mirrorDepthClearToMsaa(depthTexture, clearDepth);
    }

    /**
     * Mirrors combined color/depth clears into the multisampled scene FBO.
     */
    @Inject(method = "clearColorAndDepthTextures(Lcom/mojang/blaze3d/textures/GpuTexture;ILcom/mojang/blaze3d/textures/GpuTexture;D)V", at = @At("HEAD"))
    private void saltsAntiAliasing$mirrorColorDepthClearToMsaa(
            GpuTexture colorTexture,
            int clearColor,
            GpuTexture depthTexture,
            double clearDepth,
            CallbackInfo callbackInfo
    ) {
        ModernMinecraftHooks.mirrorColorDepthClearToMsaa(colorTexture, clearColor, depthTexture, clearDepth);
    }

    /**
     * Marks the end of a render pass so the MSAA controller can resolve when needed.
     */
    @Inject(method = "finishRenderPass", at = @At("TAIL"))
    private void saltsAntiAliasing$resolveAfterMainPass(CallbackInfo callbackInfo) {
        ModernMinecraftHooks.resolveMsaaAfterRenderPass();
    }
}
