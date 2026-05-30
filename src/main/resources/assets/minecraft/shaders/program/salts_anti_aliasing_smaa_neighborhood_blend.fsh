#version 150
/*
 * Salt's Anti Aliasing post-processing shader.
 *
 * SMAA neighborhood pass that applies edge weights to the final color resolve.
 * Runtime JSON effects bind the samplers and uniform blocks; these comments describe the pass data flow.
 */


// Scene, history, depth, or helper textures supplied by Minecraft's post-effect chain.
uniform sampler2D DiffuseSampler;
uniform sampler2D WeightsSampler;

// Packed runtime parameters updated from Java when resolution, mode, or config changes.
uniform vec2 OutSize;
uniform vec2 InSize;

// Full-screen pass coordinates and final color output for the current pixel.
in vec2 texCoord;

out vec4 fragColor;

// Executes the per-pixel resolve, upscale, sharpen, or debug operation for this pass.
void main() {
    // Work in texel-relative offsets so the same math scales across window sizes.
    vec2 texel = 1.0 / InSize;

    vec3 center = texture(DiffuseSampler, texCoord).rgb;
    vec3 west = texture(DiffuseSampler, texCoord + vec2(-texel.x, 0.0)).rgb;
    vec3 east = texture(DiffuseSampler, texCoord + vec2(texel.x, 0.0)).rgb;
    vec3 north = texture(DiffuseSampler, texCoord + vec2(0.0, -texel.y)).rgb;
    vec3 south = texture(DiffuseSampler, texCoord + vec2(0.0, texel.y)).rgb;

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
