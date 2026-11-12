package org.betterLostItems.salts_anti_aliasing.client.render.vulkan;

import net.minecraft.client.renderer.feature.MovingBlockFeatureRenderer;

/** Adapter for shared vertex-consumer routing, including Fabric API's block tessellator. */
public interface MovingBlockMotionSource {
    MovingBlockFeatureRenderer.Submit saltsAntiAliasing$currentSubmission();
}
