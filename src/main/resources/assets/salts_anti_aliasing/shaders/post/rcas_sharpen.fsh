#version 330
/*
 * Salt's Anti Aliasing post-processing shader.
 *
 * FidelityFX RCAS-style sharpening pass used after spatial upscaling to restore contrast without over-amplifying noise.
 * The JSON post-effect definitions bind these samplers and uniform blocks at runtime,
 * so the shader comments focus on the math and data flow inside the pass.
 */


// Scene, history, depth, or helper textures supplied by Minecraft's post-effect chain.
uniform sampler2D InSampler;

// Packed runtime parameters; Java updates these values each frame or whenever config changes.
layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(std140) uniform RcasConfig {
    float Sharpness;
    float EdgeLimit;
    float ClampBoost;
};

// Full-screen pass coordinates and final color output for the current pixel.
in vec2 texCoord;

out vec4 fragColor;

float maxComponent(vec3 value) {
    return max(value.r, max(value.g, value.b));
}

// Executes the per-pixel resolve/upscale/debug operation for this pass.
void main() {
    // Work in texel-relative offsets so the same shader scales across window sizes.
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
