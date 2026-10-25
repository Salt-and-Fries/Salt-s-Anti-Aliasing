package org.betterLostItems.salts_anti_aliasing.client.render.vulkan;

import com.mojang.renderpearl.backend.vulkan.VulkanDevice;
import org.lwjgl.vulkan.VK12;

/** Close cached pass buffers while the backend allocator/command encoder still exist. */
public final class VulkanPassResources {
    private VulkanPassResources() {}
    public static void release(VulkanDevice device) {
        VK12.vkDeviceWaitIdle(device.vkDevice());
        VulkanParticleMotionRenderer.release();
        VulkanEntityMotionRenderer.release();
        VulkanMotionVectorRenderer.releaseAll();
        VulkanFsrTemporalMaskRenderer.release();
        VulkanTemporalColorRejectionRenderer.release();
        VulkanTemporalDebugRenderer.release();
        VulkanGpuProfiler.release();
        ParticleMotionGeometry.clear();
    }
}
