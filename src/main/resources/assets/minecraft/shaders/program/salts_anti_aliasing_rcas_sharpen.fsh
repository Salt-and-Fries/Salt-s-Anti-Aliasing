#version 150
/*
 * Salt's Anti Aliasing post-processing shader.
 *
 * RCAS-style sharpening pass used after upscaling to recover contrast without excessive ringing.
 * Runtime JSON effects bind the samplers and uniform blocks; these comments describe the pass data flow.
 */


// Scene, history, depth, or helper textures supplied by Minecraft's post-effect chain.
uniform sampler2D DiffuseSampler;

// Packed runtime parameters updated from Java when resolution, mode, or config changes.
uniform vec2 OutSize;
uniform vec2 InSize;

uniform float Sharpness;
uniform float EdgeLimit;
uniform float ClampBoost;

// Full-screen pass coordinates and final color output for the current pixel.
in vec2 texCoord;

out vec4 fragColor;

float maxComponent(vec3 value) {
    return max(value.r, max(value.g, value.b));
}

// Executes the per-pixel resolve, upscale, sharpen, or debug operation for this pass.
void main() {
    // Work in texel-relative offsets so the same math scales across window sizes.
    vec2 texel = 1.0 / InSize;

    vec3 center = texture(DiffuseSampler, texCoord).rgb;
    vec3 north = texture(DiffuseSampler, texCoord + vec2(0.0, -texel.y)).rgb;
    vec3 south = texture(DiffuseSampler, texCoord + vec2(0.0, texel.y)).rgb;
    vec3 west = texture(DiffuseSampler, texCoord + vec2(-texel.x, 0.0)).rgb;
    vec3 east = texture(DiffuseSampler, texCoord + vec2(texel.x, 0.0)).rgb;

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
