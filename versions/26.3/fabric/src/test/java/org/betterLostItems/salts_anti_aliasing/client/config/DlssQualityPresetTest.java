package org.betterLostItems.salts_anti_aliasing.client.config;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

final class DlssQualityPresetTest {
    @Test
    void nativeIdsStayCompatibleWhenDlaaIsAddedBeforeQuality() {
        assertEquals(0, DlssQualityPreset.QUALITY.nativeId());
        assertEquals(1, DlssQualityPreset.BALANCED.nativeId());
        assertEquals(2, DlssQualityPreset.PERFORMANCE.nativeId());
        assertEquals(3, DlssQualityPreset.ULTRA_PERFORMANCE.nativeId());
        assertEquals(4, DlssQualityPreset.AUTO.nativeId());
        assertEquals(5, DlssQualityPreset.DLAA.nativeId());
        assertEquals(DlssQualityPreset.DLAA, DlssQualityPreset.values()[0]);
        assertEquals(DlssQualityPreset.QUALITY, DlssQualityPreset.values()[1]);
        assertEquals(DlssQualityPreset.QUALITY, DlssQualityPreset.defaultPreset());
    }

    @Test
    void modelIdsMatchStreamlineRatherThanUiOrder() {
        assertEquals(0, DlssModelPreset.AUTO.nativeId());
        assertEquals(11, DlssModelPreset.K.nativeId());
        assertEquals(13, DlssModelPreset.M.nativeId());
        assertEquals(12, DlssModelPreset.L.nativeId());
    }
}
