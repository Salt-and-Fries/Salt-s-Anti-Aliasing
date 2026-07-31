package org.betterLostItems.salts_anti_aliasing.client.render.vulkan;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class FsrTemporalMaskResourceTest {
    private static final String SHADER_RESOURCE =
            "/assets/salts_anti_aliasing/shaders/post/fsr_temporal_masks.fsh";

    @Test
    void temporalMaskShaderProducesSeparateReactiveAndCompositionOutputs() throws IOException {
        String shader = readResource(SHADER_RESOURCE);

        assertTrue(shader.contains("layout(location = 0) out vec4 reactiveMaskOutput"));
        assertTrue(shader.contains("layout(location = 1) out vec4 transparencyMaskOutput"));
        assertTrue(shader.contains("PreviousColorSampler"));
        assertTrue(shader.contains("PreviousOpaqueSampler"));
        assertTrue(shader.contains("MotionVectorSampler"));
        assertTrue(shader.contains("temporalCompositionGate"));
        assertTrue(shader.contains("TC_SCALE = 0.5"));
    }

    @Test
    void temporalMaskShaderRejectsTransparentChangeWithoutMarkingOrdinaryOpaqueEdges() throws IOException {
        String shader = readResource(SHADER_RESOURCE);

        assertTrue(shader.contains("temporalCompositionChange"));
        assertTrue(shader.contains("temporalEdgeChanges"));
        assertTrue(shader.contains("edgeChanges.y - edgeChanges.x"));
        assertTrue(shader.contains("REACTIVE_MAX = 0.9"));
        assertTrue(shader.contains("previousSamplePixel = reprojectedPixel(samplePixel)"));
    }

    private static String readResource(String path) throws IOException {
        try (InputStream input = FsrTemporalMaskResourceTest.class.getResourceAsStream(path)) {
            assertNotNull(input, () -> "Missing test resource: " + path);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
