package org.betterLostItems.salts_anti_aliasing.client.render.vulkan;

import com.mojang.blaze3d.systems.RenderPassDescriptor;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;

import java.util.function.Supplier;

/**
 * Render-thread scoped Vulkan MSAA state read by mixins that patch Minecraft's Vulkan internals.
 */
public final class VulkanMsaaState {
    private static final ThreadLocal<Integer> TEXTURE_SAMPLE_COUNT = ThreadLocal.withInitial(() -> 1);
    private static final ThreadLocal<Integer> PIPELINE_SAMPLE_COUNT = ThreadLocal.withInitial(() -> 1);
    private static final ThreadLocal<Integer> RENDER_PASS_SAMPLE_COUNT = ThreadLocal.withInitial(() -> 1);

    private VulkanMsaaState() {
    }

    static <T> T withTextureSampleCount(int samples, Supplier<T> action) {
        int previousSamples = TEXTURE_SAMPLE_COUNT.get();
        TEXTURE_SAMPLE_COUNT.set(sanitize(samples));
        try {
            return action.get();
        } finally {
            TEXTURE_SAMPLE_COUNT.set(previousSamples);
        }
    }

    public static int textureSampleCount(int originalSamples) {
        int samples = TEXTURE_SAMPLE_COUNT.get();
        return samples > 1 ? samples : originalSamples;
    }

    public static int currentTextureSampleCount() {
        return TEXTURE_SAMPLE_COUNT.get();
    }

    public static void beginRenderPass(RenderPassDescriptor descriptor) {
        RENDER_PASS_SAMPLE_COUNT.set(sampleCount(descriptor));
    }

    public static void endRenderPass() {
        RENDER_PASS_SAMPLE_COUNT.set(1);
    }

    public static int currentRenderPassSampleCount() {
        return RENDER_PASS_SAMPLE_COUNT.get();
    }

    public static <T> T withPipelineSampleCount(int samples, Supplier<T> action) {
        int previousSamples = PIPELINE_SAMPLE_COUNT.get();
        PIPELINE_SAMPLE_COUNT.set(sanitize(samples));
        try {
            return action.get();
        } finally {
            PIPELINE_SAMPLE_COUNT.set(previousSamples);
        }
    }

    static void setPipelineSampleCount(int samples) {
        PIPELINE_SAMPLE_COUNT.set(sanitize(samples));
    }

    static void clearPipelineSampleCount() {
        PIPELINE_SAMPLE_COUNT.set(1);
    }

    public static int pipelineSampleCount(int originalSamples) {
        int samples = PIPELINE_SAMPLE_COUNT.get();
        return samples > 1 ? samples : originalSamples;
    }

    private static int sanitize(int samples) {
        return Math.max(1, samples);
    }

    private static int sampleCount(RenderPassDescriptor descriptor) {
        int samples = 1;
        for (RenderPassDescriptor.Attachment<?> attachment : descriptor.colorAttachments()) {
            samples = Math.max(samples, sampleCount(attachment));
        }
        samples = Math.max(samples, sampleCount(descriptor.depthAttachment()));
        return samples;
    }

    private static int sampleCount(RenderPassDescriptor.Attachment<?> attachment) {
        return attachment == null ? 1 : sampleCount(attachment.textureView());
    }

    private static int sampleCount(GpuTextureView textureView) {
        if (textureView == null) {
            return 1;
        }

        GpuTexture texture = textureView.texture();
        return texture instanceof VulkanSampledTexture sampledTexture
                ? Math.max(1, sampledTexture.saltsAntiAliasing$sampleCount())
                : 1;
    }
}
