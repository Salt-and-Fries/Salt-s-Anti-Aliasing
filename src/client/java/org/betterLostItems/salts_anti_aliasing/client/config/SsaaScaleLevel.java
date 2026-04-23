package org.betterLostItems.salts_anti_aliasing.client.config;

public enum SsaaScaleLevel {
    X125(1.25f),
    X150(1.50f),
    X175(1.75f),
    X200(2.00f);

    private final float scaleFactor;

    SsaaScaleLevel(float scaleFactor) {
        this.scaleFactor = scaleFactor;
    }

    public float scaleFactor() {
        return scaleFactor;
    }

    public String label() {
        return Math.round(scaleFactor * 100.0f) + "%";
    }

    public static SsaaScaleLevel defaultLevel() {
        return X125;
    }

    public static SsaaScaleLevel clamp(SsaaScaleLevel level) {
        return level == null ? defaultLevel() : level;
    }
}
