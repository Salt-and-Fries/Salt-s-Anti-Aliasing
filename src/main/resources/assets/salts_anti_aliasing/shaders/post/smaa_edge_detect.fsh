#version 330

uniform sampler2D InSampler;
uniform sampler2D DepthSampler;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(std140) uniform SmaaEdgeConfig {
    float EdgeThreshold;
    float LocalContrastFactor;
    float DiagonalFactor;
    float AvoidTextures;
};

in vec2 texCoord;

out vec4 fragColor;

float luma(vec3 color) {
    return dot(color, vec3(0.299, 0.587, 0.114));
}

void main() {
    vec2 texel = 1.0 / InSize;

    float center = luma(texture(InSampler, texCoord).rgb);
    float left = luma(texture(InSampler, texCoord + vec2(-texel.x, 0.0)).rgb);
    float right = luma(texture(InSampler, texCoord + vec2(texel.x, 0.0)).rgb);
    float up = luma(texture(InSampler, texCoord + vec2(0.0, -texel.y)).rgb);
    float down = luma(texture(InSampler, texCoord + vec2(0.0, texel.y)).rgb);
    float upLeft = luma(texture(InSampler, texCoord + vec2(-texel.x, -texel.y)).rgb);
    float downRight = luma(texture(InSampler, texCoord + vec2(texel.x, texel.y)).rgb);
    float upRight = luma(texture(InSampler, texCoord + vec2(texel.x, -texel.y)).rgb);
    float downLeft = luma(texture(InSampler, texCoord + vec2(-texel.x, texel.y)).rgb);

    float depthCenter = texture(DepthSampler, texCoord).r;
    float depthLeft = texture(DepthSampler, texCoord + vec2(-texel.x, 0.0)).r;
    float depthRight = texture(DepthSampler, texCoord + vec2(texel.x, 0.0)).r;
    float depthUp = texture(DepthSampler, texCoord + vec2(0.0, -texel.y)).r;
    float depthDown = texture(DepthSampler, texCoord + vec2(0.0, texel.y)).r;

    float horizontalDelta = max(abs(center - left), abs(center - right));
    float verticalDelta = max(abs(center - up), abs(center - down));
    float diagonalDelta = max(
            max(abs(center - upLeft), abs(center - downRight)),
            max(abs(center - upRight), abs(center - downLeft))
    );

    float localContrast = max(horizontalDelta, max(verticalDelta, diagonalDelta));
    float adaptiveThreshold = mix(EdgeThreshold, EdgeThreshold * LocalContrastFactor, smoothstep(0.04, 0.16, localContrast));

    float horizontalEdge = smoothstep(adaptiveThreshold, adaptiveThreshold * 2.25, horizontalDelta + diagonalDelta * DiagonalFactor);
    float verticalEdge = smoothstep(adaptiveThreshold, adaptiveThreshold * 2.25, verticalDelta + diagonalDelta * DiagonalFactor);

    float horizontalDepth = max(abs(depthCenter - depthLeft), abs(depthCenter - depthRight));
    float verticalDepth = max(abs(depthCenter - depthUp), abs(depthCenter - depthDown));
    horizontalEdge = mix(horizontalEdge, smoothstep(0.00002, 0.0012, horizontalDepth), step(0.5, AvoidTextures));
    verticalEdge = mix(verticalEdge, smoothstep(0.00002, 0.0012, verticalDepth), step(0.5, AvoidTextures));

    fragColor = vec4(horizontalEdge, verticalEdge, 0.0, 1.0);
}
