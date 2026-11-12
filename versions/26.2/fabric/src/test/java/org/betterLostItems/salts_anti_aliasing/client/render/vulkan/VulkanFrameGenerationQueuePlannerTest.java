package org.betterLostItems.salts_anti_aliasing.client.render.vulkan;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class VulkanFrameGenerationQueuePlannerTest {
    @Test
    void rejectsADeviceWithoutThreePrivateQueues() {
        List<VulkanFrameGenerationQueuePlanner.QueueFamily> families = List.of(
                family(0, 7, 3, 3, true)
        );

        assertTrue(VulkanFrameGenerationQueuePlanner.plan(families, queue(0, 0)).isEmpty());
    }

    @Test
    void allocatesThreeDistinctQueuesAfterMinecraftsQueues() {
        List<VulkanFrameGenerationQueuePlanner.QueueFamily> families = List.of(
                family(0, 7, 6, 3, true)
        );

        VulkanFrameGenerationQueuePlanner.Plan plan = VulkanFrameGenerationQueuePlanner
                .plan(families, queue(0, 0))
                .orElseThrow();

        assertEquals(queue(0, 3), plan.present());
        assertEquals(queue(0, 4), plan.asyncCompute());
        assertEquals(queue(0, 5), plan.imageAcquire());
        assertDistinct(plan);
    }

    @Test
    void prefersDedicatedComputeAndTransferFamilies() {
        List<VulkanFrameGenerationQueuePlanner.QueueFamily> families = List.of(
                family(0, 7, 2, 1, true),
                family(1, 2, 2, 1, false),
                family(2, 4, 2, 1, false)
        );

        VulkanFrameGenerationQueuePlanner.Plan plan = VulkanFrameGenerationQueuePlanner
                .plan(families, queue(0, 0))
                .orElseThrow();

        assertEquals(queue(0, 1), plan.present());
        assertEquals(queue(1, 1), plan.asyncCompute());
        assertEquals(queue(2, 1), plan.imageAcquire());
        assertDistinct(plan);
    }

    @Test
    void backtracksWhenTheFirstPresentCandidateIsNeededForCompute() {
        List<VulkanFrameGenerationQueuePlanner.QueueFamily> families = List.of(
                family(0, 4, 2, 1, true),
                family(1, 3, 2, 1, true),
                family(2, 4, 2, 1, false)
        );

        VulkanFrameGenerationQueuePlanner.Plan plan = VulkanFrameGenerationQueuePlanner
                .plan(families, queue(1, 0))
                .orElseThrow();

        assertEquals(queue(0, 1), plan.present());
        assertEquals(queue(1, 1), plan.asyncCompute());
        assertEquals(queue(2, 1), plan.imageAcquire());
        assertDistinct(plan);
    }

    private static VulkanFrameGenerationQueuePlanner.QueueFamily family(
            int index,
            int flags,
            int queueCount,
            int requestedCount,
            boolean presentationSupported
    ) {
        return new VulkanFrameGenerationQueuePlanner.QueueFamily(
                index,
                flags,
                queueCount,
                requestedCount,
                presentationSupported
        );
    }

    private static VulkanFrameGenerationQueuePlanner.QueueRef queue(int family, int index) {
        return new VulkanFrameGenerationQueuePlanner.QueueRef(family, index);
    }

    private static void assertDistinct(VulkanFrameGenerationQueuePlanner.Plan plan) {
        assertNotEquals(plan.asyncCompute(), plan.present());
        assertNotEquals(plan.asyncCompute(), plan.imageAcquire());
        assertNotEquals(plan.present(), plan.imageAcquire());
    }
}
