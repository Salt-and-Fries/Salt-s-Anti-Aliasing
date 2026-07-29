#version 330
/*
 * Salt's Anti Aliasing post-processing shader.
 *
 * SMAA neighborhood pass that applies the edge weights to produce the final spatially smoothed color.
 * The JSON post-effect definitions bind these samplers and uniform blocks at runtime,
 * so the shader comments focus on the math and data flow inside the pass.
 */


// Scene, history, depth, or helper textures supplied by Minecraft's post-effect chain.
uniform sampler2D ColorSampler;
uniform sampler2D WeightsSampler;

// Packed runtime parameters; Java updates these values each frame or whenever config changes.
layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

// Full-screen pass coordinates and final color output for the current pixel.
in vec2 texCoord;

out vec4 fragColor;

// Executes the per-pixel resolve/upscale/debug operation for this pass.
void main() {
    vec2 texel = 1.0 / InSize;

    vec3 center = texture(ColorSampler, texCoord).rgb;
    vec3 west = texture(ColorSampler, texCoord + vec2(-texel.x, 0.0)).rgb;
    vec3 east = texture(ColorSampler, texCoord + vec2(texel.x, 0.0)).rgb;
    vec3 north = texture(ColorSampler, texCoord + vec2(0.0, -texel.y)).rgb;
    vec3 south = texture(ColorSampler, texCoord + vec2(0.0, texel.y)).rgb;

    vec4 weights = texture(WeightsSampler, texCoord);
    vec3 resolved = center;
    float horizontalWeight = weights.r + weights.g;
    float verticalWeight = weights.b + weights.a;
    if (max(horizontalWeight, verticalWeight) > 0.0001) {
        // Blend along only the dominant boundary axis. Averaging all four neighbors turns the
        // neighborhood pass into a general blur, especially around corners and textured blocks.
        if (horizontalWeight >= verticalWeight) {
            resolved = (center + west * weights.r + east * weights.g) / (1.0 + horizontalWeight);
        } else {
            resolved = (center + north * weights.b + south * weights.a) / (1.0 + verticalWeight);
        }

        vec3 minNeighborhood = min(center, min(min(west, east), min(north, south)));
        vec3 maxNeighborhood = max(center, max(max(west, east), max(north, south)));
        resolved = clamp(resolved, minNeighborhood, maxNeighborhood);
    }

    fragColor = vec4(resolved, 1.0);
}
