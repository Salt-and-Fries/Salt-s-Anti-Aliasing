package org.betterLostItems.salts_anti_aliasing.client.render.vulkan;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class VulkanNativeDeviceInfoTest {
    @Test
    void coreDeviceCanRemainUsableWhenFrameGenerationQueuesAreMissing() {
        VulkanNativeDeviceInfo info = info(0L, -1, 0L, -1, 0L, -1);

        assertTrue(info.complete());
        assertFalse(info.frameGenerationQueuesComplete());
    }

    @Test
    void frameGenerationRequiresThreePrivateQueuesDistinctFromTheGameQueue() {
        VulkanNativeDeviceInfo info = info(6L, 1, 7L, 0, 8L, 2);

        assertTrue(info.frameGenerationQueuesComplete());
    }

    @Test
    void frameGenerationRejectsAnyAliasedQueueHandle() {
        VulkanNativeDeviceInfo aliasesGameQueue = info(4L, 1, 7L, 0, 8L, 2);
        VulkanNativeDeviceInfo aliasesSdkQueue = info(6L, 1, 6L, 0, 8L, 2);

        assertFalse(aliasesGameQueue.frameGenerationQueuesComplete());
        assertFalse(aliasesSdkQueue.frameGenerationQueuesComplete());
    }

    private static VulkanNativeDeviceInfo info(
            long asyncQueue,
            int asyncFamily,
            long presentQueue,
            int presentFamily,
            long acquireQueue,
            int acquireFamily
    ) {
        return new VulkanNativeDeviceInfo(
                1L,
                2L,
                3L,
                4L,
                0,
                asyncQueue,
                asyncFamily,
                presentQueue,
                presentFamily,
                acquireQueue,
                acquireFamily
        );
    }
}
