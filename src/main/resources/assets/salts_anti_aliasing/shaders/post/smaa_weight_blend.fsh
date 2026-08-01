#version 330
/*
 * SMAA 1x Ultra blend-weight calculation, including the reference horizontal,
 * vertical, and diagonal searches, crossing-edge classification, area lookup,
 * and corner handling.
 */

uniform sampler2D EdgesSampler;
uniform sampler2D AreaSampler;
uniform sampler2D SearchSampler;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

in vec2 texCoord;
out vec4 fragColor;

#define SMAA_GLSL_3 1
#define SMAA_PRESET_ULTRA 1
#define SMAA_RT_METRICS vec4(1.0 / InSize, InSize)
#moj_import <salts_anti_aliasing:smaa.glsl>

void main() {
    vec2 pixelCoord;
    vec4 offsets[3];
    SMAABlendingWeightCalculationVS(texCoord, pixelCoord, offsets);
    fragColor = SMAABlendingWeightCalculationPS(
            texCoord,
            pixelCoord,
            offsets,
            EdgesSampler,
            AreaSampler,
            SearchSampler,
            vec4(0.0)
    );
}
