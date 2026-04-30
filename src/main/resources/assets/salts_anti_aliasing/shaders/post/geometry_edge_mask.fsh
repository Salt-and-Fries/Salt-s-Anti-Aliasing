#version 330

uniform sampler2D ColorSampler;
uniform sampler2D DepthSampler;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(std140) uniform EdgeMaskConfig {
    float IncludeBlockBoundaries;
    float DepthLow;
    float DepthHigh;
    float BoundaryStrength;
};

in vec2 texCoord;

out vec4 fragColor;

float luma(vec3 color) {
    return dot(color, vec3(0.299, 0.587, 0.114));
}

vec3 sampleColor(vec2 offset) {
    return texture(ColorSampler, texCoord + offset).rgb;
}

float sampleDepth(vec2 offset) {
    return texture(DepthSampler, texCoord + offset).r;
}

vec3 crossAverage(vec2 centerOffset, vec2 texel) {
    return (
        sampleColor(centerOffset) +
        sampleColor(centerOffset + vec2(texel.x, 0.0)) +
        sampleColor(centerOffset + vec2(-texel.x, 0.0)) +
        sampleColor(centerOffset + vec2(0.0, texel.y)) +
        sampleColor(centerOffset + vec2(0.0, -texel.y))
    ) * 0.2;
}

void main() {
    vec2 texel = 1.0 / InSize;

    float depthM = sampleDepth(vec2(0.0));
    float depthN = sampleDepth(vec2(0.0, -texel.y));
    float depthS = sampleDepth(vec2(0.0, texel.y));
    float depthW = sampleDepth(vec2(-texel.x, 0.0));
    float depthE = sampleDepth(vec2(texel.x, 0.0));
    float depthNW = sampleDepth(vec2(-texel.x, -texel.y));
    float depthNE = sampleDepth(vec2(texel.x, -texel.y));
    float depthSW = sampleDepth(vec2(-texel.x, texel.y));
    float depthSE = sampleDepth(vec2(texel.x, texel.y));

    float depthCurvature = max(
        max(abs(depthN + depthS - 2.0 * depthM), abs(depthW + depthE - 2.0 * depthM)),
        max(abs(depthNW + depthSE - 2.0 * depthM), abs(depthNE + depthSW - 2.0 * depthM))
    );
    float depthRange = max(
        max(max(abs(depthM - depthN), abs(depthM - depthS)), max(abs(depthM - depthW), abs(depthM - depthE))),
        max(max(abs(depthM - depthNW), abs(depthM - depthNE)), max(abs(depthM - depthSW), abs(depthM - depthSE)))
    );
    float oppositeDepthJump = max(
        max(abs(depthN - depthS), abs(depthW - depthE)),
        max(abs(depthNW - depthSE), abs(depthNE - depthSW))
    );
    float minDepth = min(depthM, min(min(min(depthN, depthS), min(depthW, depthE)), min(min(depthNW, depthNE), min(depthSW, depthSE))));
    float maxDepth = max(depthM, max(max(max(depthN, depthS), max(depthW, depthE)), max(max(depthNW, depthNE), max(depthSW, depthSE))));
    float skySilhouette = step(0.999, maxDepth) * step(minDepth, 0.9985);
    float depthEdge = max(
        smoothstep(DepthLow, DepthHigh, max(depthCurvature, max(oppositeDepthJump * 0.35, depthRange * 0.75))),
        skySilhouette
    );

    vec3 center = sampleColor(vec2(0.0));
    float centerLuma = luma(center);
    float localVariance = (
        abs(centerLuma - luma(sampleColor(vec2(texel.x, 0.0)))) +
        abs(centerLuma - luma(sampleColor(vec2(-texel.x, 0.0)))) +
        abs(centerLuma - luma(sampleColor(vec2(0.0, texel.y)))) +
        abs(centerLuma - luma(sampleColor(vec2(0.0, -texel.y))))
    ) * 0.25;

    vec3 westAverage = crossAverage(vec2(-2.0 * texel.x, 0.0), texel);
    vec3 eastAverage = crossAverage(vec2(2.0 * texel.x, 0.0), texel);
    vec3 northAverage = crossAverage(vec2(0.0, -2.0 * texel.y), texel);
    vec3 southAverage = crossAverage(vec2(0.0, 2.0 * texel.y), texel);
    float smoothedBoundary = max(
        length(eastAverage - westAverage),
        length(southAverage - northAverage)
    );

    float textureNoiseReject = 1.0 - smoothstep(0.045, 0.18, localVariance);
    float blockBoundary = smoothstep(0.16, 0.38, smoothedBoundary) * textureNoiseReject;
    blockBoundary *= step(0.5, IncludeBlockBoundaries) * BoundaryStrength;

    float mask = clamp(max(depthEdge, blockBoundary), 0.0, 1.0);
    fragColor = vec4(mask, mask, mask, 1.0);
}
