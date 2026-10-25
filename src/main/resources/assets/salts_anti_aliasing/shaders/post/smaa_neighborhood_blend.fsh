#version 450
/*
 * SMAA 1x neighborhood resolve. The reference gather chooses the dominant
 * orientation and blends the exact coverage pair with fractional offsets,
 * retaining fine texture detail and the source alpha channel.
 */

uniform sampler2D ColorSampler;
uniform sampler2D WeightsSampler;

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
    vec4 offset;
    SMAANeighborhoodBlendingVS(texCoord, offset);
    fragColor = SMAANeighborhoodBlendingPS(texCoord, offset, ColorSampler, WeightsSampler);
}
