#version 450
uniform sampler2D InSampler;
layout(std140) uniform TemporalDebugConfig { float MotionView; vec2 MotionScale; };
layout(location=0) in vec2 texCoord;
layout(location=0) out vec4 fragColor;
void main() {
    vec4 value=texture(InSampler,texCoord);
    fragColor=vec4(MotionView>0.5?clamp(vec3(value.rg*MotionScale*0.02+0.5,0.5),0.0,1.0):vec3(value.r),1.0);
}
