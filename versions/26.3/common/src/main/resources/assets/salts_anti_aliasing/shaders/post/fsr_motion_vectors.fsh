#version 450

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

layout(location = 0) in vec2 texCoord;

layout(location = 0) out vec4 fragColor;

#include <salts_anti_aliasing:temporal_motion.glsl>

void main() {
    mat4 currentClipToWorld = mat4(CurrentClipToWorld0, CurrentClipToWorld1,
        CurrentClipToWorld2, CurrentClipToWorld3);
    mat4 currentViewProjection = mat4(CurrentViewProjection0, CurrentViewProjection1,
        CurrentViewProjection2, CurrentViewProjection3);
    mat4 previousViewProjection = mat4(PreviousViewProjection0, PreviousViewProjection1,
        PreviousViewProjection2, PreviousViewProjection3);
    vec2 velocity = temporalMotionPixels(texture(SceneDepthSampler, texCoord).r, texCoord,
        currentClipToWorld, currentViewProjection, previousViewProjection,
        CameraTranslationToPrevious.xyz, MotionConfig.xy);
    fragColor = vec4(velocity, 0.0, 1.0);
}
