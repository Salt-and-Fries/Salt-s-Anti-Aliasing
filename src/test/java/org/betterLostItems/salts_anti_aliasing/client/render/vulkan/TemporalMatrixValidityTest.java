package org.betterLostItems.salts_anti_aliasing.client.render.vulkan;

import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class TemporalMatrixValidityTest {
    @Test void rejectsZeroAndNonfiniteProjectionButAcceptsReverseZ() {
        assertFalse(TemporalMatrixValidity.usable(new Matrix4f().zero()));
        assertFalse(TemporalMatrixValidity.usable(new Matrix4f().m11(Float.NaN)));
        assertFalse(TemporalMatrixValidity.usable(new Matrix4f().m00(Float.POSITIVE_INFINITY)));
        assertTrue(TemporalMatrixValidity.usable(new Matrix4f().setPerspective(1.2f, 16f/9, 2048, .05f, true)));
    }
}
