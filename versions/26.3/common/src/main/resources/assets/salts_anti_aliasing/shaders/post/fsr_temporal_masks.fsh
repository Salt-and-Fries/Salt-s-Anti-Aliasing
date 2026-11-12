#version 450
#include <salts_anti_aliasing:temporal_color_rejection.glsl>

uniform sampler2D CurrentColorSampler;
uniform sampler2D CurrentOpaqueSampler;
uniform sampler2D PreviousColorSampler;
uniform sampler2D PreviousOpaqueSampler;
uniform sampler2D MotionVectorSampler;

layout(std140) uniform FsrTemporalMaskConfig {
    // xy: render size, zw: previous-minus-current projection jitter in pixel space.
    vec4 MaskConfig; vec4 Reactivity; vec4 Composition;
};

layout(location = 0) in vec2 texCoord;

layout(location = 0) out vec4 reactiveMaskOutput;
layout(location = 1) out vec4 transparencyMaskOutput;

#define TC_THRESHOLD Composition.x
const float TC_GATE_THRESHOLD = 0.01;
#define TC_SCALE Composition.y
const float AUTOGEN_EPSILON = 0.01;
#define REACTIVE_SCALE Composition.z
const float REACTIVE_MAX = 0.9;

ivec2 renderSize() {
    return ivec2(MaskConfig.xy);
}

ivec2 clampPixel(ivec2 pixel) {
    return clamp(pixel, ivec2(0), renderSize() - ivec2(1));
}

vec3 currentColor(ivec2 pixel) {
    return texelFetch(CurrentColorSampler, clampPixel(pixel), 0).rgb;
}

vec3 currentOpaque(ivec2 pixel) {
    return texelFetch(CurrentOpaqueSampler, clampPixel(pixel), 0).rgb;
}

vec3 previousColor(vec2 uv) {
    return texture(PreviousColorSampler, uv).rgb;
}

vec3 previousOpaque(vec2 uv) {
    return texture(PreviousOpaqueSampler, uv).rgb;
}

vec3 rgbToYCoCg(vec3 color) {
    return vec3(
        dot(color, vec3(0.25, 0.5, 0.25)),
        color.r * 0.5 - color.b * 0.5,
        color.g * 0.5 - color.r * 0.25 - color.b * 0.25
    );
}

vec2 reprojectedUv(ivec2 pixel) {
    ivec2 currentPixel = clampPixel(pixel);
    vec2 motionPixels = texelFetch(MotionVectorSampler, currentPixel, 0).rg;
    vec2 previousPixelCenter = vec2(currentPixel) + vec2(0.5) + motionPixels + MaskConfig.zw;
    vec2 halfTexel = 0.5 / MaskConfig.xy;
    return clamp(previousPixelCenter / MaskConfig.xy, halfTexel, 1.0 - halfTexel);
}

float temporalCompositionChange(ivec2 currentPixel, vec2 previousUv) {
    vec3 currentPre = rgbToYCoCg(currentOpaque(currentPixel));
    vec3 currentPost = rgbToYCoCg(currentColor(currentPixel));
    vec3 previousPre = rgbToYCoCg(previousOpaque(previousUv));
    vec3 previousPost = rgbToYCoCg(previousColor(previousUv));
    vec3 contributionChange = abs(abs(currentPost - currentPre) - abs(previousPost - previousPre));
    return clamp(dot(contributionChange, vec3(1.0)), 0.0, 1.0);
}

// AMD's experimental TCR path uses this conservative prefilter before the binary
// composition-change test. It rejects tiny jitter-only differences that would otherwise
// invalidate history at every hard water/cloud edge.
float temporalCompositionGate(ivec2 currentPixel, vec2 previousUv) {
    vec3 currentPre = rgbToYCoCg(currentOpaque(currentPixel));
    vec3 currentPost = rgbToYCoCg(currentColor(currentPixel));
    vec3 previousPre = rgbToYCoCg(previousOpaque(previousUv));
    vec3 previousPost = rgbToYCoCg(previousColor(previousUv));
    vec3 currentContribution = currentPost - currentPre;
    vec3 previousContribution = previousPost - previousPre;
    bool hasTransparency = any(greaterThan(abs(currentContribution), vec3(AUTOGEN_EPSILON)));
    bool hadTransparency = any(greaterThan(abs(previousContribution), vec3(AUTOGEN_EPSILON)));
    if (!hasTransparency && !hadTransparency) {
        return 0.0;
    }

    vec3 opaqueChange = currentPre - previousPre;
    vec3 finalChange = currentPost - previousPost;
    vec3 estimatedContribution = finalChange / max(vec3(AUTOGEN_EPSILON), opaqueChange);
    float gate = max(estimatedContribution.x, max(estimatedContribution.y, estimatedContribution.z));
    return clamp(gate * length(currentPost - previousPost), 0.0, 1.0);
}

