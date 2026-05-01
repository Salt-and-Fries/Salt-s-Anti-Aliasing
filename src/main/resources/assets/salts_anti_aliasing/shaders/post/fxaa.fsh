#version 330
/*
 * Salt's Anti Aliasing post-processing shader.
 *
 * Enhanced FXAA resolve pass that combines luma, color contrast, and depth contrast to smooth high-confidence edges.
 * Runtime JSON effects bind the samplers and uniform blocks; these comments describe the pass data flow.
 */


// Scene, history, depth, or helper textures supplied by Minecraft's post-effect chain.
uniform sampler2D InSampler;
uniform sampler2D DepthSampler;

// Packed runtime parameters updated from Java when resolution, mode, or config changes.
layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(std140) uniform FxaaConfig {
    float SubpixelBlend;
    float EdgeThreshold;
    float EdgeThresholdMin;
    float SearchRadius;
    float ColorWeight;
    float DepthWeight;
    float VarianceGuard;
    float EdgeConfidenceScale;
};

// Full-screen pass coordinates and final color output for the current pixel.
in vec2 texCoord;

out vec4 fragColor;

float luma(vec3 color) {
    return dot(color, vec3(0.299, 0.587, 0.114));
}

vec3 sampleColor(vec2 offset) {
    return texture(InSampler, texCoord + offset).rgb;
}

float sampleDepth(vec2 offset) {
    return texture(DepthSampler, texCoord + offset).r;
}

float colorDistance(vec3 a, vec3 b) {
    vec3 delta = a - b;
    return sqrt(dot(delta, delta));
}

