#version 450
uniform sampler2D InputSampler;
layout(location = 0) in vec2 texCoord;
// Vulkan depth blits require identical formats. Shader writes also handle D32 -> D24S8.
void main() {
    gl_FragDepth = texture(InputSampler, texCoord).r;
}
