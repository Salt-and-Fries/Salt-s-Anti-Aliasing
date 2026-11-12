#version 450
uniform sampler2D SceneDepthSampler;
layout(std140) uniform EntityMotionConfig {
    mat4 CurrentViewProjection;
    mat4 PreviousViewProjection;
    vec4 JitterAndSize;
    vec4 PreviousCameraToCurrent;
};
layout(location=0) in vec4 currentClip;
layout(location=1) in vec4 previousClip;
layout(location=0) out vec4 fragColor;
void main() {
    vec2 uv=gl_FragCoord.xy/JitterAndSize.zw;
    float depth=texture(SceneDepthSampler,uv).r;
    // Alpha holes and occluded faces retain the camera field. Never overwrite their depth.
    if(abs(gl_FragCoord.z-depth)>max(0.0000005,depth*0.0001)
            || previousClip.w<=0.00001 || currentClip.w<=0.00001)discard;
    vec2 motion=(previousClip.xy/previousClip.w-currentClip.xy/currentClip.w)*0.5*JitterAndSize.zw;
    fragColor=vec4(clamp(motion,-JitterAndSize.zw,JitterAndSize.zw),0.0,1.0);
}
