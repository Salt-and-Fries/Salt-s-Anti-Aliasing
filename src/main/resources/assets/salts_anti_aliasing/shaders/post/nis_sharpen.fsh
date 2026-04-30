#version 330

uniform sampler2D InSampler;
uniform sampler2D DepthSampler;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(std140) uniform NisSharpenConfig {
    float Sharpness;
    float EdgeBoost;
    float ClampBoost;
    float AvoidTextures;
};

in vec2 texCoord;

out vec4 fragColor;

float luma(vec3 color) {
    return dot(color, vec3(0.299, 0.587, 0.114));
}

void main() {
    vec2 texelSize = 1.0 / InSize;

    vec3 center = texture(InSampler, texCoord).rgb;
    float depthCenter = texture(DepthSampler, texCoord).r;
    vec3 north = texture(InSampler, texCoord + vec2(0.0, -texelSize.y)).rgb;
    vec3 south = texture(InSampler, texCoord + vec2(0.0, texelSize.y)).rgb;
    vec3 west = texture(InSampler, texCoord + vec2(-texelSize.x, 0.0)).rgb;
    vec3 east = texture(InSampler, texCoord + vec2(texelSize.x, 0.0)).rgb;
    vec3 northWest = texture(InSampler, texCoord + vec2(-texelSize.x, -texelSize.y)).rgb;
    vec3 northEast = texture(InSampler, texCoord + vec2(texelSize.x, -texelSize.y)).rgb;
    vec3 southWest = texture(InSampler, texCoord + vec2(-texelSize.x, texelSize.y)).rgb;
    vec3 southEast = texture(InSampler, texCoord + vec2(texelSize.x, texelSize.y)).rgb;
    float depthRange = max(
            max(abs(depthCenter - texture(DepthSampler, texCoord + vec2(0.0, -texelSize.y)).r),
                abs(depthCenter - texture(DepthSampler, texCoord + vec2(0.0, texelSize.y)).r)),
            max(abs(depthCenter - texture(DepthSampler, texCoord + vec2(-texelSize.x, 0.0)).r),
                abs(depthCenter - texture(DepthSampler, texCoord + vec2(texelSize.x, 0.0)).r))
    );

    vec3 blurred =
            center * 0.25 +
            (north + south + west + east) * 0.125 +
            (northWest + northEast + southWest + southEast) * 0.0625;

    float centerLuma = luma(center);
    float edgeSignal = max(
            max(abs(centerLuma - luma(north)), abs(centerLuma - luma(south))),
            max(abs(centerLuma - luma(west)), abs(centerLuma - luma(east)))
    );

    float adaptiveBoost = mix(0.75, EdgeBoost, smoothstep(0.01, 0.12, edgeSignal));
    vec3 sharpened = center + (center - blurred) * (Sharpness * 4.0 * adaptiveBoost);

    vec3 minNeighborhood = min(
            center,
            min(
                    min(min(north, south), min(west, east)),
                    min(min(northWest, northEast), min(southWest, southEast))
            )
    );
    vec3 maxNeighborhood = max(
            center,
            max(
                    max(max(north, south), max(west, east)),
                    max(max(northWest, northEast), max(southWest, southEast))
            )
    );

    vec3 clampPad = (maxNeighborhood - minNeighborhood) * ClampBoost;
    vec3 outputColor = clamp(sharpened, minNeighborhood - clampPad, maxNeighborhood + clampPad);
    float geometryMask = smoothstep(0.00002, 0.0012, depthRange);
    outputColor = mix(outputColor, center, step(0.5, AvoidTextures) * (1.0 - geometryMask));
    fragColor = vec4(outputColor, 1.0);
}
