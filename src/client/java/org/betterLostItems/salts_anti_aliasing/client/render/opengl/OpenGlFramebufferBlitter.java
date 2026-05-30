package org.betterLostItems.salts_anti_aliasing.client.render.opengl;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.opengl.GL30C;

/**
 * Small framebuffer copy helper for Minecraft 1.21.1's pre-GPU-texture renderer.
 */
final class OpenGlFramebufferBlitter {
    private OpenGlFramebufferBlitter() {
    }

    static void blitColor(RenderTarget source, RenderTarget target, int filter) {
        blit(source, target, GL30C.GL_COLOR_BUFFER_BIT, filter);
    }

    static void blitDepth(RenderTarget source, RenderTarget target) {
        blit(source, target, GL30C.GL_DEPTH_BUFFER_BIT, GL30C.GL_NEAREST);
    }

    private static void blit(RenderTarget source, RenderTarget target, int mask, int filter) {
        RenderSystem.assertOnRenderThread();

        GL30C.glBindFramebuffer(GL30C.GL_READ_FRAMEBUFFER, source.frameBufferId);
        GL30C.glBindFramebuffer(GL30C.GL_DRAW_FRAMEBUFFER, target.frameBufferId);
        GL30C.glBlitFramebuffer(
                0,
                0,
                source.width,
                source.height,
                0,
                0,
                target.width,
                target.height,
                mask,
                filter
        );

        GL30C.glBindFramebuffer(GL30C.GL_READ_FRAMEBUFFER, 0);
    }
}
