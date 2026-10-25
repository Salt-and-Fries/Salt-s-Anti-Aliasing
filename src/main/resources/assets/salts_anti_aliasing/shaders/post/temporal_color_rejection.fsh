#version 450
#include <salts_anti_aliasing:temporal_color_rejection.glsl>
uniform sampler2D CurrentSampler;
uniform sampler2D PreviousSampler;
uniform sampler2D MotionVectorSampler;
layout(std140) uniform TemporalColorConfig { vec4 MaskConfig; vec4 Reactivity; };
layout(location = 0) in vec2 texCoord;
layout(location = 0) out vec4 fragColor;
void main() {
    vec2 uv = (floor(texCoord * MaskConfig.xy) + 0.5) / MaskConfig.xy;
    vec2 motion = texture(MotionVectorSampler, uv).rg;
    fragColor = vec4(temporalColorRejection(CurrentSampler, PreviousSampler, uv,
            MaskConfig.xy, motion, MaskConfig.zw,Reactivity), 0.0, 0.0, 1.0);
}
