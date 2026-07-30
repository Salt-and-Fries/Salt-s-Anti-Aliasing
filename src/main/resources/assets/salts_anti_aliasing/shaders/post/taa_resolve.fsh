#version 330
/*
 * Camera-reprojected temporal anti-aliasing for Minecraft's finite reverse-Z renderer.
 * The current sample stays sharp; only validated, reprojected history contributes.
 */

uniform sampler2D CurrentSampler;
uniform sampler2D CurrentDepthSampler;
uniform sampler2D HistorySampler;
uniform sampler2D HistoryDepthSampler;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(std140) uniform TaaConfig {
    vec4 CurrentClipToWorld0;
    vec4 CurrentClipToWorld1;
    vec4 CurrentClipToWorld2;
    vec4 CurrentClipToWorld3;
    vec4 PreviousViewProjection0;
    vec4 PreviousViewProjection1;
    vec4 PreviousViewProjection2;
    vec4 PreviousViewProjection3;
    float BaseHistoryWeight;
    float LumaRejection;
    float DepthRejection;
    float VarianceGamma;
    float CurrentJitterX;
    float CurrentJitterY;
    float PreviousJitterX;
    float PreviousJitterY;
    float CameraMotion;
};

in vec2 texCoord;
out vec4 fragColor;

vec3 rgbToYCoCg(vec3 color) {
    float co = color.r - color.b;
    float temporary = color.b + co * 0.5;
    float cg = color.g - temporary;
    float y = temporary + cg * 0.5;
    return vec3(y, co, cg);
}

vec3 yCoCgToRgb(vec3 color) {
    float temporary = color.x - color.z * 0.5;
    float g = color.z + temporary;
    float b = temporary - color.y * 0.5;
    float r = b + color.y;
    return vec3(r, g, b);
}

vec3 clipToAabb(vec3 value, vec3 minimumValue, vec3 maximumValue) {
    vec3 center = (minimumValue + maximumValue) * 0.5;
    vec3 extent = max((maximumValue - minimumValue) * 0.5, vec3(0.00001));
    vec3 offset = value - center;
    float maximumUnit = max(max(abs(offset.x / extent.x), abs(offset.y / extent.y)), abs(offset.z / extent.z));
    return center + offset / max(1.0, maximumUnit);
}

vec3 sampleHistoryCatmullRom(vec2 uv) {
    vec2 textureSizeValue = max(InSize, vec2(1.0));
    vec2 samplePosition = uv * textureSizeValue;
    vec2 centerPosition = floor(samplePosition - 0.5) + 0.5;
    vec2 fraction = samplePosition - centerPosition;

    vec2 weight0 = fraction * (-0.5 + fraction * (1.0 - 0.5 * fraction));
    vec2 weight1 = 1.0 + fraction * fraction * (-2.5 + 1.5 * fraction);
    vec2 weight2 = fraction * (0.5 + fraction * (2.0 - 1.5 * fraction));
    vec2 weight3 = fraction * fraction * (-0.5 + 0.5 * fraction);
    vec2 weight12 = weight1 + weight2;
    vec2 offset12 = weight2 / max(weight12, vec2(0.00001));

    vec2 texel = 1.0 / textureSizeValue;
    vec2 position0 = (centerPosition - 1.0) * texel;
    vec2 position12 = (centerPosition + offset12) * texel;
    vec2 position3 = (centerPosition + 2.0) * texel;
    vec2 minimumUv = texel * 0.5;
    vec2 maximumUv = vec2(1.0) - minimumUv;

    position0 = clamp(position0, minimumUv, maximumUv);
    position12 = clamp(position12, minimumUv, maximumUv);
    position3 = clamp(position3, minimumUv, maximumUv);

    vec3 result = vec3(0.0);
    result += texture(HistorySampler, vec2(position0.x, position0.y)).rgb * weight0.x * weight0.y;
    result += texture(HistorySampler, vec2(position12.x, position0.y)).rgb * weight12.x * weight0.y;
    result += texture(HistorySampler, vec2(position3.x, position0.y)).rgb * weight3.x * weight0.y;
    result += texture(HistorySampler, vec2(position0.x, position12.y)).rgb * weight0.x * weight12.y;
    result += texture(HistorySampler, vec2(position12.x, position12.y)).rgb * weight12.x * weight12.y;
    result += texture(HistorySampler, vec2(position3.x, position12.y)).rgb * weight3.x * weight12.y;
    result += texture(HistorySampler, vec2(position0.x, position3.y)).rgb * weight0.x * weight3.y;
    result += texture(HistorySampler, vec2(position12.x, position3.y)).rgb * weight12.x * weight3.y;
    result += texture(HistorySampler, vec2(position3.x, position3.y)).rgb * weight3.x * weight3.y;
    return result;
}

