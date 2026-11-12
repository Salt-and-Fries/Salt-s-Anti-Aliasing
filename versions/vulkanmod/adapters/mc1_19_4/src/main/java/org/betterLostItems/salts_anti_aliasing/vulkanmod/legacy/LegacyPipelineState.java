package org.betterLostItems.salts_anti_aliasing.vulkanmod.legacy;
public interface LegacyPipelineState {
    Signature salts$signature();
    record Signature(int color, int depth, int samples, boolean coverage, boolean hand) {}
}
