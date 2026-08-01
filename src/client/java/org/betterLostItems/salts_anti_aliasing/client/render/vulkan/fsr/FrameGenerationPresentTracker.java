package org.betterLostItems.salts_anti_aliasing.client.render.vulkan.fsr;

/**
 * Compares game-frame presents with the replacement swapchain's real display-present counter.
 */
final class FrameGenerationPresentTracker {
    private long gamePresentCount;
    private long sdkPresentCount = -1L;
    private boolean outputConfirmed;

    boolean recordSuccessfulGamePresent(long observedSdkPresentCount) {
        gamePresentCount++;
        sdkPresentCount = observedSdkPresentCount;
        boolean wasConfirmed = outputConfirmed;
        outputConfirmed = outputConfirmed || sdkPresentCount > gamePresentCount;
        return outputConfirmed && !wasConfirmed;
    }

    void reset() {
        gamePresentCount = 0L;
        sdkPresentCount = -1L;
        outputConfirmed = false;
    }

    long gamePresentCount() {
        return gamePresentCount;
    }

    long sdkPresentCount() {
        return sdkPresentCount;
    }

    boolean outputConfirmed() {
        return outputConfirmed;
    }
}
