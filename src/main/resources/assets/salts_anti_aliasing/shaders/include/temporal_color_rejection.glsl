// Reject color changes that camera motion cannot explain, including moving opaque objects.
// A current 3x3 envelope preserves subpixel edge accumulation instead of treating jitter as motion.
float temporalColorRejection(sampler2D currentSampler, sampler2D previousSampler,
                            vec2 uv, vec2 size, vec2 motionPixels, vec2 jitterDelta) {
    vec2 texel = 1.0 / max(size, vec2(1.0));
    vec2 previousUv = uv + (motionPixels + jitterDelta) * texel;
    if (any(lessThan(previousUv, texel * 0.5)) || any(greaterThan(previousUv, 1.0 - texel * 0.5))) return 0.9;
    vec3 minimumColor = vec3(1e10);
    vec3 maximumColor = vec3(-1e10);
    for (int y = -1; y <= 1; ++y) {
        for (int x = -1; x <= 1; ++x) {
            vec3 color = texture(currentSampler, clamp(uv + vec2(x, y) * texel, texel * 0.5, 1.0 - texel * 0.5)).rgb;
            minimumColor = min(minimumColor, color);
            maximumColor = max(maximumColor, color);
        }
    }
    vec3 previousColor = texture(previousSampler, previousUv).rgb;
    vec3 excess = abs(previousColor - clamp(previousColor, minimumColor - 0.005, maximumColor + 0.005));
    float scale = max(0.1, max(max(maximumColor.r, maximumColor.g), maximumColor.b));
    float change = max(max(excess.r, excess.g), excess.b) / scale;
    return 0.9 * smoothstep(0.06, 0.30, change);
}
