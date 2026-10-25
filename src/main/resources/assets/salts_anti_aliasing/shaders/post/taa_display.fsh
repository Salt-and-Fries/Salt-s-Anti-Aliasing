#version 450
uniform sampler2D InSampler;
layout(std140) uniform TaaDisplayConfig { float DebugView; };
layout(location=0) in vec2 texCoord;
layout(location=0) out vec4 fragColor;
void main() {
    vec4 resolved=texture(InSampler,texCoord);
    bool confidenceView = DebugView == 2.0 || DebugView >= 5.0;
    fragColor=vec4(confidenceView?vec3(resolved.a):resolved.rgb,1.0);
}
