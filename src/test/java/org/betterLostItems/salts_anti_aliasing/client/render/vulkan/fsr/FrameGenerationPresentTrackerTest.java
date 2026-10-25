package org.betterLostItems.salts_anti_aliasing.client.render.vulkan.fsr;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

final class FrameGenerationPresentTrackerTest {
    @Test
    void measuresActualOutputWithoutAssumingEveryGameFrameGeneratesAnother() {
        FrameGenerationPresentTracker tracker = new FrameGenerationPresentTracker();
        tracker.recordSuccessfulGamePresent(0,0);
        tracker.recordSuccessfulGamePresent(1,250_000_000L);
        tracker.recordSuccessfulGamePresent(3,500_000_000L);
        tracker.recordSuccessfulGamePresent(4,750_000_000L);
        tracker.recordSuccessfulGamePresent(6,1_000_000_000L);
        assertEquals(4.0,tracker.renderedFps());
        assertEquals(6.0,tracker.presentedFps());
        tracker.reset();
        assertEquals(-1,tracker.presentedFps());
    }

    @Test
    void restartsRatesWhenTheSdkCounterResetsOrBecomesUnavailable() {
        FrameGenerationPresentTracker tracker = new FrameGenerationPresentTracker();
        tracker.recordSuccessfulGamePresent(8,0);
        tracker.recordSuccessfulGamePresent(10,1_000_000_000L);
        assertEquals(2.0,tracker.presentedFps());
        tracker.recordSuccessfulGamePresent(0,1_100_000_000L);
        assertEquals(-1,tracker.presentedFps());
        tracker.recordSuccessfulGamePresent(-1,1_200_000_000L);
        assertEquals(-1,tracker.renderedFps());
    }
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
