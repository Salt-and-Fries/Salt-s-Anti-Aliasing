#version 450
uniform sampler2D SceneDepthSampler;
uniform sampler2D ParticleAtlas;
layout(std140) uniform ParticleMotionConfig {
    mat4 CurrentViewProjection;
    mat4 PreviousViewProjection;
    vec4 JitterAndSize;
    vec4 PreviousCameraToCurrent;
};
layout(location=0) in vec4 currentClip;
layout(location=1) in vec4 previousClip;
layout(location=2) in vec2 texCoord;
layout(location=3) in float alpha;
layout(location=4) flat in float threshold;
layout(location=0) out vec4 fragColor;
void main() {
    // Holes retain the underlying world vector. Translucent layers contribute only when dominant.
    if(texture(ParticleAtlas,texCoord).a*alpha<threshold)discard;
    float opaqueDepth=texture(SceneDepthSampler,gl_FragCoord.xy/JitterAndSize.zw).r;
    if(gl_FragCoord.z+max(0.0000005,opaqueDepth*0.0001)<opaqueDepth
            ||previousClip.w<=0.00001||currentClip.w<=0.00001)discard;
    vec2 motion=(previousClip.xy/previousClip.w-currentClip.xy/currentClip.w)*0.5*JitterAndSize.zw;
    fragColor=vec4(clamp(motion,-JitterAndSize.zw,JitterAndSize.zw),0.0,1.0);
}
