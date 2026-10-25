package org.betterLostItems.salts_anti_aliasing.client.render.vulkan.dlss;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

final class DlssOptimalSettingsTest {
    private static DlssOptimalSettings settings(int width, int height) {
        return new DlssOptimalSettings(width, height, width, height, width, height, 0);
    }

    @Test
    void coversTheOutputPixelAreaForEveryDlssScale() {
        assertEquals(8, settings(1920, 1080).jitterPhaseCount(1920, 1080));
        assertEquals(18, settings(1280, 720).jitterPhaseCount(1920, 1080));
        assertEquals(24, settings(1114, 626).jitterPhaseCount(1920, 1080));
        assertEquals(32, settings(960, 540).jitterPhaseCount(1920, 1080));
        assertEquals(72, settings(640, 360).jitterPhaseCount(1920, 1080));
    }

    @Test
    void usesActualRoundedDimensionsAndAvoidsIntegerOverflowAtHighResolution() {
        assertEquals(19, settings(853, 480).jitterPhaseCount(1280, 720));
        assertEquals(18, settings(1707, 960).jitterPhaseCount(2560, 1440));
        assertEquals(72, settings(10240, 5760).jitterPhaseCount(30720, 17280));
        assertEquals(8, settings(0, 0).jitterPhaseCount(1920, 1080));
    }
}
