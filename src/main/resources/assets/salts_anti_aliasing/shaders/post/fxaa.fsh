#version 330

/*
 * NVIDIA FXAA 3.11 by Timothy Lottes
 * Adapted for Salt's Anti Aliasing from the NVIDIA GameWorks reference.
 *
 * Copyright (c) 2014-2015, NVIDIA CORPORATION. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *  * Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *  * Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 *  * Neither the name of NVIDIA CORPORATION nor the names of its
 *    contributors may be used to endorse or promote products derived
 *    from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS ``AS IS'' AND ANY
 * EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY
 * OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

/*
 * FXAA Quality 3.11-style resolve.
 *
 * This follows NVIDIA's reference algorithm: local-contrast rejection, 3x3
 * edge classification, a bidirectional end-of-edge search, and a final
 * subpixel-sized sample perpendicular to the edge.  Preset 28's search
 * schedule is used for high quality without the wide convolution blur that
 * the old pass introduced.
 */

uniform sampler2D InSampler;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(std140) uniform FxaaConfig {
    float SubpixelBlend;
    float EdgeThreshold;
    float EdgeThresholdMin;
};

in vec2 texCoord;
out vec4 fragColor;

const int FXAA_SEARCH_STEP_COUNT = 11;
const float FXAA_SEARCH_STEPS[FXAA_SEARCH_STEP_COUNT] = float[](
    1.0, 1.5,
    2.0, 2.0, 2.0, 2.0, 2.0, 2.0, 2.0,
    4.0, 8.0
);

vec2 clampUv(vec2 uv, vec2 texel) {
    vec2 halfTexel = texel * 0.5;
    return clamp(uv, halfTexel, vec2(1.0) - halfTexel);
}

vec4 sampleScene(vec2 uv, vec2 texel) {
    // Explicit LOD keeps every search tap in the same full-resolution image.
    return textureLod(InSampler, clampUv(uv, texel), 0.0);
}

float fxaaLuma(vec3 color) {
    // FXAA expects perceptually encoded display RGB, which Minecraft's final
    // scene target provides at this point in the frame.
    return dot(color, vec3(0.299, 0.587, 0.114));
}

float sampleLuma(vec2 uv, vec2 texel) {
    return fxaaLuma(sampleScene(uv, texel).rgb);
}

