package org.betterLostItems.salts_anti_aliasing.client.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class FsrQualityPresetTest {
    @Test
    void usesFidelityFxJitterSequenceLengths() {
        assertEquals(8, FsrQualityPreset.NATIVE_AA.jitterPhaseCount());
        assertEquals(18, FsrQualityPreset.QUALITY.jitterPhaseCount());
        assertEquals(23, FsrQualityPreset.BALANCED.jitterPhaseCount());
        assertEquals(32, FsrQualityPreset.PERFORMANCE.jitterPhaseCount());
        assertEquals(72, FsrQualityPreset.ULTRA_PERFORMANCE.jitterPhaseCount());
    }
}
