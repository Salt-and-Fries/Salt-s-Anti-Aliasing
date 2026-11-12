package org.betterLostItems.salts_anti_aliasing.client.render.vulkan;

import org.joml.Matrix4f;
import org.joml.Matrix4fc;

/** Keeps GPU reprojection in the current camera's local world space. */
final class TemporalCameraMatrices {
    private final Matrix4f current = new Matrix4f();
    private final Matrix4f previous = new Matrix4f();
    private final Matrix4f jittered = new Matrix4f();
    private boolean initialized;
    private double cameraX;
    private double cameraY;
    private double cameraZ;

    void update(Matrix4fc projection, Matrix4fc jitteredProjection, Matrix4fc viewRotation,
                double x, double y, double z) {
        if (initialized) {
            // Subtract in double precision before uploading the small inter-frame translation.
            previous.set(current).translate((float) (x - cameraX), (float) (y - cameraY), (float) (z - cameraZ));
        }
        current.set(projection).mul(viewRotation);
        jittered.set(jitteredProjection).mul(viewRotation);
        if (!initialized) {
            previous.set(current);
        }
        cameraX = x;
        cameraY = y;
        cameraZ = z;
        initialized = true;
    }

    Matrix4f current() {
        return current;
    }

    Matrix4f previous() {
        return previous;
    }

    Matrix4f jittered() {
        return jittered;
    }

    void reset() {
        initialized = false;
        current.identity();
        previous.identity();
        jittered.identity();
    }
}
