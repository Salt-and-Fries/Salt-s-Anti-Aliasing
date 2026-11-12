#version 450
uniform sampler2D FullSampler;
uniform sampler2D OpaqueSampler;
layout(location=0) in vec2 texCoord;
layout(location=0) out vec4 fullColor;
layout(location=1) out vec4 opaqueColor;
vec4 decode(vec4 value) {
    vec3 c=max(value.rgb,vec3(0.0));
    return vec4(mix(pow((c+0.055)/1.055,vec3(2.4)),c/12.92,
            lessThanEqual(c,vec3(0.04045))),value.a);
}
void main() {
    fullColor=decode(texture(FullSampler,texCoord));
    opaqueColor=decode(texture(OpaqueSampler,texCoord));
}