void main() {
    vec2 texel = 1.0 / max(InSize, vec2(1.0));
    vec3 currentRgb = texture(CurrentSampler, texCoord).rgb;
    float currentDepth = texture(CurrentDepthSampler, texCoord).r;

    // Vulkan 26.2 uses finite reverse-Z: zero is the far clear value and one is near.
    if (currentDepth <= 0.000001) {
        fragColor = vec4(currentRgb, 1.0);
        return;
    }

    mat4 currentClipToWorld = mat4(
        CurrentClipToWorld0,
        CurrentClipToWorld1,
        CurrentClipToWorld2,
        CurrentClipToWorld3
    );
    mat4 previousViewProjection = mat4(
        PreviousViewProjection0,
        PreviousViewProjection1,
        PreviousViewProjection2,
        PreviousViewProjection3
    );

    vec4 currentClip = vec4(texCoord * 2.0 - 1.0, currentDepth, 1.0);
    vec4 worldPosition = currentClipToWorld * currentClip;
    if (abs(worldPosition.w) <= 0.000001) {
        fragColor = vec4(currentRgb, 1.0);
        return;
    }
    worldPosition /= worldPosition.w;

    vec4 previousClip = previousViewProjection * worldPosition;
    if (previousClip.w <= 0.000001) {
        fragColor = vec4(currentRgb, 1.0);
        return;
    }

    vec3 previousNdc = previousClip.xyz / previousClip.w;
    vec2 previousUnjitteredUv = previousNdc.xy * 0.5 + 0.5;
    vec2 previousJitterUv = vec2(PreviousJitterX, -PreviousJitterY) * texel;
    vec2 historyUv = previousUnjitteredUv + previousJitterUv;
    vec2 historyMargin = texel * 1.5;
    if (any(lessThan(historyUv, historyMargin)) || any(greaterThan(historyUv, vec2(1.0) - historyMargin))) {
        fragColor = vec4(currentRgb, 1.0);
        return;
    }

    float historyDepth = texture(HistoryDepthSampler, historyUv).r;
    float expectedHistoryDepth = previousNdc.z;
    float depthScale = max(max(abs(historyDepth), abs(expectedHistoryDepth)), 0.0001);
    float relativeDepthError = abs(historyDepth - expectedHistoryDepth) / depthScale;
    float depthConfidence = 1.0 - smoothstep(
        0.01,
        0.05,
        relativeDepthError * max(DepthRejection, 0.0001)
    );

    vec3 neighborhoodMinimum = vec3(100000.0);
    vec3 neighborhoodMaximum = vec3(-100000.0);
    vec3 neighborhoodMean = vec3(0.0);
    vec3 neighborhoodSecondMoment = vec3(0.0);
    for (int y = -1; y <= 1; ++y) {
        for (int x = -1; x <= 1; ++x) {
            vec2 sampleUv = clamp(
                texCoord + vec2(float(x), float(y)) * texel,
                texel * 0.5,
                vec2(1.0) - texel * 0.5
            );
            vec3 sampleValue = rgbToYCoCg(texture(CurrentSampler, sampleUv).rgb);
            neighborhoodMinimum = min(neighborhoodMinimum, sampleValue);
            neighborhoodMaximum = max(neighborhoodMaximum, sampleValue);
            neighborhoodMean += sampleValue;
            neighborhoodSecondMoment += sampleValue * sampleValue;
        }
    }

    neighborhoodMean /= 9.0;
    neighborhoodSecondMoment /= 9.0;
    vec3 standardDeviation = sqrt(max(neighborhoodSecondMoment - neighborhoodMean * neighborhoodMean, vec3(0.0)));
    vec3 varianceMinimum = max(neighborhoodMinimum, neighborhoodMean - standardDeviation * VarianceGamma);
    vec3 varianceMaximum = min(neighborhoodMaximum, neighborhoodMean + standardDeviation * VarianceGamma);

    vec3 historyValue = rgbToYCoCg(sampleHistoryCatmullRom(historyUv));
    vec3 clippedHistory = clipToAabb(historyValue, varianceMinimum, varianceMaximum);
    vec3 currentValue = rgbToYCoCg(currentRgb);

    float lumaScale = max(max(abs(currentValue.x), abs(clippedHistory.x)), 0.1);
    float relativeLumaError = abs(currentValue.x - clippedHistory.x) / lumaScale;
    float lumaConfidence = 1.0 - smoothstep(
        0.04,
        0.25,
        relativeLumaError * max(LumaRejection, 0.0001)
    );
    float clippingDistance = length(historyValue - clippedHistory);
    float clippingConfidence = 1.0 - smoothstep(0.02, 0.30, clippingDistance);
    float motionPixels = length((historyUv - texCoord) * InSize);
    float motionConfidence = 1.0 / (1.0 + motionPixels * 0.025);
    float cameraConfidence = mix(1.0, 0.65, clamp(CameraMotion, 0.0, 1.0));

    float historyWeight = clamp(
        BaseHistoryWeight
        * depthConfidence
        * lumaConfidence
        * clippingConfidence
        * motionConfidence
        * cameraConfidence,
        0.0,
        0.95
    );

    vec3 resolved = mix(currentValue, clippedHistory, historyWeight);
    fragColor = vec4(max(yCoCgToRgb(resolved), vec3(0.0)), 1.0);
}
