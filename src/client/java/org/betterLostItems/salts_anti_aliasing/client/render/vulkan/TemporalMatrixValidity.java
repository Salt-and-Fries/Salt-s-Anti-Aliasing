package org.betterLostItems.salts_anti_aliasing.client.render.vulkan;

import org.joml.Matrix4fc;

/** Reject startup/transition camera matrices before inverse projection or native evaluation. */
final class TemporalMatrixValidity {
    private TemporalMatrixValidity() {}
    static boolean usable(Matrix4fc matrix) {
        if (matrix == null || !matrix.isFinite()) return false;
        float determinant = matrix.determinant();
        return Float.isFinite(determinant) && Math.abs(determinant) > 1.0e-12f;
    }
}
