// Both native upscalers consume unjittered motion from the current pixel to its previous position.
vec2 temporalMotionPixels(float depth, vec2 uv, mat4 currentClipToWorld,
        mat4 currentViewProjection, mat4 previousViewProjection,
        vec3 cameraTranslationToPrevious, vec2 renderSize) {
    // Vulkan reverse-Z depth is already in the zero-to-one clip range.
    vec4 world = currentClipToWorld * vec4(uv * 2.0 - 1.0, depth, 1.0);
    if (abs(world.w) <= 0.00001) {
        return vec2(0.0);
    }
    world /= world.w;

    vec4 previousWorld = world;
    if (depth <= 0.0000001) {
        // Clear-depth sky has rotational motion but no camera-translation parallax.
        previousWorld.xyz += cameraTranslationToPrevious;
    }
    vec4 currentClip = currentViewProjection * world;
    vec4 previousClip = previousViewProjection * previousWorld;
    if (currentClip.w <= 0.00001 || previousClip.w <= 0.00001) {
        return vec2(0.0);
    }
    vec2 currentUv = currentClip.xy / currentClip.w * 0.5 + 0.5;
    vec2 previousUv = previousClip.xy / previousClip.w * 0.5 + 0.5;
    return (previousUv - currentUv) * renderSize;
}
