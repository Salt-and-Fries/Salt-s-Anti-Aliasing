#version 330

uniform sampler2D SceneDepthSampler;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(std140) uniform FsrMotionConfig {
    vec4 CurrentClipToWorld0;
    vec4 CurrentClipToWorld1;
    vec4 CurrentClipToWorld2;
    vec4 CurrentClipToWorld3;
    vec4 CurrentViewProjection0;
    vec4 CurrentViewProjection1;
    vec4 CurrentViewProjection2;
    vec4 CurrentViewProjection3;
    vec4 PreviousViewProjection0;
    vec4 PreviousViewProjection1;
    vec4 PreviousViewProjection2;
    vec4 PreviousViewProjection3;
    vec4 MotionConfig;
    vec4 CameraTranslationToPrevious;
};

in vec2 texCoord;

out vec4 fragColor;

void main() {
    float depth = texture(SceneDepthSampler, texCoord).r;

    mat4 currentClipToWorld = mat4(
        CurrentClipToWorld0,
        CurrentClipToWorld1,
        CurrentClipToWorld2,
        CurrentClipToWorld3
    );
    mat4 currentViewProjection = mat4(
        CurrentViewProjection0,
        CurrentViewProjection1,
        CurrentViewProjection2,
        CurrentViewProjection3
    );
    mat4 previousViewProjection = mat4(
        PreviousViewProjection0,
        PreviousViewProjection1,
        PreviousViewProjection2,
        PreviousViewProjection3
    );

    vec2 currentNdc = texCoord * 2.0 - 1.0;
    // Vulkan's zero-to-one clip convention consumes the sampled depth unchanged.
    vec4 currentClip = vec4(currentNdc, depth, 1.0);
    vec4 world = currentClipToWorld * currentClip;
    if (abs(world.w) <= 0.00001) {
        fragColor = vec4(0.0);
        return;
    }
    world /= world.w;

    vec4 previousWorld = world;
    if (depth <= 0.0000001) {
        // Reverse-Z clears the background to zero. The reconstructed point is only an arbitrary
        // far-plane stand-in, so translating it with the camera creates false sky parallax.
        previousWorld.xyz += CameraTranslationToPrevious.xyz;
    }

    vec4 currentUnjitteredClip = currentViewProjection * world;
    vec4 previousClip = previousViewProjection * previousWorld;
    if (currentUnjitteredClip.w <= 0.00001 || previousClip.w <= 0.00001) {
        fragColor = vec4(0.0);
        return;
    }

    vec2 currentUnjitteredNdc = currentUnjitteredClip.xy / currentUnjitteredClip.w;
    vec2 previousNdc = previousClip.xy / previousClip.w;
    vec2 currentUnjitteredUv = currentUnjitteredNdc * 0.5 + 0.5;
    vec2 previousUv = previousNdc * 0.5 + 0.5;
    vec2 velocityPixels = (previousUv - currentUnjitteredUv) * MotionConfig.xy;

    fragColor = vec4(velocityPixels, 0.0, 1.0);
}
