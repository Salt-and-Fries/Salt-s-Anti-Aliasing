#version 330

uniform sampler2D InSampler;
uniform sampler2D DepthSampler;
uniform sampler2D EdgeMaskSampler;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(std140) uniform RcasConfig {
    float Sharpness;
    float EdgeLimit;
    float ClampBoost;
    float AvoidTextures;
};

in vec2 texCoord;

out vec4 fragColor;

float maxComponent(vec3 value) {
    return max(value.r, max(value.g, value.b));
}

float sampleGeometryMask(vec2 texel) {
    float mask = texture(EdgeMaskSampler, texCoord).r;
    mask = max(mask, texture(EdgeMaskSampler, texCoord + vec2(texel.x, 0.0)).r);
    mask = max(mask, texture(EdgeMaskSampler, texCoord + vec2(-texel.x, 0.0)).r);
    mask = max(mask, texture(EdgeMaskSampler, texCoord + vec2(0.0, texel.y)).r);
    mask = max(mask, texture(EdgeMaskSampler, texCoord + vec2(0.0, -texel.y)).r);
    mask = max(mask, texture(EdgeMaskSampler, texCoord + vec2(texel.x, texel.y)).r * 0.75);
    mask = max(mask, texture(EdgeMaskSampler, texCoord + vec2(-texel.x, texel.y)).r * 0.75);
    mask = max(mask, texture(EdgeMaskSampler, texCoord + vec2(texel.x, -texel.y)).r * 0.75);
    mask = max(mask, texture(EdgeMaskSampler, texCoord + vec2(-texel.x, -texel.y)).r * 0.75);
    return clamp(mask, 0.0, 1.0);
}

void main() {
    vec2 texel = 1.0 / InSize;

    vec3 center = texture(InSampler, texCoord).rgb;
    float depthCenter = texture(DepthSampler, texCoord).r;
    vec3 north = texture(InSampler, texCoord + vec2(0.0, -texel.y)).rgb;
    vec3 south = texture(InSampler, texCoord + vec2(0.0, texel.y)).rgb;
    vec3 west = texture(InSampler, texCoord + vec2(-texel.x, 0.0)).rgb;
    vec3 east = texture(InSampler, texCoord + vec2(texel.x, 0.0)).rgb;
    float depthN = texture(DepthSampler, texCoord + vec2(0.0, -texel.y)).r;
    float depthS = texture(DepthSampler, texCoord + vec2(0.0, texel.y)).r;
    float depthW = texture(DepthSampler, texCoord + vec2(-texel.x, 0.0)).r;
    float depthE = texture(DepthSampler, texCoord + vec2(texel.x, 0.0)).r;

    vec3 minNeighborhood = min(center, min(min(north, south), min(west, east)));
    vec3 maxNeighborhood = max(center, max(max(north, south), max(west, east)));
    vec3 range = maxNeighborhood - minNeighborhood;

    float contrast = maxComponent(range);
    float adaptiveMask = 1.0 - smoothstep(0.04, EdgeLimit, contrast);
    float sharpenAmount = Sharpness * mix(1.15, 2.2, adaptiveMask);

    vec3 ring = (north + south + west + east) * 0.25;
    vec3 sharpened = center + (center - ring) * sharpenAmount;

    vec3 clampPad = range * ClampBoost;
    vec3 outputColor = clamp(sharpened, minNeighborhood - clampPad, maxNeighborhood + clampPad);
    float geometryMask = sampleGeometryMask(texel);
    outputColor = mix(outputColor, center, step(0.5, AvoidTextures) * (1.0 - geometryMask));
    fragColor = vec4(outputColor, 1.0);
}
