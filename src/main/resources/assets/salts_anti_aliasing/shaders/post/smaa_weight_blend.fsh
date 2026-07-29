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

float edgeX(vec2 uv) {
    return texture(EdgesSampler, uv).r;
}

float edgeY(vec2 uv) {
    return texture(EdgesSampler, uv).g;
}

// A pixel-aligned boundary is already perfectly sampled and must not be softened.  A
// morphological AA weight is only useful where the boundary ends, turns, or moves to the
// neighbouring row/column (the staircase pattern of a diagonal edge).
float verticalPattern(vec2 uv, vec2 texel) {
    float boundary = edgeX(uv);
    float above = edgeX(uv - vec2(0.0, texel.y));
    float below = edgeX(uv + vec2(0.0, texel.y));

    float turnAbove = max(
            max(edgeY(uv), edgeY(uv - vec2(texel.x, 0.0))),
            max(edgeX(uv - texel), edgeX(uv + vec2(texel.x, -texel.y)))
    );
    float turnBelow = max(
            max(edgeY(uv + vec2(0.0, texel.y)), edgeY(uv + vec2(-texel.x, texel.y))),
            max(edgeX(uv + vec2(-texel.x, texel.y)), edgeX(uv + texel))
    );

    float endpoint = max((1.0 - above) * turnAbove, (1.0 - below) * turnBelow);
    return boundary * clamp(endpoint, 0.0, 1.0);
}

float horizontalPattern(vec2 uv, vec2 texel) {
    float boundary = edgeY(uv);
    float left = edgeY(uv - vec2(texel.x, 0.0));
    float right = edgeY(uv + vec2(texel.x, 0.0));

    float turnLeft = max(
            max(edgeX(uv), edgeX(uv - vec2(0.0, texel.y))),
            max(edgeY(uv - texel), edgeY(uv + vec2(-texel.x, texel.y)))
    );
    float turnRight = max(
            max(edgeX(uv + vec2(texel.x, 0.0)), edgeX(uv + vec2(texel.x, -texel.y))),
            max(edgeY(uv + vec2(texel.x, -texel.y)), edgeY(uv + texel))
    );

    float endpoint = max((1.0 - left) * turnLeft, (1.0 - right) * turnRight);
    return boundary * clamp(endpoint, 0.0, 1.0);
}

// Executes the per-pixel resolve/upscale/debug operation for this pass.
void main() {
    vec2 texel = 1.0 / InSize;

    vec3 center = texture(ColorSampler, texCoord).rgb;
    vec3 west = texture(ColorSampler, texCoord + vec2(-texel.x, 0.0)).rgb;
    vec3 east = texture(ColorSampler, texCoord + vec2(texel.x, 0.0)).rgb;
    vec3 north = texture(ColorSampler, texCoord + vec2(0.0, -texel.y)).rgb;
    vec3 south = texture(ColorSampler, texCoord + vec2(0.0, texel.y)).rgb;

    float centerLuma = luma(center);
    vec4 contrast = abs(centerLuma - vec4(luma(west), luma(east), luma(north), luma(south)));

    // An edge stored at this pixel lies on its left/top boundary. The right/bottom boundary is
    // stored by the adjacent pixel, so classify each boundary at the texel that owns it. Straight
    // aligned edges produce zero weights; only turns and staircase endpoints are reconstructed.
    vec4 pattern = vec4(
            verticalPattern(texCoord, texel),
            verticalPattern(texCoord + vec2(texel.x, 0.0), texel),
            horizontalPattern(texCoord, texel),
            horizontalPattern(texCoord + vec2(0.0, texel.y), texel)
    );
    vec4 weights = pattern * contrast * SearchStrength;
    weights = clamp(weights, 0.0, MaxBlend);

    float horizontalStrength = max(weights.r, weights.g);
    float verticalStrength = max(weights.b, weights.a);
    if (horizontalStrength > verticalStrength) {
        weights.ba *= 1.0 - horizontalStrength * CornerRounding;
    } else {
        weights.rg *= 1.0 - verticalStrength * CornerRounding;
    }

    fragColor = weights;
}
