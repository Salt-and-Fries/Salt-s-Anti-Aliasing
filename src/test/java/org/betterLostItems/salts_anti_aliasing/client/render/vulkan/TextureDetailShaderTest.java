package org.betterLostItems.salts_anti_aliasing.client.render.vulkan;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.lwjgl.util.shaderc.Shaderc;

final class TextureDetailShaderTest {
    @Test
    void automaticBiasUsesTheTrueRenderRatioAndBoundsTheVariants() {
        assertEquals(-6, TextureDetailShaderPatch.quarterSteps(true, 0, .5f, 640, 1280));
        assertEquals(-2, TextureDetailShaderPatch.quarterSteps(true, 0, .5f, 640, 1280, false));
        assertEquals(-12, TextureDetailShaderPatch.quarterSteps(true, 0, 0, 1, 16384));
        assertEquals(4, TextureDetailShaderPatch.quarterSteps(false, 99, 0, 640, 1280));
    }

    @ParameterizedTest
    @ValueSource(strings = {"-3.0", "-1.5", "0.25", "1.0"})
    void biasedGradientAndExplicitLodPathsCompileToSpirv(String bias) {
        String shader =
                TextureDetailShaderPatch.patch(
                        """
                #version 450
                uniform sampler2D Atlas;
                layout(location=0) in vec2 uv;
                layout(location=0) out vec4 color;
                void main() { color=(textureGrad(Atlas,uv,dFdx(uv),dFdy(uv))+textureLod(Atlas,uv,2.0))*0.5; }
                """);
        long compiler = Shaderc.shaderc_compiler_initialize(),
                options = Shaderc.shaderc_compile_options_initialize();
        try {
            Shaderc.shaderc_compile_options_set_auto_bind_uniforms(options, true);
            Shaderc.shaderc_compile_options_add_macro_definition(options, "SALTS_MIP_BIAS", bias);
            long result =
                    Shaderc.shaderc_compile_into_spv(
                            compiler,
                            shader,
                            Shaderc.shaderc_fragment_shader,
                            "texture-detail",
                            "main",
                            options);
            try {
                assertEquals(
                        Shaderc.shaderc_compilation_status_success,
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
}
