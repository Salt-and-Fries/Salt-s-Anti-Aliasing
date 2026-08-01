#version 330
/*
 * SMAA 1x Ultra edge detection, adapted from the official iryoku/smaa GLSL
 * reference implementation. Color edge detection catches equal-luma chromatic
 * boundaries that the cheaper luma path can miss.
 */

uniform sampler2D InSampler;

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
    vec4 offsets[3];
    SMAAEdgeDetectionVS(texCoord, offsets);
    vec2 edges = SMAAColorEdgeDetectionPS(texCoord, offsets, InSampler);
    fragColor = vec4(edges, 0.0, 1.0);
}
