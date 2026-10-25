#version 450
uniform sampler2D WorldSampler;
layout(location = 0) out vec4 fragColor;
void main() {
    fragColor = texelFetch(WorldSampler, ivec2(gl_FragCoord.xy), 0);
}
