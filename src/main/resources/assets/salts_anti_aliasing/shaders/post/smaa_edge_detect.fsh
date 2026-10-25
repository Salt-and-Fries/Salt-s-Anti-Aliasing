#version 450
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

layout(location = 0) in vec2 texCoord;
layout(location = 0) out vec4 fragColor;

#define SMAA_GLSL_3 1
layout(std140) uniform SmaaConfig {
    float EdgeThreshold;
    float SearchSteps;
    float DiagonalSteps;
    float CornerRounding;
};
#define SMAA_THRESHOLD EdgeThreshold
#define SMAA_MAX_SEARCH_STEPS int(SearchSteps)
#define SMAA_MAX_SEARCH_STEPS_DIAG int(DiagonalSteps)
#define SMAA_CORNER_ROUNDING int(CornerRounding)
#define SMAA_RT_METRICS vec4(1.0 / InSize, InSize)
#include <salts_anti_aliasing:smaa.glsl>

void main() {
    vec4 offsets[3];
    SMAAEdgeDetectionVS(texCoord, offsets);
    vec2 edges = SMAAColorEdgeDetectionPS(texCoord, offsets, InSampler);
    fragColor = vec4(edges, 0.0, 1.0);
}
