package org.betterLostItems.salts_anti_aliasing.client.render.vulkan.fsr;

/**
 * Runtime availability for the optional AMD FSR stack.
 */
public enum FsrRuntimeStatus {
    NOT_CONFIGURED("AMD FSR is not configured", false, false),
    BRIDGE_PATH_MISSING("AMD FSR JNI bridge path is missing", false, false),
    RUNTIME_PATH_MISSING("AMD FidelityFX runtime path is missing", false, false),
    BRIDGE_LOAD_FAILED("AMD FSR JNI bridge failed to load", false, false),
    VULKAN_DEVICE_MISSING("Vulkan device information is not available yet", false, false),
    INITIALIZATION_FAILED("AMD FidelityFX initialization failed", false, false),
    UNSUPPORTED("AMD FSR is not supported by this GPU, driver, or FidelityFX runtime", false, false),
    UPSCALING_READY("AMD FSR upscaling is ready", true, false),
    FRAME_GENERATION_SWAPCHAIN_UNAVAILABLE(
            "AMD FSR upscaling is ready; FSR3 frame-generation swapchain is unavailable",
            true,
            false
    ),
    FRAME_GENERATION_READY("AMD FSR upscaling and frame generation are ready", true, true);

    private final String message;
    private final boolean upscalingReady;
    private final boolean frameGenerationReady;

    FsrRuntimeStatus(String message, boolean upscalingReady, boolean frameGenerationReady) {
        this.message = message;
        this.upscalingReady = upscalingReady;
        this.frameGenerationReady = frameGenerationReady;
    }

    public boolean upscalingReady() {
        return upscalingReady;
    }

    public boolean frameGenerationReady() {
        return frameGenerationReady;
    }

    public String message() {
        return message;
    }
}
