#version 330
/*
 * Salt's Anti Aliasing post-processing shader.
 *
 * SMAA edge-detection pass that records edge confidence for later weight calculation.
 * Runtime JSON effects bind the samplers and uniform blocks; these comments describe the pass data flow.
 */


// Scene, history, depth, or helper textures supplied by Minecraft's post-effect chain.
uniform sampler2D InSampler;

// Packed runtime parameters updated from Java when resolution, mode, or config changes.
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

// Executes the per-pixel resolve, upscale, sharpen, or debug operation for this pass.
void main() {
    // Work in texel-relative offsets so the same math scales across window sizes.
    vec2 texel = 1.0 / InSize;

    float center = luma(texture(InSampler, texCoord).rgb);
    float left = luma(texture(InSampler, texCoord + vec2(-texel.x, 0.0)).rgb);
    float right = luma(texture(InSampler, texCoord + vec2(texel.x, 0.0)).rgb);
    float up = luma(texture(InSampler, texCoord + vec2(0.0, -texel.y)).rgb);
    float down = luma(texture(InSampler, texCoord + vec2(0.0, texel.y)).rgb);
    float upLeft = luma(texture(InSampler, texCoord + vec2(-texel.x, -texel.y)).rgb);
    float downRight = luma(texture(InSampler, texCoord + vec2(texel.x, texel.y)).rgb);
    float upRight = luma(texture(InSampler, texCoord + vec2(texel.x, -texel.y)).rgb);
    float downLeft = luma(texture(InSampler, texCoord + vec2(-texel.x, texel.y)).rgb);

    float horizontalDelta = max(abs(center - left), abs(center - right));
    float verticalDelta = max(abs(center - up), abs(center - down));
    float diagonalDelta = max(
            max(abs(center - upLeft), abs(center - downRight)),
            max(abs(center - upRight), abs(center - downLeft))
    );

    float localContrast = max(horizontalDelta, max(verticalDelta, diagonalDelta));
    float adaptiveThreshold = mix(EdgeThreshold, EdgeThreshold * LocalContrastFactor, smoothstep(0.04, 0.16, localContrast));

    float horizontalEdge = smoothstep(adaptiveThreshold, adaptiveThreshold * 2.25, horizontalDelta + diagonalDelta * DiagonalFactor);
    float verticalEdge = smoothstep(adaptiveThreshold, adaptiveThreshold * 2.25, verticalDelta + diagonalDelta * DiagonalFactor);

    fragColor = vec4(horizontalEdge, verticalEdge, 0.0, 1.0);
}
