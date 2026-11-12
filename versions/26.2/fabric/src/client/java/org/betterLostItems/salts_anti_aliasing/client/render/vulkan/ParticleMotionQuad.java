package org.betterLostItems.salts_anti_aliasing.client.render.vulkan;

import org.joml.Quaternionfc;
import org.joml.Vector3f;

/** The same corner order and billboard transform used by vanilla QuadParticleRenderState. */
public final class ParticleMotionQuad {
    private ParticleMotionQuad() {}

    public static float[] positions(float x, float y, float z, Quaternionfc rotation, float scale) {
        float[] result = new float[12];
        int[] nx = {1, 1, -1, -1}, ny = {-1, 1, 1, -1};
        var point = new Vector3f();
        for (int i = 0; i < 4; i++) {
            point.set(nx[i], ny[i], 0).rotate(rotation).mul(scale).add(x, y, z);
            result[i * 3] = point.x;
            result[i * 3 + 1] = point.y;
            result[i * 3 + 2] = point.z;
        }
        return result;
    }
}
