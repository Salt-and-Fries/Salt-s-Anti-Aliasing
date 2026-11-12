package org.betterLostItems.salts_anti_aliasing.mixin.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SkyRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** SkyRenderer caches its construction target; scene modes can replace that target later. */
@Mixin(SkyRenderer.class)
public abstract class SkyRendererMixin {
    @Redirect(method = "render", at = @At(value = "INVOKE", target =
            "Lcom/mojang/blaze3d/pipeline/RenderTarget;getColorTextureView()Lcom/mojang/renderpearl/api/textures/GpuTextureView;"))
    private GpuTextureView saltsAntiAliasing$currentSkyColor(RenderTarget cachedTarget) {
        return Minecraft.getInstance().gameRenderer.mainRenderTarget().getColorTextureView();
    }

    @Redirect(method = "render", at = @At(value = "INVOKE", target =
            "Lcom/mojang/blaze3d/pipeline/RenderTarget;getDepthTextureView()Lcom/mojang/renderpearl/api/textures/GpuTextureView;"))
    private GpuTextureView saltsAntiAliasing$currentSkyDepth(RenderTarget cachedTarget) {
        return Minecraft.getInstance().gameRenderer.mainRenderTarget().getDepthTextureView();
    }
}
