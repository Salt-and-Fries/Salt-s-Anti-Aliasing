#version 450
uniform sampler2D DepthSampler;
uniform sampler2D MotionSampler;
layout(location=0) in vec2 texCoord;
layout(location=0) out vec4 fragColor;
void main() {
    vec2 source=vec2(texCoord.x,1.0-texCoord.y);
    vec2 motion=texture(MotionSampler,source).rg;
    fragColor=vec4(motion.x,-motion.y,0.0,1.0);
    gl_FragDepth=texture(DepthSampler,source).r;
}