void main() {
    vec2 texel = 1.0 / max(InSize, vec2(1.0));
    vec2 posM = texCoord;

    vec4 colorM = sampleScene(posM, texel);
    float lumaM = fxaaLuma(colorM.rgb);
    float lumaN = sampleLuma(posM + vec2(0.0, -texel.y), texel);
    float lumaS = sampleLuma(posM + vec2(0.0, texel.y), texel);
    float lumaW = sampleLuma(posM + vec2(-texel.x, 0.0), texel);
    float lumaE = sampleLuma(posM + vec2(texel.x, 0.0), texel);

    float rangeMax = max(lumaM, max(max(lumaN, lumaS), max(lumaW, lumaE)));
    float rangeMin = min(lumaM, min(min(lumaN, lumaS), min(lumaW, lumaE)));
    float lumaRange = rangeMax - rangeMin;
    if (lumaRange < max(EdgeThresholdMin, rangeMax * EdgeThreshold)) {
        fragColor = colorM;
        return;
    }

    float lumaNW = sampleLuma(posM + vec2(-texel.x, -texel.y), texel);
    float lumaNE = sampleLuma(posM + vec2(texel.x, -texel.y), texel);
    float lumaSW = sampleLuma(posM + vec2(-texel.x, texel.y), texel);
    float lumaSE = sampleLuma(posM + vec2(texel.x, texel.y), texel);

    float lumaNS = lumaN + lumaS;
    float lumaWE = lumaW + lumaE;
    float edgeHorz1 = (-2.0 * lumaM) + lumaNS;
    float edgeVert1 = (-2.0 * lumaM) + lumaWE;
    float edgeHorz2 = (-2.0 * lumaE) + lumaNE + lumaSE;
    float edgeVert2 = (-2.0 * lumaN) + lumaNW + lumaNE;
    float edgeHorz3 = (-2.0 * lumaW) + lumaNW + lumaSW;
    float edgeVert3 = (-2.0 * lumaS) + lumaSW + lumaSE;
    float edgeHorz = abs(edgeHorz3) + 2.0 * abs(edgeHorz1) + abs(edgeHorz2);
    float edgeVert = abs(edgeVert3) + 2.0 * abs(edgeVert1) + abs(edgeVert2);
    bool horzSpan = edgeHorz >= edgeVert;

    // Estimate isolated subpixel aliasing with the reference 3x3 low-pass
    // polynomial.  The result remains in [0, 1], unlike the old overdriven
    // blend, so fine texture detail is retained.
    float subpixA = (lumaNS + lumaWE) * 2.0
        + lumaNW + lumaNE + lumaSW + lumaSE;
    float subpixB = (subpixA / 12.0) - lumaM;
    float subpixC = clamp(abs(subpixB) / max(lumaRange, 1e-6), 0.0, 1.0);
    float subpixD = (-2.0 * subpixC) + 3.0;
    float subpixE = subpixC * subpixC;
    float subpixF = subpixD * subpixE;
    float subpixelOffset = subpixF * subpixF * clamp(SubpixelBlend, 0.0, 1.0);

    // Choose the highest-contrast pair perpendicular to the edge.
    float pairLumaN = horzSpan ? lumaN : lumaW;
    float pairLumaS = horzSpan ? lumaS : lumaE;
    float gradientN = pairLumaN - lumaM;
    float gradientS = pairLumaS - lumaM;
    bool pairN = abs(gradientN) >= abs(gradientS);
    float gradient = max(abs(gradientN), abs(gradientS));
    float pairedLuma = pairN ? pairLumaN : pairLumaS;
    float pairAverage = 0.5 * (lumaM + pairedLuma);

    float lengthSign = horzSpan ? texel.y : texel.x;
    if (pairN) {
        lengthSign = -lengthSign;
    }

    vec2 posB = posM;
    if (horzSpan) {
        posB.y += 0.5 * lengthSign;
    } else {
        posB.x += 0.5 * lengthSign;
    }

    // Search along the edge using FXAA Quality preset 28. Bilinear sampling
    // at the pair midpoint is intentional and part of the reference method.
    vec2 edgeStep = horzSpan ? vec2(texel.x, 0.0) : vec2(0.0, texel.y);
    vec2 posN = posB - edgeStep * FXAA_SEARCH_STEPS[0];
    vec2 posP = posB + edgeStep * FXAA_SEARCH_STEPS[0];
    float gradientThreshold = gradient * 0.25;
    float lumaEndN = 0.0;
    float lumaEndP = 0.0;
    bool doneN = false;
    bool doneP = false;

    // The reference preset samples P0..P9 and, if still unfinished, advances
    // by the final P10 distance before measuring the bounded span.
    for (int i = 0; i < FXAA_SEARCH_STEP_COUNT - 1; ++i) {
        if (!doneN) {
            lumaEndN = sampleLuma(posN, texel) - pairAverage;
            doneN = abs(lumaEndN) >= gradientThreshold;
        }
        if (!doneP) {
            lumaEndP = sampleLuma(posP, texel) - pairAverage;
            doneP = abs(lumaEndP) >= gradientThreshold;
        }
        if (doneN && doneP) {
            break;
        }
        float nextStep = FXAA_SEARCH_STEPS[i + 1];
        if (!doneN) {
            posN -= edgeStep * nextStep;
        }
        if (!doneP) {
            posP += edgeStep * nextStep;
        }
    }

    float distanceN = horzSpan ? (posM.x - posN.x) : (posM.y - posN.y);
    float distanceP = horzSpan ? (posP.x - posM.x) : (posP.y - posM.y);
    float spanLength = max(distanceN + distanceP, 1e-6);
    bool directionN = distanceN < distanceP;
    bool centerBelowPair = (lumaM - pairAverage) < 0.0;
    bool goodSpanN = (lumaEndN < 0.0) != centerBelowPair;
    bool goodSpanP = (lumaEndP < 0.0) != centerBelowPair;
    bool goodSpan = directionN ? goodSpanN : goodSpanP;
    float edgeOffset = goodSpan
        ? 0.5 - min(distanceN, distanceP) / spanLength
        : 0.0;

    // The geometric edge resolve is bounded to half a pixel. The standard
    // subpixel term may reach the configured 0.75-pixel quality default for
    // isolated single-pixel aliasing, exactly as in FXAA Quality 3.11.
    float pixelOffset = max(edgeOffset, subpixelOffset);
    if (horzSpan) {
        posM.y += pixelOffset * lengthSign;
    } else {
        posM.x += pixelOffset * lengthSign;
    }

    vec4 resolved = sampleScene(posM, texel);
    fragColor = vec4(resolved.rgb, colorM.a);
}
