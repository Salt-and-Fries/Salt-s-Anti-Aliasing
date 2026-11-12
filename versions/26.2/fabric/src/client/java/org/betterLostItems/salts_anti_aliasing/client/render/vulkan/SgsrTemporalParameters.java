package org.betterLostItems.salts_anti_aliasing.client.render.vulkan;

import org.joml.Matrix4f;

/** Pure math for the official SGSR 2 fragment-path uniform contract. */
public final class SgsrTemporalParameters {
    public static final int UNIFORM_BYTES = 128;
    public static final int JITTER_PHASES = 32;
    private SgsrTemporalParameters() {}

    public static Matrix4f clipToPrevious(float[] current, float[] previous) {
        // Convert the algorithm's conventional 0-near/1-far Z to Minecraft reverse-Z and back.
        Matrix4f flipZ = new Matrix4f().m22(-1.0f).m32(1.0f);
        return new Matrix4f(flipZ).mul(new Matrix4f().set(previous))
                .mul(new Matrix4f().set(current).invert()).mul(flipZ);
    }

    public static float varianceScale(int width, int height, int outputWidth, int outputHeight) {
        double areaRatio = (double) outputWidth * outputHeight / ((double) width * height);
        return (float) Math.min(20.0, areaRatio * areaRatio * areaRatio);
    }

    public static boolean cameraStill(float[] current, float[] previous) {
        double delta = 0;
        for (int i = 0; i < 16; i++) delta += Math.abs(current[i] - previous[i]);
        return delta < 1e-5;
    }
}
