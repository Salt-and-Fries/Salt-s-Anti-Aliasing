package org.betterLostItems.salts_anti_aliasing.vulkanmod.gpu;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.lwjgl.util.shaderc.Shaderc;

import static org.junit.jupiter.api.Assertions.*;

class VulkanShaderCompilerTest {
    @ParameterizedTest
    @ValueSource(strings = {"dlss_motion_vectors", "edge_debug", "fsr1_upscale", "fsr_linear_to_srgb",
            "fsr_motion_vectors", "fsr_srgb_to_linear", "fsr_decode_pair", "fsr_temporal_masks", "fxaa",
            "hand_background_copy", "presentation_motion", "entity_motion", "taa_display", "temporal_debug",
            "nis_sharpen", "nis_upscale", "smaa_edge_detect", "smaa_neighborhood_blend", "smaa_weight_blend",
            "ssaa_resolve", "taa_resolve", "temporal_color_rejection", "sgsr1_standard", "sgsr1_edge",
            "sgsr2_convert", "sgsr2_upscale", "sgsr2_entity_motion", "sgsr2_particle_motion", "particle_motion"})
    void compilesProductionAlgorithmForOldestVulkanTarget(String shader) {
        var result = VulkanShaderCompiler.fragment(shader);
        assertTrue(result.spirv().length > 20);
        assertTrue(result.reflection().colorOutputs() > 0);
        assertFalse(result.reflection().descriptors().isEmpty());
    }

    @Test void preservesActualUniformPaddingAndSamplerBindings() {
        var reflection = VulkanShaderCompiler.compile("layout-test", """
                #version 450
                uniform sampler2D Source;
                layout(std140) uniform Config { float strength; vec3 color; mat4 transform; vec2 size; };
                layout(location=0) out vec4 result;
                void main() { result = texture(Source, size) * vec4(color, strength) * transform; }
                """, Shaderc.shaderc_fragment_shader).reflection();
        var block = reflection.descriptors().stream().filter(d -> d.name().equals("Config")).findFirst().orElseThrow();
        assertEquals(SpirvReflection.Kind.UNIFORM_BUFFER, block.kind());
        assertEquals(112, block.bytes());
        assertEquals(java.util.List.of(0, 16, 32, 96), block.members().stream().map(SpirvReflection.Member::offset).toList());
        assertTrue(reflection.descriptors().stream().anyMatch(d -> d.name().equals("Source")
                && d.kind() == SpirvReflection.Kind.SAMPLED_IMAGE && d.binding() != block.binding()));
    }

    @Test void fullscreenVertexDoesNotRequireMinecraftVertexFormats() {
        assertTrue(VulkanShaderCompiler.fullscreenVertex().reflection().descriptors().isEmpty());
    }

    @Test void malformedSpirvIsRejectedBeforeNativePipelineCreation() {
        assertThrows(IllegalArgumentException.class, () -> SpirvReflection.read(new byte[20]));
    }

    @Test void legacyTerrainBiasCompilesWithoutChangingUniformLayoutOrCutoutThresholds() {
        var source = """
                #version 450
                layout(binding=2) uniform sampler2D Sampler0;
                layout(binding=1) uniform UBO {
                    vec4 FogColor;
                    float FogEnvironmentalStart, FogEnvironmentalEnd;
                    float FogRenderDistanceStart, FogRenderDistanceEnd;
                    float FogSkyEnd, FogCloudsEnd;
                    float AlphaCutout;
                };
                layout(location=0) in vec2 texCoord0;
                layout(location=1) in float sphericalVertexDistance;
                layout(location=0) out vec4 fragColor;
                void main() {
                    vec4 color = texture(Sampler0, texCoord0);
                    if (color.a < AlphaCutout) discard;
                    fragColor = color;
                }
                """;
        var original = VulkanShaderCompiler.compile("terrain-original", source, Shaderc.shaderc_fragment_shader).reflection();
        var patched = VulkanShaderCompiler.compile("terrain-biased", TerrainTextureDetail.patchLegacy(source), Shaderc.shaderc_fragment_shader).reflection();
        var before = original.descriptors().stream().filter(d -> d.kind() == SpirvReflection.Kind.UNIFORM_BUFFER).findFirst().orElseThrow();
        var after = patched.descriptors().stream().filter(d -> d.kind() == SpirvReflection.Kind.UNIFORM_BUFFER).findFirst().orElseThrow();
        assertEquals(before.bytes(), after.bytes());
        assertEquals(before.members(), after.members());
        for (int steps = -12; steps <= 4; steps++) for (float cutoff : new float[]{0, .1f, .5f, 1}) {
            float encoded = cutoff + steps * 2f;
            int decodedSteps = (int) Math.floor(encoded * .5f);
            assertEquals(steps, decodedSteps);
            assertEquals(cutoff, encoded - decodedSteps * 2f, 0.000002f);
        }
    }

    @Test void classicTerrainAddsBiasAfterTheOriginalFogFieldsAndPreservesCutout() {
        var source = """
                #version 450
                layout(binding=2) uniform sampler2D Sampler0;
                layout(binding=1) uniform UBO {
                    vec4 ColorModulator;
                    vec4 FogColor;
                    float FogStart;
                    float FogEnd;
                };
                layout(location=0) in vec2 texCoord0;
                layout(location=0) out vec4 fragColor;
                void main() {
                    vec4 color = texture(Sampler0, texCoord0) * ColorModulator;
                    if (color.a < 0.5) discard;
                    fragColor = color;
                }
                """;
        var patched = TerrainTextureDetail.patchClassic(source);
        var reflection = VulkanShaderCompiler.compile("classic-terrain-biased", patched,
                Shaderc.shaderc_fragment_shader).reflection();
        var block = reflection.descriptors().stream()
                .filter(d -> d.kind() == SpirvReflection.Kind.UNIFORM_BUFFER).findFirst().orElseThrow();
        assertEquals(java.util.List.of(0, 16, 32, 36, 40),
                block.members().stream().map(SpirvReflection.Member::offset).toList());
        assertTrue(patched.contains("if (color.a < 0.5) discard;"));
        assertEquals(patched, TerrainTextureDetail.patchClassic(patched));
    }
}
