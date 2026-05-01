#version 330
/*
 * Salt's Anti Aliasing post-processing shader.
 *
 * FSR 1 EASU-style upscale pass that reconstructs a native-resolution scene from a lower internal render scale.
 * The JSON post-effect definitions bind these samplers and uniform blocks at runtime,
 * so the shader comments focus on the math and data flow inside the pass.
 */


// Scene, history, depth, or helper textures supplied by Minecraft's post-effect chain.
uniform sampler2D InSampler;

// Packed runtime parameters; Java updates these values each frame or whenever config changes.
layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(std140) uniform Fsr1Config {
    float SourceScale;
    float EdgeBlend;
    float DetailBoost;
    float ClampBoost;
};

// Full-screen pass coordinates and final color output for the current pixel.
in vec2 texCoord;

out vec4 fragColor;

float luma(vec3 color) {
    return dot(color, vec3(0.299, 0.587, 0.114));
}

vec3 sampleScene(vec2 offset) {
    return texture(InSampler, texCoord + offset).rgb;
}

// Executes the per-pixel resolve/upscale/debug operation for this pass.
void main() {
    // Work in texel-relative offsets so the same shader scales across window sizes.
    vec2 sourceTexel = 1.0 / max(InSize, vec2(1.0));

    vec3 center = texture(InSampler, texCoord).rgb;
    vec3 north = sampleScene(vec2(0.0, -sourceTexel.y));
    vec3 south = sampleScene(vec2(0.0, sourceTexel.y));
    vec3 west = sampleScene(vec2(-sourceTexel.x, 0.0));
    vec3 east = sampleScene(vec2(sourceTexel.x, 0.0));
    vec3 northWest = sampleScene(vec2(-sourceTexel.x, -sourceTexel.y));
    vec3 northEast = sampleScene(vec2(sourceTexel.x, -sourceTexel.y));
    vec3 southWest = sampleScene(vec2(-sourceTexel.x, sourceTexel.y));
    vec3 southEast = sampleScene(vec2(sourceTexel.x, sourceTexel.y));

    float lumaNorth = luma(north);
    float lumaSouth = luma(south);
    float lumaWest = luma(west);
    float lumaEast = luma(east);
    float lumaNorthWest = luma(northWest);
    float lumaNorthEast = luma(northEast);
    float lumaSouthWest = luma(southWest);
    float lumaSouthEast = luma(southEast);

    vec2 gradient = vec2(
            (lumaEast - lumaWest) + 0.5 * ((lumaNorthEast + lumaSouthEast) - (lumaNorthWest + lumaSouthWest)),
            (lumaSouth - lumaNorth) + 0.5 * ((lumaSouthWest + lumaSouthEast) - (lumaNorthWest + lumaNorthEast))
    );

    float gradientMagnitude = max(length(gradient), 0.00001);
    vec2 gradientDir = gradient / gradientMagnitude;
    vec2 edgeDir = vec2(-gradientDir.y, gradientDir.x);
    float edgeStrength = clamp(gradientMagnitude * mix(6.0, 9.0, 1.0 - SourceScale), 0.0, 1.0);

    vec2 edgeOffset = edgeDir * sourceTexel * mix(0.35, 0.65, 1.0 - SourceScale);
    vec3 alongEdgeA = sampleScene(edgeOffset);
    vec3 alongEdgeB = sampleScene(-edgeOffset);
    vec3 directionalBlend = (center * 2.0 + alongEdgeA + alongEdgeB) * 0.25;

    vec3 blurred =
            center * 0.25 +
            (north + south + west + east) * 0.125 +
            (northWest + northEast + southWest + southEast) * 0.0625;
    vec3 detailEnhanced = center + (center - blurred) * (DetailBoost * mix(0.8, 1.35, edgeStrength));

    vec3 reconstructed = mix(detailEnhanced, directionalBlend, edgeStrength * EdgeBlend);

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

    fragColor = vec4(clamp(reconstructed, minNeighborhood - clampPad, maxNeighborhood + clampPad), 1.0);
}
