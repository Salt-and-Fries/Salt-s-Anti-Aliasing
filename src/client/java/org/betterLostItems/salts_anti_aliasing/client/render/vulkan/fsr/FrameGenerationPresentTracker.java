package org.betterLostItems.salts_anti_aliasing.client.render.vulkan.fsr;

/**
 * Compares game-frame presents with the replacement swapchain's real display-present counter.
 */
final class FrameGenerationPresentTracker {
    private long gamePresentCount;
    private long sdkPresentCount = -1L;
    private boolean outputConfirmed;
    private long previousPresentNs;
    private double lastIntervalMs;
    private boolean rateWindowStarted;
    private long rateWindowNs, rateGameStart, rateSdkStart;
    private double renderedFps = -1, presentedFps = -1;
    double lastIntervalMs() { return lastIntervalMs; }
    double renderedFps() { return renderedFps; }
    double presentedFps() { return presentedFps; }

    boolean recordSuccessfulGamePresent(long observedSdkPresentCount) {
        return recordSuccessfulGamePresent(observedSdkPresentCount, System.nanoTime());
    }

    boolean recordSuccessfulGamePresent(long observedSdkPresentCount, long now) {
        lastIntervalMs=previousPresentNs==0?0:(now-previousPresentNs)/1000000.0;
        previousPresentNs=now;
        gamePresentCount++;
        if (observedSdkPresentCount < 0 || observedSdkPresentCount < sdkPresentCount) {
            rateWindowStarted=false;
            renderedFps=presentedFps=-1;
        }
        sdkPresentCount = observedSdkPresentCount;
        if (sdkPresentCount >= 0) {
            if (!rateWindowStarted) {
                rateWindowStarted=true;
                rateWindowNs=now;
                rateGameStart=gamePresentCount;
                rateSdkStart=sdkPresentCount;
            } else if (now-rateWindowNs >= 1_000_000_000L) {
                double seconds=(now-rateWindowNs)/1_000_000_000.0;
                renderedFps=(gamePresentCount-rateGameStart)/seconds;
                presentedFps=(sdkPresentCount-rateSdkStart)/seconds;
                rateWindowNs=now;
                rateGameStart=gamePresentCount;
                rateSdkStart=sdkPresentCount;
            }
        }
        boolean wasConfirmed = outputConfirmed;
        outputConfirmed = outputConfirmed || sdkPresentCount > gamePresentCount;
        return outputConfirmed && !wasConfirmed;
    }

    void reset() {
        previousPresentNs=0;lastIntervalMs=0;
        gamePresentCount = 0L;
        sdkPresentCount = -1L;
        outputConfirmed = false;
        rateWindowStarted=false;
        renderedFps=presentedFps=-1;
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
