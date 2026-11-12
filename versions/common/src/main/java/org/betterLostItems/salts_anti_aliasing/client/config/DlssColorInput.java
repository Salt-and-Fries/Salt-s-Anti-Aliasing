package org.betterLostItems.salts_anti_aliasing.client.config;

/** Source encoding, independent of whether the display itself supports HDR. */
public enum DlssColorInput {
    AUTO, PERCEPTUAL_SDR, LINEAR_HDR;
    public boolean linear(boolean floatingPointTarget) {
        return this == LINEAR_HDR || (this == AUTO && floatingPointTarget);
    }
}
