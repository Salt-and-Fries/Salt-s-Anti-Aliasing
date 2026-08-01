package org.betterLostItems.salts_anti_aliasing.client.render.vulkan.fsr;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class FrameGenerationPresentTrackerTest {
    @Test
    void confirmsOnlyWhenTheSdkActuallyPresentsMoreFramesThanTheGame() {
        FrameGenerationPresentTracker tracker = new FrameGenerationPresentTracker();

        assertFalse(tracker.recordSuccessfulGamePresent(1L));
        assertFalse(tracker.outputConfirmed());
        assertTrue(tracker.recordSuccessfulGamePresent(3L));
        assertTrue(tracker.outputConfirmed());
    }

    @Test
    void delayedSdkCounterStillConfirmsOnALaterFrame() {
        FrameGenerationPresentTracker tracker = new FrameGenerationPresentTracker();

        assertFalse(tracker.recordSuccessfulGamePresent(0L));
        assertFalse(tracker.recordSuccessfulGamePresent(2L));
        assertTrue(tracker.recordSuccessfulGamePresent(4L));
    }

    @Test
    void resetStartsANewSwapchainMeasurement() {
        FrameGenerationPresentTracker tracker = new FrameGenerationPresentTracker();
        tracker.recordSuccessfulGamePresent(2L);

        tracker.reset();

        assertFalse(tracker.outputConfirmed());
        assertFalse(tracker.recordSuccessfulGamePresent(1L));
    }
}
