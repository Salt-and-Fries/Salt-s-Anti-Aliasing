package org.betterLostItems.salts_anti_aliasing.client.render.vulkan;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.lwjgl.util.shaderc.Shaderc;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/** Compiles the actual shipped shaders using the same SPIR-V requirements as RenderPearl. */
final class ShaderCompilationTest {
    @ParameterizedTest
    @ValueSource(strings = {
            "particle_motion.vsh", "particle_motion", "dlss_motion_vectors", "edge_debug", "fsr1_upscale", "fsr_linear_to_srgb",
            "fsr_motion_vectors", "fsr_srgb_to_linear", "fsr_decode_pair", "fsr_temporal_masks", "fxaa",
            "hand_background_copy", "presentation_motion", "entity_motion.vsh", "entity_motion", "taa_display", "temporal_debug", "nis_sharpen", "nis_upscale", "smaa_edge_detect", "smaa_neighborhood_blend",
            "smaa_weight_blend", "ssaa_resolve", "taa_resolve", "temporal_color_rejection"
    })
    void compilesFragmentShaderToSpirv(String shaderName) throws IOException {
        String source = readResource("post/" + shaderName + (shaderName.endsWith(".vsh")?"":".fsh"))
                .replace("#include <salts_anti_aliasing:ffx_a.glsl>", readResource("include/ffx_a.glsl"))
                .replace("#include <salts_anti_aliasing:ffx_fsr1.glsl>", readResource("include/ffx_fsr1.glsl"))
                .replace("#include <salts_anti_aliasing:nis_scaler.glsl>", readResource("include/nis_scaler.glsl"))
                .replace("#include <salts_anti_aliasing:smaa.glsl>", readResource("include/smaa.glsl"))
                .replace("#include <salts_anti_aliasing:temporal_motion.glsl>", readResource("include/temporal_motion.glsl"))
                .replace("#include <salts_anti_aliasing:temporal_color_rejection.glsl>", readResource("include/temporal_color_rejection.glsl"));
        long compiler = Shaderc.shaderc_compiler_initialize();
        assertNotEquals(0L, compiler);
        long options = Shaderc.shaderc_compile_options_initialize();
        try {
            Shaderc.shaderc_compile_options_set_target_env(options,
                    Shaderc.shaderc_target_env_vulkan, Shaderc.shaderc_env_version_vulkan_1_2);
            Shaderc.shaderc_compile_options_set_auto_bind_uniforms(options, true);
            long result = Shaderc.shaderc_compile_into_spv(compiler, source,
                    shaderName.endsWith(".vsh")?Shaderc.shaderc_vertex_shader:Shaderc.shaderc_fragment_shader, shaderName, "main", options);
            try {
                assertNotEquals(0L, result);
                assertEquals(Shaderc.shaderc_compilation_status_success,
                        Shaderc.shaderc_result_get_compilation_status(result),
                        () -> Shaderc.shaderc_result_get_error_message(result));
            } finally {
                Shaderc.shaderc_result_release(result);
            }
        } finally {
            Shaderc.shaderc_compile_options_release(options);
            Shaderc.shaderc_compiler_release(compiler);
        }
    }

    private static String readResource(String shaderPath) throws IOException {
        String path = "/assets/salts_anti_aliasing/shaders/" + shaderPath;
        try (InputStream input = ShaderCompilationTest.class.getResourceAsStream(path)) {
            assertNotNull(input, () -> "Missing shader: " + path);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
