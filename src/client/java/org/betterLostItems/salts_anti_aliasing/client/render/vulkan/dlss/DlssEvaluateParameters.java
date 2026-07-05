package org.betterLostItems.salts_anti_aliasing.client.render.vulkan.dlss;

/**
 * Native DLSS evaluate arguments for one rendered frame.
 */
public record DlssEvaluateParameters(
        long commandBuffer,
        long inputColorImage,
        long inputColorView,
        long outputColorImage,
        long outputColorView,
        long depthImage,
        long depthView,
        long motionVectorImage,
        long motionVectorView,
        int renderWidth,
        int renderHeight,
        int outputWidth,
        int outputHeight,
        float jitterX,
        float jitterY,
        boolean resetHistory,
        long frameIndex,
        float motionVectorScaleX,
        float motionVectorScaleY,
        float[] currentViewProjection,
        float[] previousViewProjection
) {
}
