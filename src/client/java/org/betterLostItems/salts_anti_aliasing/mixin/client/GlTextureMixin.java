package org.betterLostItems.salts_anti_aliasing.mixin.client;

import com.mojang.blaze3d.opengl.DirectStateAccess;
import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.textures.GpuTexture;
import org.betterLostItems.salts_anti_aliasing.client.platform.modern.ModernMinecraftHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Hooks direct OpenGL texture framebuffer requests, including Sodium terrain rendering, so the
 * main scene target can be redirected into the active MSAA framebuffer.
 */
@Mixin(GlTexture.class)
public abstract class GlTextureMixin {
    /**
     * Redirects main-target framebuffer lookups into the multisampled scene FBO when MSAA is active.
     */
    @Inject(method = "getFbo", at = @At("RETURN"), cancellable = true)
    private void saltsAntiAliasing$redirectMainTextureFramebuffer(
            DirectStateAccess directStateAccess,
            GpuTexture depthTexture,
            CallbackInfoReturnable<Integer> callbackInfo
    ) {
        Integer overrideFramebufferId = ModernMinecraftHooks.overrideFramebuffer(
                (GpuTexture) (Object) this,
                depthTexture,
                callbackInfo.getReturnValueI()
        );
        if (overrideFramebufferId != null) {
            callbackInfo.setReturnValue(overrideFramebufferId);
        }
    }
}
