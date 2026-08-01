package org.betterLostItems.salts_anti_aliasing.client.render.vulkan.fsr;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.lwjgl.vulkan.KHRSwapchain.VK_IMAGE_LAYOUT_PRESENT_SRC_KHR;
import static org.lwjgl.vulkan.KHRSynchronization2.VK_ACCESS_2_SHADER_READ_BIT_KHR;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL;

class FrameGenerationSwapchainImagePolicyTest {
    @Test
    void keepsRegularSwapchainPresentState() {
        assertEquals(
                VK_IMAGE_LAYOUT_PRESENT_SRC_KHR,
                FrameGenerationSwapchainImagePolicy.finalLayout(false, VK_IMAGE_LAYOUT_PRESENT_SRC_KHR)
        );
        assertEquals(0L, FrameGenerationSwapchainImagePolicy.finalAccessMask(false, 0L));
    }

    @Test
    void makesFidelityFxProxyBackbufferShaderReadable() {
        assertEquals(
                VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL,
                FrameGenerationSwapchainImagePolicy.finalLayout(true, VK_IMAGE_LAYOUT_PRESENT_SRC_KHR)
        );
        assertEquals(VK_ACCESS_2_SHADER_READ_BIT_KHR, FrameGenerationSwapchainImagePolicy.finalAccessMask(true, 0L));
    }
}
