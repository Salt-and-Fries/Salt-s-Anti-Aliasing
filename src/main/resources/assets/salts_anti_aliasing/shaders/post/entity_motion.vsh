#version 450
layout(location=0) in vec3 Position;
layout(location=1) in vec3 PreviousPosition;
layout(std140) uniform EntityMotionConfig {
    mat4 CurrentViewProjection;
    mat4 PreviousViewProjection;
    vec4 JitterAndSize;
    vec4 PreviousCameraToCurrent;
};
layout(location=0) out vec4 currentClip;
layout(location=1) out vec4 previousClip;
void main() {
    currentClip=CurrentViewProjection*vec4(Position,1.0);
    previousClip=PreviousViewProjection*vec4(PreviousPosition+PreviousCameraToCurrent.xyz,1.0);
    gl_Position=currentClip;
    gl_Position.xy+=JitterAndSize.xy*gl_Position.w;
}
