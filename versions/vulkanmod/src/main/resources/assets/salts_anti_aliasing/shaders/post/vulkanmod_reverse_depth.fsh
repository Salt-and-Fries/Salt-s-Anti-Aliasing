#version 450
uniform sampler2D InputSampler;
layout(std140) uniform DepthNormalization { float ForwardScale; };
layout(location = 0) in vec2 texCoord;
// Older Minecraft/VulkanMod uses forward Z. Normalize once so the modern temporal
// and vendor algorithms retain exactly the same reverse-Z depth convention.
void main() {
    gl_FragDepth = clamp(1.0 - texture(InputSampler, texCoord).r * ForwardScale, 0.0, 1.0);
}
