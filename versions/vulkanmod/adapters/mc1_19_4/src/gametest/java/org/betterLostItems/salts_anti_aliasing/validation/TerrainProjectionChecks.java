package org.betterLostItems.salts_anti_aliasing.validation;
import org.betterLostItems.salts_anti_aliasing.vulkanmod.SaltsVulkanModClient;
import org.joml.Matrix4f;
/** Checks the matrix actually supplied to VulkanMod's terrain renderer. */
public final class TerrainProjectionChecks {
    private static long checked;
    public static long checked() { return checked; }
    public static void observe(Matrix4f projection) {
        if (!SaltsVulkanModClient.temporalActive() || !SaltsVulkanModClient.temporal().captured()) return;
        float[] expected = SaltsVulkanModClient.temporal().nativeJitteredProjection();
        float[] actual = projection.get(new float[16]);
        for (int i = 0; i < 16; i++)
            if (Math.abs(expected[i] - actual[i]) > 1e-5f)
                throw new AssertionError("Terrain projection differs from the temporal scene matrix at element " + i);
        checked++;
    }
}
