package org.betterLostItems.salts_anti_aliasing.client.config;

public enum QualityPreset {
    LOW("Low"),
    MEDIUM("Medium"),
    HIGH("High"),
    ULTRA("Ultra");

    private final String displayName;

    QualityPreset(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
