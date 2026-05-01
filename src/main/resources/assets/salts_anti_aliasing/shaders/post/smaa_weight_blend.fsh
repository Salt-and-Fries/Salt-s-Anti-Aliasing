#version 330
/*
 * Salt's Anti Aliasing post-processing shader.
 *
 * SMAA blend-weight pass that searches around detected edges and encodes how strongly neighboring pixels should contribute.
 * The JSON post-effect definitions bind these samplers and uniform blocks at runtime,
 * so the shader comments focus on the math and data flow inside the pass.
 */


// Scene, history, depth, or helper textures supplied by Minecraft's post-effect chain.
uniform sampler2D ColorSampler;
uniform sampler2D EdgesSampler;

// Packed runtime parameters; Java updates these values each frame or whenever config changes.
layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(std140) uniform SmaaWeightConfig {
    float SearchStrength;
    float CornerRounding;
    float MaxBlend;
};

// Full-screen pass coordinates and final color output for the current pixel.
in vec2 texCoord;

out vec4 fragColor;

float luma(vec3 color) {
    return dot(color, vec3(0.299, 0.587, 0.114));
}

// Executes the per-pixel resolve/upscale/debug operation for this pass.
void main() {
    // Work in texel-relative offsets so the same shader scales across window sizes.
    vec2 texel = 1.0 / InSize;

    vec2 edge = texture(EdgesSampler, texCoord).rg;
    vec2 leftEdge = texture(EdgesSampler, texCoord + vec2(-texel.x, 0.0)).rg;
    vec2 rightEdge = texture(EdgesSampler, texCoord + vec2(texel.x, 0.0)).rg;
    vec2 upEdge = texture(EdgesSampler, texCoord + vec2(0.0, -texel.y)).rg;
    vec2 downEdge = texture(EdgesSampler, texCoord + vec2(0.0, texel.y)).rg;

    vec3 west = texture(ColorSampler, texCoord + vec2(-texel.x, 0.0)).rgb;
    vec3 east = texture(ColorSampler, texCoord + vec2(texel.x, 0.0)).rgb;
    vec3 north = texture(ColorSampler, texCoord + vec2(0.0, -texel.y)).rgb;
    vec3 south = texture(ColorSampler, texCoord + vec2(0.0, texel.y)).rgb;

    float contrastX = abs(luma(east) - luma(west));
    float contrastY = abs(luma(south) - luma(north));

    float spanX = edge.x + leftEdge.x * 0.5 + rightEdge.x * 0.5;
    float spanY = edge.y + upEdge.y * 0.5 + downEdge.y * 0.5;

    float horizontalBlend = clamp(edge.x * (0.45 + spanX * 0.35 + contrastX * SearchStrength), 0.0, MaxBlend);
    float verticalBlend = clamp(edge.y * (0.45 + spanY * 0.35 + contrastY * SearchStrength), 0.0, MaxBlend);

    float leftWeight = horizontalBlend * (0.5 + 0.5 * leftEdge.x);
    float rightWeight = horizontalBlend * (0.5 + 0.5 * rightEdge.x);
    float upWeight = verticalBlend * (0.5 + 0.5 * upEdge.y);
    float downWeight = verticalBlend * (0.5 + 0.5 * downEdge.y);

    float horizontalSum = max(leftWeight + rightWeight, 0.0001);
    float verticalSum = max(upWeight + downWeight, 0.0001);

    leftWeight = horizontalBlend * (leftWeight / horizontalSum);
    rightWeight = horizontalBlend * (rightWeight / horizontalSum);
    upWeight = verticalBlend * (upWeight / verticalSum);
    downWeight = verticalBlend * (downWeight / verticalSum);

    float cornerSuppression = 1.0 - min(edge.x, edge.y) * CornerRounding;
    fragColor = vec4(leftWeight, rightWeight, upWeight, downWeight) * cornerSuppression;
}
