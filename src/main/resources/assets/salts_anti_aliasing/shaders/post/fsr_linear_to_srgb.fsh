#version 330

uniform sampler2D InputSampler;

in vec2 texCoord;

out vec4 fragColor;

vec3 linearToSrgb(vec3 color) {
    vec3 positive = max(color, vec3(0.0));
    vec3 low = positive * 12.92;
    vec3 high = 1.055 * pow(positive, vec3(1.0 / 2.4)) - 0.055;
    return mix(high, low, lessThanEqual(positive, vec3(0.0031308)));
}

void main() {
    vec4 sampled = texture(InputSampler, texCoord);
    fragColor = vec4(linearToSrgb(sampled.rgb), sampled.a);
}
