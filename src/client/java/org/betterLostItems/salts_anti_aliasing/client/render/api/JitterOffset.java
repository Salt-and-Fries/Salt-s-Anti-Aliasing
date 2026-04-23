package org.betterLostItems.salts_anti_aliasing.client.render.api;

public record JitterOffset(float x, float y) {
    public static JitterOffset none() {
        return new JitterOffset(0.0f, 0.0f);
    }
}
