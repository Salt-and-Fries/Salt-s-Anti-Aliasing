#version 450
// NVIDIA Image Scaling reference SDR scaler, adapted to Minecraft's fullscreen fragment pass.
uniform sampler2D InSampler;
layout(std140) uniform SamplerInfo { vec2 OutSize; vec2 InSize; };
layout(location = 0) in vec2 texCoord;
layout(location = 0) out vec4 fragColor;
#include <salts_anti_aliasing:nis_scaler.glsl>
float getY(vec3 color) { return dot(color, vec3(0.2126, 0.7152, 0.0722)); }
void main() {
    vec2 sourcePosition = (floor(texCoord * OutSize) + 0.5) * InSize / OutSize - 0.5;
    ivec2 base = ivec2(floor(sourcePosition));
    vec2 phase = fract(sourcePosition);
    ivec2 phaseIndex = clamp(ivec2(phase * 64.0), ivec2(0), ivec2(63));
    float support[6][6];
    for (int i = 0; i < 6; ++i) {
        for (int j = 0; j < 6; ++j) {
            ivec2 position = clamp(base + ivec2(j - 2, i - 2), ivec2(0), ivec2(InSize) - 1);
            support[i][j] = getY(texelFetch(InSampler, position, 0).rgb);
        }
    }
    float edgeSupport[4][4];
    for (int i = 0; i < 4; ++i)
        for (int j = 0; j < 4; ++j) edgeSupport[i][j] = support[i + 1][j + 1];
    vec4 edges[2][2];
    for (int i = 0; i < 2; ++i)
        for (int j = 0; j < 2; ++j) edges[i][j] = GetEdgeMap(edgeSupport, i, j);
    vec4 weights = GetInterpEdgeMap(edges, phase.x, phase.y);
    float baseWeight = 1.0 - weights.x - weights.y - weights.z - weights.w;
    float outputY = FilterNormal(support, phaseIndex.x, phaseIndex.y) * baseWeight
            + AddDirFilters(support, phase.x, phase.y, phaseIndex.x, phaseIndex.y, weights);
    vec4 color = texture(InSampler, (sourcePosition + 0.5) / InSize);
    fragColor = vec4(clamp(color.rgb + (outputY - getY(color.rgb)), 0.0, 1.0), color.a);
}
