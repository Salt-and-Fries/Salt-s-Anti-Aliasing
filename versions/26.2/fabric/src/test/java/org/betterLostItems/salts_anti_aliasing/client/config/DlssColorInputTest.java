package org.betterLostItems.salts_anti_aliasing.client.config;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class DlssColorInputTest {
    @Test void automaticUsesTheUpstreamTargetAndOverridesAreExplicit() {
        assertFalse(DlssColorInput.AUTO.linear(false));
        assertTrue(DlssColorInput.AUTO.linear(true));
        assertFalse(DlssColorInput.PERCEPTUAL_SDR.linear(true));
        assertTrue(DlssColorInput.LINEAR_HDR.linear(false));
        var tuning = new RenderTuning(); tuning.dlssColorInput = null; tuning.sanitize();
        assertEquals(DlssColorInput.AUTO, tuning.dlssColorInput);
    }
}