// Returns opaque and transparent edge change in x/y. Each neighbor uses its own motion vector so
// a foreground block does not force its motion onto adjacent water, sky, or clouds.
vec2 temporalEdgeChanges(ivec2 currentPixel) {
    float opaqueDifference[9];
    float transparentDifference[9];
    int index = 0;
    for (int y = -1; y <= 1; ++y) {
        for (int x = -1; x <= 1; ++x) {
            ivec2 samplePixel = currentPixel + ivec2(x, y);
            vec2 previousSampleUv = reprojectedUv(samplePixel);
            vec3 currentOpaqueSample = currentOpaque(samplePixel);
            vec3 previousOpaqueSample = previousOpaque(previousSampleUv);
            vec3 currentContribution = abs(currentColor(samplePixel) - currentOpaqueSample);
            vec3 previousContribution = abs(previousColor(previousSampleUv) - previousOpaqueSample);
            opaqueDifference[index] = length(currentOpaqueSample - previousOpaqueSample);
            transparentDifference[index] = length(currentContribution - previousContribution);
            ++index;
        }
    }

    float opaqueGradientX = abs(opaqueDifference[3] - opaqueDifference[4])
        * abs(opaqueDifference[5] - opaqueDifference[4]);
    float opaqueGradientY = abs(opaqueDifference[1] - opaqueDifference[4])
        * abs(opaqueDifference[7] - opaqueDifference[4]);
    float transparentGradientX = abs(transparentDifference[3] - transparentDifference[4])
        * abs(transparentDifference[5] - transparentDifference[4]);
    float transparentGradientY = abs(transparentDifference[1] - transparentDifference[4])
        * abs(transparentDifference[7] - transparentDifference[4]);
    return sqrt(sqrt(max(
        vec2(0.0),
        vec2(
            opaqueGradientX * opaqueGradientY,
            transparentGradientX * transparentGradientY
        )
    )));
}

void main() {
    ivec2 currentPixel = clampPixel(ivec2(texCoord * MaskConfig.xy));
    vec2 previousUv = reprojectedUv(currentPixel);

    float compositionCandidate = 0.0;
    if (temporalCompositionGate(currentPixel, previousUv) > TC_GATE_THRESHOLD) {
        float compositionChange = temporalCompositionChange(currentPixel, previousUv);
        compositionCandidate = compositionChange < TC_THRESHOLD ? 0.0 : 1.0;
    }
    float transparencyAndComposition = compositionCandidate * TC_SCALE;

    float reactive = 0.0;
    if (compositionCandidate > 0.5) {
        vec2 edgeChanges = temporalEdgeChanges(currentPixel);
        reactive = clamp((edgeChanges.y - edgeChanges.x) * REACTIVE_SCALE, 0.0, REACTIVE_MAX);
    }
    vec2 uv = (vec2(currentPixel) + 0.5) / MaskConfig.xy;
    vec2 motionPixels = texelFetch(MotionVectorSampler, currentPixel, 0).rg;
    reactive = max(reactive*Reactivity.x, temporalColorRejection(CurrentColorSampler, PreviousColorSampler,
            uv, MaskConfig.xy, motionPixels, MaskConfig.zw,Reactivity));

    reactiveMaskOutput = vec4(clamp(reactive,0.0,Reactivity.z), 0.0, 0.0, 1.0);
    transparencyMaskOutput = vec4(transparencyAndComposition, 0.0, 0.0, 1.0);
}
