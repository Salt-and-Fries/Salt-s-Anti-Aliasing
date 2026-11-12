package org.betterLostItems.salts_anti_aliasing.client.render.vulkan.xess;

/** JNI ABI 1, built against the pinned Intel XeSS Vulkan headers. */
final class XessNativeBridge {
    private XessNativeBridge() {}
    static native String load(String runtime);
    static native String[] instanceRequirements();
    static native String[] deviceRequirements(long instance, long physicalDevice);
    static native void create(long instance, long physicalDevice, long device);
    static native int[] settings(int outputWidth, int outputHeight, int quality);
    static native int execute(long commandBuffer, long[] imageAndViewHandles, int[] formats,
                              int width, int height, float jitterX, float jitterY, boolean reset);
    static native void destroy();
}
