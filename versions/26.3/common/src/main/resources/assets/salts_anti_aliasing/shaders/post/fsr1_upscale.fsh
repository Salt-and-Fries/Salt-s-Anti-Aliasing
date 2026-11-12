#version 450
// AMD FidelityFX FSR 1 reference EASU, FP32. Sharpening remains a separate user-controlled pass.
uniform sampler2D InSampler;
layout(std140) uniform SamplerInfo { vec2 OutSize; vec2 InSize; };
layout(location = 0) in vec2 texCoord;
layout(location = 0) out vec4 fragColor;
#define A_GPU 1
#define A_GLSL 1
#include <salts_anti_aliasing:ffx_a.glsl>
#define FSR_EASU_F 1
AF4 FsrEasuRF(AF2 p) { return textureGather(InSampler, p, 0); }
AF4 FsrEasuGF(AF2 p) { return textureGather(InSampler, p, 1); }
AF4 FsrEasuBF(AF2 p) { return textureGather(InSampler, p, 2); }
#include <salts_anti_aliasing:ffx_fsr1.glsl>
void main() {
    AU4 con0, con1, con2, con3;
    FsrEasuCon(con0, con1, con2, con3, InSize.x, InSize.y, InSize.x, InSize.y, OutSize.x, OutSize.y);
    AF3 color;
    FsrEasuF(color, AU2(floor(texCoord * OutSize)), con0, con1, con2, con3);
    fragColor = vec4(color, 1.0);
}
