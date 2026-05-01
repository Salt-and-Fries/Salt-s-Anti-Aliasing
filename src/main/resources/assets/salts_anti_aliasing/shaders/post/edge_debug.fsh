#version 330
/*
 * Salt's Anti Aliasing post-processing shader.
 *
 * Diagnostic pass that makes edge and aliasing signals visible while tuning the renderer.
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
    vec3 rgbNW = sampleColor(vec2(-texel.x, -texel.y));
    vec3 rgbNE = sampleColor(vec2(texel.x, -texel.y));
    vec3 rgbSW = sampleColor(vec2(-texel.x, texel.y));
    vec3 rgbSE = sampleColor(vec2(texel.x, texel.y));

    float lumaM = luma(rgbM);
    float lumaN = luma(rgbN);
    float lumaS = luma(rgbS);
    float lumaW = luma(rgbW);
    float lumaE = luma(rgbE);
    float lumaNW = luma(rgbNW);
    float lumaNE = luma(rgbNE);
    float lumaSW = luma(rgbSW);
    float lumaSE = luma(rgbSE);

    float lumaMin = min(lumaM, min(min(min(lumaN, lumaS), min(lumaW, lumaE)), min(min(lumaNW, lumaNE), min(lumaSW, lumaSE))));
    float lumaMax = max(lumaM, max(max(max(lumaN, lumaS), max(lumaW, lumaE)), max(max(lumaNW, lumaNE), max(lumaSW, lumaSE))));
    float lumaRange = lumaMax - lumaMin;

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

    float depthM = sampleDepth(vec2(0.0));
    float depthRange = max(
        max(max(abs(depthM - sampleDepth(vec2(0.0, -texel.y))), abs(depthM - sampleDepth(vec2(0.0, texel.y)))),
            max(abs(depthM - sampleDepth(vec2(-texel.x, 0.0))), abs(depthM - sampleDepth(vec2(texel.x, 0.0))))),
        max(max(abs(depthM - sampleDepth(vec2(-texel.x, -texel.y))), abs(depthM - sampleDepth(vec2(texel.x, -texel.y)))),
            max(abs(depthM - sampleDepth(vec2(-texel.x, texel.y))), abs(depthM - sampleDepth(vec2(texel.x, texel.y)))))
    );

    float edgeSignal = max(lumaRange, colorRange * ColorWeight + depthRange * DepthWeight);
    edgeSignal = clamp(edgeSignal * EdgeConfidenceScale, 0.0, 1.0);
    float threshold = max(EdgeThresholdMin, mix(EdgeThreshold * 0.65, EdgeThreshold * 1.35, clamp(variance * 18.0, 0.0, 1.0)));
    float edgeMask = smoothstep(threshold, threshold * 2.3 + 0.012, edgeSignal);
    edgeMask = pow(edgeMask, 0.72);

    fragColor = vec4(vec3(edgeMask), 1.0);
}
