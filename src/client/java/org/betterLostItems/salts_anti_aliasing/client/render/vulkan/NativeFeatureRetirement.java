package org.betterLostItems.salts_anti_aliasing.client.render.vulkan;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.commands.GpuFence;

/** Small reuse window followed by nonblocking retirement after GPU completion. */
final class NativeFeatureRetirement {
    private GpuFence fence;
    private boolean used;
    private int inactiveFrames;

    void active() {
        used = true;
        inactiveFrames = 0;
        if (fence != null) {
            fence.close();
            fence = null;
        }
    }

    void inactive(Runnable release) {
        if (!used) return;
        if (fence == null) {
            var encoder = RenderSystem.getDevice().createCommandEncoder();
            fence = encoder.createFence();
            encoder.submit();
        }
        if (++inactiveFrames < 3 || !fence.awaitCompletion(0)) return;
        fence.close();
        fence = null;
        used = false;
        release.run();
    }
}
