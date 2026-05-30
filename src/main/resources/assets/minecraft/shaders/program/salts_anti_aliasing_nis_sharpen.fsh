#version 150
/*
 * Salt's Anti Aliasing post-processing shader.
 *
 * NIS-style sharpening pass used to restore local detail after scaled rendering.
 * Runtime JSON effects bind the samplers and uniform blocks; these comments describe the pass data flow.
 */


// Scene, history, depth, or helper textures supplied by Minecraft's post-effect chain.
uniform sampler2D DiffuseSampler;

// Packed runtime parameters updated from Java when resolution, mode, or config changes.
uniform vec2 OutSize;
uniform vec2 InSize;

uniform float Sharpness;
uniform float EdgeBoost;
uniform float ClampBoost;

// Full-screen pass coordinates and final color output for the current pixel.
in vec2 texCoord;

out vec4 fragColor;

float luma(vec3 color) {
    return dot(color, vec3(0.299, 0.587, 0.114));
}

// Executes the per-pixel resolve, upscale, sharpen, or debug operation for this pass.
void main() {
    // Work in texel-relative offsets so the same math scales across window sizes.
    vec2 texelSize = 1.0 / InSize;

    vec3 center = texture(DiffuseSampler, texCoord).rgb;
    vec3 north = texture(DiffuseSampler, texCoord + vec2(0.0, -texelSize.y)).rgb;
    vec3 south = texture(DiffuseSampler, texCoord + vec2(0.0, texelSize.y)).rgb;
    vec3 west = texture(DiffuseSampler, texCoord + vec2(-texelSize.x, 0.0)).rgb;
    vec3 east = texture(DiffuseSampler, texCoord + vec2(texelSize.x, 0.0)).rgb;
    vec3 northWest = texture(DiffuseSampler, texCoord + vec2(-texelSize.x, -texelSize.y)).rgb;
    vec3 northEast = texture(DiffuseSampler, texCoord + vec2(texelSize.x, -texelSize.y)).rgb;
    vec3 southWest = texture(DiffuseSampler, texCoord + vec2(-texelSize.x, texelSize.y)).rgb;
    vec3 southEast = texture(DiffuseSampler, texCoord + vec2(texelSize.x, texelSize.y)).rgb;

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
    fragColor = vec4(outputColor, 1.0);
}
