package org.betterLostItems.salts_anti_aliasing.client.render.vulkan;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.renderpearl.api.textures.GpuTexture;

/** Logical payload of directly owned targets; excludes SDK allocations and driver overhead. */
public final class VulkanOwnedTargetMemory {
    private VulkanOwnedTargetMemory() {}

    static long bytes(RenderTarget... targets) {
        long sum = 0;
        for (var target : targets)
            if (target != null) {
                int samples =
                        target.getColorTextureView() == null
                                ? 1
                                : VulkanMsaaState.sampleCount(target.getColorTextureView());
                sum +=
                        (bytes(target.getColorTexture()) + bytes(target.getDepthTexture()))
                                * samples;
            }
        return sum;
    }

    private static long bytes(GpuTexture texture) {
        if (texture == null || texture.isClosed()) return 0;
        return (long) texture.getWidth(0) * texture.getHeight(0) * texture.getFormat().blockSize();
    }

    public static long totalBytes() {
        return VulkanSceneScaleController.instance().allocatedTargetBytes()
                + VulkanHandAaController.instance().allocatedTargetBytes()
                + VulkanSceneMsaaController.instance().allocatedTargetBytes()
                + VulkanSceneTemporalController.instance().allocatedTargetBytes()
                + VulkanSceneDlssController.instance().allocatedTargetBytes()
                + VulkanSceneFsrController.instance().allocatedTargetBytes();
    }
}
