#version 330

uniform sampler2D ColorSampler;
uniform sampler2D WeightsSampler;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec2 texel = 1.0 / InSize;

    vec3 center = texture(ColorSampler, texCoord).rgb;
    vec3 west = texture(ColorSampler, texCoord + vec2(-texel.x, 0.0)).rgb;
    vec3 east = texture(ColorSampler, texCoord + vec2(texel.x, 0.0)).rgb;
    vec3 north = texture(ColorSampler, texCoord + vec2(0.0, -texel.y)).rgb;
    vec3 south = texture(ColorSampler, texCoord + vec2(0.0, texel.y)).rgb;

    vec4 weights = texture(WeightsSampler, texCoord);
    float totalWeight = weights.r + weights.g + weights.b + weights.a;

    vec3 resolved = center;
    if (totalWeight > 0.0001) {
        vec3 blended =
                center +
                west * weights.r +
                east * weights.g +
                north * weights.b +
                south * weights.a;

        resolved = blended / (1.0 + totalWeight);
        vec3 minNeighborhood = min(center, min(min(west, east), min(north, south)));
        vec3 maxNeighborhood = max(center, max(max(west, east), max(north, south)));
        resolved = clamp(resolved, minNeighborhood, maxNeighborhood);
    }

    fragColor = vec4(resolved, 1.0);
}
