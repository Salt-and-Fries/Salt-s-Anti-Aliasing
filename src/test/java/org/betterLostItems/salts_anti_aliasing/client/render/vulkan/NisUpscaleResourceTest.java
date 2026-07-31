package org.betterLostItems.salts_anti_aliasing.client.render.vulkan;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class NisUpscaleResourceTest {
    private static final String EFFECT_RESOURCE =
            "/assets/salts_anti_aliasing/post_effect/nis_upscale.json";
    private static final String SHADER_RESOURCE =
            "/assets/salts_anti_aliasing/shaders/post/nis_upscale.fsh";

    @Test
    void upscaleEffectUsesDedicatedScalingShader() throws IOException {
        String effect = readResource(EFFECT_RESOURCE);

        assertTrue(effect.contains(
                "\"fragment_shader\": \"salts_anti_aliasing:post/nis_upscale\""
        ));
        assertFalse(effect.contains("nis_sharpen"));
        assertFalse(effect.contains("NisSharpenConfig"));
        assertFalse(effect.contains("Sharpness"));
    }

    @Test
    void scalingShaderIsEdgeAwareAndSharpeningIndependent() throws IOException {
        String shader = readResource(SHADER_RESOURCE);

        assertTrue(shader.contains("edgeStrength"));
        assertTrue(shader.contains("edgeTangent"));
        assertTrue(shader.contains("directionalReconstruction"));
        assertTrue(shader.contains("sourceScale"));
        assertFalse(shader.contains("NisSharpenConfig"));
        assertFalse(shader.contains("uniform float Sharpness"));
    }

    private static String readResource(String path) throws IOException {
        try (InputStream input = NisUpscaleResourceTest.class.getResourceAsStream(path)) {
            assertNotNull(input, () -> "Missing test resource: " + path);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
