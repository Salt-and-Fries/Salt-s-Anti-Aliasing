#version 450
// Integrate every covered supersample, including partial texels at fractional scales.
// A linear blit samples only four source texels even when the SSAA scale is much higher.
uniform sampler2D InSampler;
layout(std140) uniform SamplerInfo { vec2 OutSize; vec2 InSize; };
layout(location = 0) in vec2 texCoord;
layout(location = 0) out vec4 fragColor;
void main() {
    vec2 scale = InSize / OutSize;
    vec2 footprintStart = floor(texCoord * OutSize) * scale;
    vec2 footprintEnd = min(footprintStart + scale, InSize);
    ivec2 first = ivec2(floor(footprintStart));
    ivec2 last = ivec2(ceil(footprintEnd));
    vec4 accumulated = vec4(0.0);
    float totalWeight = 0.0;
    for (int y = first.y; y < last.y; ++y) {
        float weightY = max(0.0, min(float(y + 1), footprintEnd.y) - max(float(y), footprintStart.y));
        for (int x = first.x; x < last.x; ++x) {
            float weightX = max(0.0, min(float(x + 1), footprintEnd.x) - max(float(x), footprintStart.x));
            float weight = weightX * weightY;
            accumulated += texelFetch(InSampler, clamp(ivec2(x, y), ivec2(0), ivec2(InSize) - 1), 0) * weight;
            totalWeight += weight;
        }
    }
    fragColor = accumulated / max(totalWeight, 0.000001);
}
