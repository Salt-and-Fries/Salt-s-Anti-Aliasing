#version 330
/*
 * Salt's Anti Aliasing post-processing shader.
 *
 * Temporal resolve pass that blends the current scene with history using neighborhood clamps so jittered samples reduce shimmer without leaving obvious trails.
 * The JSON post-effect definitions bind these samplers and uniform blocks at runtime,
 * so the shader comments focus on the math and data flow inside the pass.
 */


// Scene, history, depth, or helper textures supplied by Minecraft's post-effect chain.
uniform sampler2D CurrentSampler;
uniform sampler2D CurrentDepthSampler;
uniform sampler2D HistorySampler;

// Packed runtime parameters; Java updates these values each frame or whenever config changes.
layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(std140) uniform TaaConfig {
    float BaseHistoryWeight;
    float LumaRejection;
    float DepthRejection;
    float NeighborhoodClamp;
    float CurrentJitterX;
    float CurrentJitterY;
    float PreviousJitterX;
    float PreviousJitterY;
    float CameraMotion;
};

// Full-screen pass coordinates and final color output for the current pixel.
in vec2 texCoord;

out vec4 fragColor;

float luma(vec3 color) {
    return dot(color, vec3(0.299, 0.587, 0.114));
}

vec3 sampleCurrent(vec2 offset) {
    return texture(CurrentSampler, texCoord + offset).rgb;
}

float sampleDepth(sampler2D depthSampler, vec2 offset) {
    return texture(depthSampler, texCoord + offset).r;
}

// Executes the per-pixel resolve/upscale/debug operation for this pass.
void main() {
    // Work in texel-relative offsets so the same shader scales across window sizes.
    vec2 texel = 1.0 / InSize;
    vec2 historyUv = clamp(
        texCoord + vec2(PreviousJitterX - CurrentJitterX, PreviousJitterY - CurrentJitterY) * texel,
        vec2(0.0),
        vec2(1.0)
    );

    vec3 current = texture(CurrentSampler, texCoord).rgb;
    vec3 history = texture(HistorySampler, historyUv).rgb;
    float currentDepth = texture(CurrentDepthSampler, texCoord).r;

    vec3 minNeighborhood = current;
    vec3 maxNeighborhood = current;
    vec3 neighborhoodAverage = current;
    vec3 wideAverage = vec3(0.0);
    float depthEdge = 0.0;
    float lumaMin = luma(current);
    float lumaMax = lumaMin;

    for (int y = -1; y <= 1; y++) {
        for (int x = -1; x <= 1; x++) {
            if (x == 0 && y == 0) {
                continue;
            }

            vec2 offset = vec2(float(x), float(y)) * texel;
            vec3 sampleColor = sampleCurrent(offset);
            minNeighborhood = min(minNeighborhood, sampleColor);
            maxNeighborhood = max(maxNeighborhood, sampleColor);
            neighborhoodAverage += sampleColor;
            depthEdge = max(depthEdge, abs(currentDepth - sampleDepth(CurrentDepthSampler, offset)));
            float sampleLuma = luma(sampleColor);
            lumaMin = min(lumaMin, sampleLuma);
            lumaMax = max(lumaMax, sampleLuma);
        }
    }

    neighborhoodAverage /= 9.0;
    wideAverage =
        sampleCurrent(vec2(-2.0 * texel.x, 0.0)) +
        sampleCurrent(vec2( 2.0 * texel.x, 0.0)) +
        sampleCurrent(vec2(0.0, -2.0 * texel.y)) +
        sampleCurrent(vec2(0.0,  2.0 * texel.y)) +
        sampleCurrent(vec2(-2.0 * texel.x, -2.0 * texel.y)) +
        sampleCurrent(vec2( 2.0 * texel.x, -2.0 * texel.y)) +
        sampleCurrent(vec2(-2.0 * texel.x,  2.0 * texel.y)) +
        sampleCurrent(vec2( 2.0 * texel.x,  2.0 * texel.y));
    wideAverage /= 8.0;

    vec3 clampPad = (maxNeighborhood - minNeighborhood) * NeighborhoodClamp;
    vec3 clampedHistory = clamp(history, minNeighborhood - clampPad, maxNeighborhood + clampPad);
    float lumaRange = lumaMax - lumaMin;
    float edgeBlend = smoothstep(0.02, 0.18, lumaRange);
    vec3 exaggeratedSpatial = mix(neighborhoodAverage, wideAverage, 0.18);
    vec3 spatialResolved = mix(current, exaggeratedSpatial, 0.08 + edgeBlend * 0.20);
    float motionFactor = clamp(CameraMotion, 0.0, 1.0);
    spatialResolved = mix(spatialResolved, current, motionFactor * 0.45);

    float lumaDelta = abs(luma(spatialResolved) - luma(clampedHistory));
    float rejection = clamp(
            lumaDelta * LumaRejection +
            depthEdge * DepthRejection,
            0.0,
            1.0
    );

    float rejectionScale = mix(0.65, 0.92, motionFactor);
    float historyWeight = clamp(
        BaseHistoryWeight *
        (1.0 - rejection * rejectionScale) *
        (1.0 - motionFactor * 0.82),
        0.0,
        0.96
    );
    vec3 resolved = mix(spatialResolved, clampedHistory, historyWeight);
    fragColor = vec4(resolved, 1.0);
}
