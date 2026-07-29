#version 330
/*
 * Salt's Anti Aliasing post-processing shader.
 *
 * SMAA edge-detection pass that writes edge confidence for later blend-weight calculation.
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

layout(std140) uniform SmaaEdgeConfig {
    float EdgeThreshold;
    float LocalContrastFactor;
    float DiagonalFactor;
};

// Full-screen pass coordinates and final color output for the current pixel.
in vec2 texCoord;

out vec4 fragColor;

float luma(vec3 color) {
    return dot(color, vec3(0.299, 0.587, 0.114));
}

// Executes the per-pixel resolve/upscale/debug operation for this pass.
void main() {
    vec2 texel = 1.0 / InSize;

    float center = luma(texture(InSampler, texCoord).rgb);
    float left = luma(texture(InSampler, texCoord + vec2(-texel.x, 0.0)).rgb);
    float right = luma(texture(InSampler, texCoord + vec2(texel.x, 0.0)).rgb);
    float up = luma(texture(InSampler, texCoord + vec2(0.0, -texel.y)).rgb);
    float down = luma(texture(InSampler, texCoord + vec2(0.0, texel.y)).rgb);
    float leftLeft = luma(texture(InSampler, texCoord + vec2(-2.0 * texel.x, 0.0)).rgb);
    float upUp = luma(texture(InSampler, texCoord + vec2(0.0, -2.0 * texel.y)).rgb);

    // Only record the left and top boundaries. Symmetric edge detection marks both sides of an
    // edge and produces a two-pixel-wide low-pass region.
    vec2 delta = abs(vec2(center - left, center - up));
    vec2 edges = smoothstep(vec2(EdgeThreshold), vec2(EdgeThreshold * 2.0), delta);
    if (edges.x + edges.y <= 0.0) {
        fragColor = vec4(0.0, 0.0, 0.0, 1.0);
        return;
    }

    float diagonalDelta = max(
            abs(center - luma(texture(InSampler, texCoord + vec2(-texel.x, -texel.y)).rgb)),
            abs(center - luma(texture(InSampler, texCoord + vec2(texel.x, texel.y)).rgb))
    );
    float neighborhoodMax = max(
            max(max(delta.x, delta.y), max(abs(center - right), abs(center - down))),
            max(max(abs(left - leftLeft), abs(up - upUp)), diagonalDelta * DiagonalFactor)
    );

    // Suppress weak texture contrast next to a stronger boundary. This keeps SMAA focused on
    // geometric silhouettes instead of softening every high-frequency surface detail.
    float localThreshold = neighborhoodMax / max(LocalContrastFactor, 1.0);
    edges *= step(vec2(localThreshold), delta);
    fragColor = vec4(edges, 0.0, 1.0);
}
