#version 330

uniform sampler2D InSampler;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(std140) uniform RcasConfig {
    float Sharpness;
    float EdgeLimit;
    float ClampBoost;
};

in vec2 texCoord;

out vec4 fragColor;

float maxComponent(vec3 value) {
    return max(value.r, max(value.g, value.b));
}

void main() {
    vec2 texel = 1.0 / InSize;

    vec3 center = texture(InSampler, texCoord).rgb;
    vec3 north = texture(InSampler, texCoord + vec2(0.0, -texel.y)).rgb;
    vec3 south = texture(InSampler, texCoord + vec2(0.0, texel.y)).rgb;
    vec3 west = texture(InSampler, texCoord + vec2(-texel.x, 0.0)).rgb;
    vec3 east = texture(InSampler, texCoord + vec2(texel.x, 0.0)).rgb;

    vec3 minNeighborhood = min(center, min(min(north, south), min(west, east)));
    vec3 maxNeighborhood = max(center, max(max(north, south), max(west, east)));
    vec3 range = maxNeighborhood - minNeighborhood;

    float contrast = maxComponent(range);
    float adaptiveMask = 1.0 - smoothstep(0.04, EdgeLimit, contrast);
    float sharpenAmount = Sharpness * mix(1.15, 2.2, adaptiveMask);

    vec3 ring = (north + south + west + east) * 0.25;
    vec3 sharpened = center + (center - ring) * sharpenAmount;

    vec3 clampPad = range * ClampBoost;
    fragColor = vec4(clamp(sharpened, minNeighborhood - clampPad, maxNeighborhood + clampPad), 1.0);
}
