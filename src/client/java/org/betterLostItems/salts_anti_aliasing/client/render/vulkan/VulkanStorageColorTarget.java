package org.betterLostItems.salts_anti_aliasing.client.render.vulkan;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;

/**
 * Render target whose color texture may be used as a Vulkan storage image.
 */
final class VulkanStorageColorTarget extends RenderTarget {
    private static final int COLOR_USAGE = GpuTexture.USAGE_TEXTURE_BINDING
            | GpuTexture.USAGE_RENDER_ATTACHMENT
            | GpuTexture.USAGE_COPY_SRC
            | GpuTexture.USAGE_COPY_DST
            | VulkanStorageTextureUsage.USAGE_STORAGE;

    VulkanStorageColorTarget(String label, int width, int height, GpuFormat format) {
        super(label, false, format);
        RenderSystem.assertOnRenderThread();
        resize(width, height);
    }

    @Override
    public void createBuffers(int width, int height) {
        RenderSystem.assertOnRenderThread();
        GpuDevice device = RenderSystem.getDevice();
        int maxTextureSize = device.getDeviceInfo().limits().maxTextureSize();
        if (width <= 0 || width > maxTextureSize || height <= 0 || height > maxTextureSize) {
            throw new IllegalArgumentException(
                    "Window " + width + "x" + height + " size out of bounds (max. size: " + maxTextureSize + ")"
            );
        }

        this.width = width;
        this.height = height;
        this.colorTexture = device.createTexture(
                () -> this.label + " / Color",
                COLOR_USAGE,
                this.format,
                width,
                height,
                1,
                1
        );
        this.colorTextureView = device.createTextureView(this.colorTexture);
    }
}
