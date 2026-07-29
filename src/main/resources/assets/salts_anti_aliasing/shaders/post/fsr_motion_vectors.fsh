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
    vec4 PreviousViewProjection0;
    vec4 PreviousViewProjection1;
    vec4 PreviousViewProjection2;
    vec4 PreviousViewProjection3;
    vec4 MotionConfig;
};

in vec2 texCoord;

out vec4 fragColor;

void main() {
    float depth = texture(SceneDepthSampler, texCoord).r;
    if (depth >= 1.0) {
        fragColor = vec4(0.0);
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

    vec2 currentNdc = texCoord * 2.0 - 1.0;
    vec4 currentClip = vec4(currentNdc, depth * 2.0 - 1.0, 1.0);
    vec4 world = currentClipToWorld * currentClip;
    world /= max(abs(world.w), 0.00001);

    vec4 previousClip = previousViewProjection * world;
    vec2 previousNdc = previousClip.xy / max(abs(previousClip.w), 0.00001);
    vec2 previousUv = previousNdc * 0.5 + 0.5;
    vec2 velocityPixels = (previousUv - texCoord) * MotionConfig.xy;

    fragColor = vec4(velocityPixels, 0.0, 1.0);
}
