#version 330

uniform sampler2D InputSampler;

in vec2 texCoord;

out vec4 fragColor;

vec3 srgbToLinear(vec3 color) {
    vec3 positive = max(color, vec3(0.0));
    vec3 low = positive / 12.92;
    vec3 high = pow((positive + 0.055) / 1.055, vec3(2.4));
    return mix(high, low, lessThanEqual(positive, vec3(0.04045)));
}

void main() {
    vec4 sampled = texture(InputSampler, texCoord);
    fragColor = vec4(srgbToLinear(sampled.rgb), sampled.a);
}
