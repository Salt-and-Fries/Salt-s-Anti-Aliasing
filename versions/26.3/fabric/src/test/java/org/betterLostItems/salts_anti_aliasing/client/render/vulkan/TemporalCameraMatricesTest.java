package org.betterLostItems.salts_anti_aliasing.client.render.vulkan;

import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class TemporalCameraMatricesTest {
    private static final int WIDTH = 3840;
    private static final int HEIGHT = 2160;

    @ParameterizedTest
    @ValueSource(doubles = {0.0, 1_000_000.0, 29_999_900.0, -29_999_900.0})
    void preservesSubpixelCameraMotionAtEveryWorldPosition(double origin) {
        Matrix4f projection = projection();
        Matrix4f previousRotation = new Matrix4f().rotationYXZ(0.2f, -0.1f, 0.0f);
        Matrix4f currentRotation = new Matrix4f().rotationYXZ(0.2001f, -0.1002f, 0.0f);
        TemporalCameraMatrices matrices = new TemporalCameraMatrices();
        matrices.update(projection, projection, previousRotation, origin, 64.0, origin);
        matrices.update(projection, projection, currentRotation,
                origin + 0.015625, 64.0078125, origin - 0.03125);

        // The fixed point is defined in double world space. Independent ground truth subtracts
        // each camera position before converting the small point coordinates to float.
        double pointX = origin + 2.25;
        double pointY = 65.5;
        double pointZ = origin - 12.5;
        Vector4f currentRelative = new Vector4f((float) (pointX - origin - 0.015625),
                (float) (pointY - 64.0078125), (float) (pointZ - origin + 0.03125), 1.0f);
        Vector4f actualPrevious = project(matrices.previous(), currentRelative);
        Vector4f expectedPrevious = project(new Matrix4f(projection).mul(previousRotation),
                new Vector4f((float) (pointX - origin), (float) (pointY - 64.0),
                        (float) (pointZ - origin), 1.0f));
        assertPixelPosition(expectedPrevious, actualPrevious);
        assertTrue(matrices.current().equals(new Matrix4f(projection).mul(currentRotation), 0.0f));
    }

    @Test
    void stationaryCameraHasZeroMotionAcrossTheJitterSequence() {
        TemporalCameraMatrices matrices = new TemporalCameraMatrices();
        Matrix4f projection = projection();
        Matrix4f rotation = new Matrix4f().rotationYXZ(0.3f, 0.15f, 0.0f);
        for (int frame = 1; frame <= 16; frame++) {
            float jitterX = (halton(frame, 2) - 0.5f) * 2.0f / WIDTH;
            float jitterY = -(halton(frame, 3) - 0.5f) * 2.0f / HEIGHT;
            Matrix4f jittered = new Matrix4f().translation(jitterX, jitterY, 0).mul(projection);
            matrices.update(projection, jittered, rotation, 29_999_900, 70, -29_999_900);
            // This is the same depth unprojection and unjittered projection consumed by GLSL.
            Vector4f clip = project(matrices.jittered(), new Vector4f(1.25f, 0.75f, -20.0f, 1.0f));
            Vector4f reconstructed = new Matrix4f(matrices.jittered()).invert().transform(clip);
            reconstructed.div(reconstructed.w);
            assertPixelPosition(project(matrices.current(), reconstructed),
                    project(matrices.previous(), reconstructed));
        }
    }

    @ParameterizedTest
    @ValueSource(floats = {0.05f, 0.1f, 10.0f, 256.0f, 1024.0f})
    void reconstructsFiniteReverseZWithoutOpenGlDepthConversion(float distance) {
        TemporalCameraMatrices matrices = new TemporalCameraMatrices();
        Matrix4f projection = projection();
        matrices.update(projection, projection, new Matrix4f(), 0, 0, 0);
        Vector4f point = new Vector4f(0.0f, 0.0f, -distance, 1.0f);
        Vector4f clip = project(matrices.current(), point);
        assertTrue(clip.z >= -0.000001f && clip.z <= 1.000001f);
        Vector4f reconstructed = new Matrix4f(matrices.jittered()).invert().transform(clip);
        reconstructed.div(reconstructed.w);
        assertEquals(point.z, reconstructed.z, Math.max(0.00001f, distance * 0.000001f));
    }

    @Test
    void skyReprojectionCancelsTranslationAndRetainsRotation() {
        TemporalCameraMatrices matrices = new TemporalCameraMatrices();
        Matrix4f projection = projection();
        Matrix4f previousRotation = new Matrix4f().rotationY(0.05f);
        matrices.update(projection, projection, previousRotation, 1_000_000, 64, 1_000_000);
        matrices.update(projection, projection, new Matrix4f().rotationY(0.06f),
                1_000_002, 65, 1_000_003);
        Vector4f currentDirection = new Vector4f(2, 1, -1024, 1);
        Vector4f previousDirection = new Vector4f(currentDirection).add(-2, -1, -3, 0);
        assertPixelPosition(project(new Matrix4f(projection).mul(previousRotation), currentDirection),
                project(matrices.previous(), previousDirection));
    }

    @Test
    void resetStartsASeparateCameraHistory() {
        TemporalCameraMatrices matrices = new TemporalCameraMatrices();
        matrices.update(projection(), projection(), new Matrix4f(), 0, 64, 0);
        matrices.reset();
        matrices.update(projection(), projection(), new Matrix4f().rotationY(1.0f), 29_999_900, 100, 1000);
        assertTrue(matrices.current().equals(matrices.previous(), 0.0f));
    }

    private static Matrix4f projection() {
        // Minecraft 26.3 swaps the near/far arguments to JOML's zero-to-one perspective.
        return new Matrix4f().setPerspective((float) Math.toRadians(70), (float) WIDTH / HEIGHT,
                1024.0f, 0.05f, true);
    }

    private static Vector4f project(Matrix4f matrix, Vector4f position) {
        Vector4f result = matrix.transform(new Vector4f(position));
        return result.div(result.w);
    }

    private static void assertPixelPosition(Vector4f expected, Vector4f actual) {
        assertEquals(expected.x * WIDTH / 2.0f, actual.x * WIDTH / 2.0f, 0.001f);
        assertEquals(expected.y * HEIGHT / 2.0f, actual.y * HEIGHT / 2.0f, 0.001f);
        assertEquals(expected.z, actual.z, 0.000001f);
    }

    private static float halton(int index, int radix) {
        float result = 0;
        float fraction = 1.0f / radix;
        while (index > 0) {
            result += fraction * (index % radix);
            index /= radix;
            fraction /= radix;
        }
        return result;
    }
}