// Executes the per-pixel resolve, upscale, sharpen, or debug operation for this pass.
void main() {
    // Work in texel-relative offsets so the same math scales across window sizes.
    vec2 texel = 1.0 / InSize;

    vec3 rgbM = texture(InSampler, texCoord).rgb;
    vec3 rgbN = sampleColor(vec2(0.0, -texel.y));
    vec3 rgbS = sampleColor(vec2(0.0, texel.y));
    vec3 rgbW = sampleColor(vec2(-texel.x, 0.0));
    vec3 rgbE = sampleColor(vec2(texel.x, 0.0));
    vec3 rgbNW = texture(InSampler, texCoord + vec2(-texel.x, -texel.y)).rgb;
    vec3 rgbNE = texture(InSampler, texCoord + vec2(texel.x, -texel.y)).rgb;
    vec3 rgbSW = texture(InSampler, texCoord + vec2(-texel.x, texel.y)).rgb;
    vec3 rgbSE = texture(InSampler, texCoord + vec2(texel.x, texel.y)).rgb;

    float lumaM = luma(rgbM);
    float lumaN = luma(rgbN);
    float lumaS = luma(rgbS);
    float lumaW = luma(rgbW);
    float lumaE = luma(rgbE);
    float lumaNW = luma(rgbNW);
    float lumaNE = luma(rgbNE);
    float lumaSW = luma(rgbSW);
    float lumaSE = luma(rgbSE);

    float depthM = sampleDepth(vec2(0.0));
    float depthN = sampleDepth(vec2(0.0, -texel.y));
    float depthS = sampleDepth(vec2(0.0, texel.y));
    float depthW = sampleDepth(vec2(-texel.x, 0.0));
    float depthE = sampleDepth(vec2(texel.x, 0.0));
    float depthNW = sampleDepth(vec2(-texel.x, -texel.y));
    float depthNE = sampleDepth(vec2(texel.x, -texel.y));
    float depthSW = sampleDepth(vec2(-texel.x, texel.y));
    float depthSE = sampleDepth(vec2(texel.x, texel.y));

    float lumaMin = min(lumaM, min(min(min(lumaN, lumaS), min(lumaW, lumaE)), min(min(lumaNW, lumaNE), min(lumaSW, lumaSE))));
    float lumaMax = max(lumaM, max(max(max(lumaN, lumaS), max(lumaW, lumaE)), max(max(lumaNW, lumaNE), max(lumaSW, lumaSE))));
    float lumaRange = lumaMax - lumaMin;
    vec3 localAverage = (rgbM + rgbN + rgbS + rgbW + rgbE + rgbNW + rgbNE + rgbSW + rgbSE) / 9.0;
    vec3 localMin = min(min(min(rgbM, rgbN), min(rgbS, rgbW)), min(min(rgbE, rgbNW), min(min(rgbNE, rgbSW), rgbSE)));
    vec3 localMax = max(max(max(rgbM, rgbN), max(rgbS, rgbW)), max(max(rgbE, rgbNW), max(max(rgbNE, rgbSW), rgbSE)));

    float variance =
        pow(lumaN - lumaM, 2.0) +
        pow(lumaS - lumaM, 2.0) +
        pow(lumaW - lumaM, 2.0) +
        pow(lumaE - lumaM, 2.0);
    variance *= 0.25;

    float colorRange = max(
        max(colorDistance(rgbM, rgbN), colorDistance(rgbM, rgbS)),
        max(colorDistance(rgbM, rgbW), colorDistance(rgbM, rgbE))
    );

    float depthRange = max(
        max(max(abs(depthM - depthN), abs(depthM - depthS)), max(abs(depthM - depthW), abs(depthM - depthE))),
        max(max(abs(depthM - depthNW), abs(depthM - depthNE)), max(abs(depthM - depthSW), abs(depthM - depthSE)))
    );

    float edgeSignal = max(lumaRange, colorRange * ColorWeight + depthRange * DepthWeight);
    edgeSignal = clamp(edgeSignal * EdgeConfidenceScale, 0.0, 1.0);

    float threshold = max(EdgeThresholdMin, mix(EdgeThreshold * 0.45, EdgeThreshold * 1.05, clamp(variance * 8.0, 0.0, 1.0)));
    if (edgeSignal < threshold) {
        fragColor = vec4(rgbM, 1.0);
        return;
    }

    vec2 direction = vec2(
        -((lumaNW + 2.0 * lumaN + lumaNE) - (lumaSW + 2.0 * lumaS + lumaSE)),
        ((lumaNW + 2.0 * lumaW + lumaSW) - (lumaNE + 2.0 * lumaE + lumaSE))
    );

    float directionLength = length(direction);
    if (directionLength < 1e-4) {
        fragColor = vec4(rgbM, 1.0);
        return;
    }

    vec2 edgeDirection = direction / directionLength;
    float searchScale = mix(0.8, SearchRadius, edgeSignal) / 3.6;
    vec2 searchStep = edgeDirection * texel * searchScale;

    vec3 directionalBlend =
        rgbM * 0.12 +
        sampleColor(searchStep * -0.75) * 0.18 +
        sampleColor(searchStep * 0.75) * 0.18 +
        sampleColor(searchStep * -1.75) * 0.15 +
        sampleColor(searchStep * 1.75) * 0.15 +
        sampleColor(searchStep * -3.0) * 0.10 +
        sampleColor(searchStep * 3.0) * 0.10 +
        sampleColor(searchStep * -4.5) * 0.01 +
        sampleColor(searchStep * 4.5) * 0.01;

    float subpixelAlias = clamp(abs(lumaM - luma(localAverage)) / max(lumaRange, 0.012), 0.0, 1.0);
    subpixelAlias = pow(subpixelAlias, 0.7) * SubpixelBlend;
    vec3 subpixelBlendColor = mix(rgbM, localAverage, clamp(subpixelAlias * 0.82, 0.0, 0.88));

    float varianceGuard = 1.0 - clamp(variance / (0.028 + VarianceGuard * 0.08), 0.0, 0.55);
    float blendAmount = clamp(0.22 + edgeSignal * varianceGuard * 1.45, 0.0, 0.96);
    vec3 resolved = mix(subpixelBlendColor, directionalBlend, clamp(0.52 + edgeSignal * 0.72 + subpixelAlias * 0.28, 0.0, 1.0));
    resolved = clamp(resolved, localMin - vec3(0.03), localMax + vec3(0.03));
    resolved = mix(rgbM, resolved, blendAmount);
    fragColor = vec4(resolved, 1.0);
}
