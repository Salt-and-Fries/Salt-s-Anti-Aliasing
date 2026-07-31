#version 330
/*
 * Salt's Anti Aliasing post-processing shader.
 *
 * NIS-style spatial reconstruction for a scene rendered below native resolution.
 * This is an original fragment-shader implementation suited to Minecraft's post
 * chain, not a copy of NVIDIA's compute shader. It keeps reconstruction separate
 * from the user-controlled sharpening pass: there is no high-pass filter or
 * sharpness parameter in this shader.
 */

uniform sampler2D InSampler;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

in vec2 texCoord;

out vec4 fragColor;

float luma(vec3 color) {
    return dot(color, vec3(0.299, 0.587, 0.114));
}

vec3 sampleSource(vec2 uv, vec2 halfTexel) {
    return texture(InSampler, clamp(uv, halfTexel, vec2(1.0) - halfTexel)).rgb;
}

vec3 reconstructPair(vec3 center, vec3 sampleA, vec3 sampleB, float localContrast) {
    // Similarity weights stop a directional filter from pulling color across an edge.
    float similarityScale = 4.0 / max(localContrast, 0.04);
    float centerLuma = luma(center);
    float weightA = exp2(-abs(luma(sampleA) - centerLuma) * similarityScale);
    float weightB = exp2(-abs(luma(sampleB) - centerLuma) * similarityScale);
    return (center * 2.0 + sampleA * weightA + sampleB * weightB)
            / (2.0 + weightA + weightB);
}

void main() {
    vec2 safeInSize = max(InSize, vec2(1.0));
    vec2 safeOutSize = max(OutSize, vec2(1.0));
    vec2 sourceTexel = 1.0 / safeInSize;
    vec2 halfTexel = sourceTexel * 0.5;

    vec4 centerSample = texture(InSampler, clamp(texCoord, halfTexel, vec2(1.0) - halfTexel));
    vec3 center = centerSample.rgb;
    vec3 north = sampleSource(texCoord + vec2(0.0, -sourceTexel.y), halfTexel);
    vec3 south = sampleSource(texCoord + vec2(0.0, sourceTexel.y), halfTexel);
    vec3 west = sampleSource(texCoord + vec2(-sourceTexel.x, 0.0), halfTexel);
    vec3 east = sampleSource(texCoord + vec2(sourceTexel.x, 0.0), halfTexel);
    vec3 northWest = sampleSource(texCoord + vec2(-sourceTexel.x, -sourceTexel.y), halfTexel);
    vec3 northEast = sampleSource(texCoord + vec2(sourceTexel.x, -sourceTexel.y), halfTexel);
    vec3 southWest = sampleSource(texCoord + vec2(-sourceTexel.x, sourceTexel.y), halfTexel);
    vec3 southEast = sampleSource(texCoord + vec2(sourceTexel.x, sourceTexel.y), halfTexel);

    float lumaNorth = luma(north);
    float lumaSouth = luma(south);
    float lumaWest = luma(west);
    float lumaEast = luma(east);
    float lumaNorthWest = luma(northWest);
    float lumaNorthEast = luma(northEast);
    float lumaSouthWest = luma(southWest);
    float lumaSouthEast = luma(southEast);

    // Sobel edge normal. Its perpendicular is the safest direction to reconstruct in.
    vec2 gradient = vec2(
            (lumaNorthEast + 2.0 * lumaEast + lumaSouthEast)
                    - (lumaNorthWest + 2.0 * lumaWest + lumaSouthWest),
            (lumaSouthWest + 2.0 * lumaSouth + lumaSouthEast)
                    - (lumaNorthWest + 2.0 * lumaNorth + lumaNorthEast)
    ) * 0.25;

    float minLuma = min(
            luma(center),
            min(
                    min(min(lumaNorth, lumaSouth), min(lumaWest, lumaEast)),
                    min(min(lumaNorthWest, lumaNorthEast), min(lumaSouthWest, lumaSouthEast))
            )
    );
    float maxLuma = max(
            luma(center),
            max(
                    max(max(lumaNorth, lumaSouth), max(lumaWest, lumaEast)),
                    max(max(lumaNorthWest, lumaNorthEast), max(lumaSouthWest, lumaSouthEast))
            )
    );
    float localContrast = maxLuma - minLuma;
    float gradientMagnitude = length(gradient);
    float normalizedGradient = gradientMagnitude / max(localContrast, 0.02);
    float edgeStrength = smoothstep(0.01, 0.08, localContrast)
            * smoothstep(0.20, 0.85, normalizedGradient);

    vec2 edgeNormal = gradientMagnitude > 0.00001
            ? gradient / gradientMagnitude
            : vec2(1.0, 0.0);
    vec2 edgeTangent = vec2(-edgeNormal.y, edgeNormal.x);

    // Evaluate the four principal NIS-style edge directions. All weights are
    // positive, so this performs reconstruction without an implicit sharpen.
    vec3 horizontal = reconstructPair(center, west, east, localContrast);
    vec3 vertical = reconstructPair(center, north, south, localContrast);
    vec3 diagonalDown = reconstructPair(center, northWest, southEast, localContrast);
    vec3 diagonalUp = reconstructPair(center, northEast, southWest, localContrast);

    const float inverseSqrtTwo = 0.70710678118;
    float horizontalWeight = pow(abs(edgeTangent.x), 8.0);
    float verticalWeight = pow(abs(edgeTangent.y), 8.0);
    float diagonalDownWeight = pow(abs(dot(edgeTangent, vec2(inverseSqrtTwo))), 8.0);
    float diagonalUpWeight = pow(abs(dot(edgeTangent, vec2(inverseSqrtTwo, -inverseSqrtTwo))), 8.0);
    float directionWeight = max(
            horizontalWeight + verticalWeight + diagonalDownWeight + diagonalUpWeight,
            0.00001
    );
    vec3 directionalReconstruction = (
            horizontal * horizontalWeight
                    + vertical * verticalWeight
                    + diagonalDown * diagonalDownWeight
                    + diagonalUp * diagonalUpWeight
    ) / directionWeight;

    float sourceScale = clamp(
            min(safeInSize.x / safeOutSize.x, safeInSize.y / safeOutSize.y),
            0.0,
            1.0
    );
    float upscaleAmount = 1.0 - sourceScale;
    float reconstructionAmount = edgeStrength * upscaleAmount * mix(0.65, 1.0, upscaleAmount);
    vec3 reconstructed = mix(center, directionalReconstruction, reconstructionAmount);

    // Keep the positive directional filter inside the source neighborhood to
    // prevent halos and color ringing around high-contrast Minecraft textures.
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

    fragColor = vec4(clamp(reconstructed, minNeighborhood, maxNeighborhood), centerSample.a);
}
