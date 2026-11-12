package org.betterLostItems.salts_anti_aliasing.client.config;

/** Requested Streamline render preset. NVIDIA App overrides can take precedence. */
public enum DlssModelPreset {
    AUTO(0), K(11), M(13), L(12);

    private final int nativeId;

    DlssModelPreset(int nativeId) { this.nativeId = nativeId; }

    public int nativeId() { return nativeId; }
}
