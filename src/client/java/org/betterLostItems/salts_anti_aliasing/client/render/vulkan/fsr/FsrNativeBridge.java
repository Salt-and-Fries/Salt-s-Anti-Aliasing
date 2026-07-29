package org.betterLostItems.salts_anti_aliasing.client.render.vulkan.fsr;

import org.betterLostItems.salts_anti_aliasing.SaltsAntiAliasing;
import org.betterLostItems.salts_anti_aliasing.client.config.FsrQualityPreset;
import org.betterLostItems.salts_anti_aliasing.client.render.vulkan.VulkanNativeDeviceInfo;
import org.lwjgl.vulkan.VkAllocationCallbacks;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkPresentInfoKHR;
import org.lwjgl.vulkan.VkQueue;
import org.lwjgl.vulkan.VkSwapchainCreateInfoKHR;

import java.nio.IntBuffer;
import java.nio.LongBuffer;

import static org.lwjgl.system.MemoryUtil.memAddress;
import static org.lwjgl.system.MemoryUtil.memAddressSafe;

/**
 * Thin Java owner for the optional native AMD FSR bridge.
 */
final class FsrNativeBridge {
    static final int SWAPCHAIN_UNHANDLED = Integer.MIN_VALUE;

    private boolean bridgeLoaded;
    private boolean initialized;
    private String lastBridgePath = "";

    FsrRuntimeStatus configure(FsrNativeConfiguration configuration, VulkanNativeDeviceInfo deviceInfo) {
        FsrRuntimeStatus validationStatus = configuration.validate();
        if (!validationStatus.upscalingReady()) {
            return validationStatus;
        }

        if (!loadBridge(configuration.bridgePath())) {
            return FsrRuntimeStatus.BRIDGE_LOAD_FAILED;
        }

        if (deviceInfo == null || !deviceInfo.complete()) {
            return FsrRuntimeStatus.VULKAN_DEVICE_MISSING;
        }

        if (!initialized) {
            int result = initializeNative(
                    configuration.runtimePath(),
                    configuration.logPath(),
                    deviceInfo.instance(),
                    deviceInfo.physicalDevice(),
                    deviceInfo.device(),
                    deviceInfo.graphicsQueue(),
                    deviceInfo.graphicsQueueFamily(),
                    deviceInfo.computeQueue(),
                    deviceInfo.computeQueueFamily(),
                    deviceInfo.transferQueue(),
                    deviceInfo.transferQueueFamily()
            );
            if (result != 0) {
                SaltsAntiAliasing.LOGGER.warn("AMD FSR native initialization failed with result {}", result);
                return FsrRuntimeStatus.INITIALIZATION_FAILED;
            }
            initialized = true;
        }

        if (!isUpscalingSupportedNative()) {
            return FsrRuntimeStatus.UNSUPPORTED;
        }

        return isFrameGenerationSupportedNative()
                ? FsrRuntimeStatus.FRAME_GENERATION_READY
                : FsrRuntimeStatus.UPSCALING_READY;
    }

    boolean isFrameGenerationSwapchainActive() {
        return initialized && isFrameGenerationSwapchainActiveNative();
    }

    FsrOptimalSettings queryOptimalSettings(FsrQualityPreset preset, int outputWidth, int outputHeight) {
        if (!initialized || outputWidth <= 0 || outputHeight <= 0) {
            return FsrOptimalSettings.fallback(preset, outputWidth, outputHeight);
        }

        int[] values = new int[2];
        int result = queryOptimalSettingsNative(preset.ordinal(), outputWidth, outputHeight, values);
        if (result != 0) {
            return FsrOptimalSettings.fallback(preset, outputWidth, outputHeight);
        }

        return new FsrOptimalSettings(
                Math.max(1, values[0]),
                Math.max(1, values[1]),
                Math.max(1, outputWidth),
                Math.max(1, outputHeight)
        );
    }

    int evaluate(FsrEvaluateParameters parameters) {
        if (!initialized) {
            return -1;
        }

        return evaluateNative(
                parameters.commandBuffer(),
                parameters.fsrVersion(),
                parameters.frameGeneration(),
                parameters.inputColorImage(),
                parameters.inputColorView(),
                parameters.outputColorImage(),
                parameters.outputColorView(),
                parameters.depthImage(),
                parameters.depthView(),
                parameters.motionVectorImage(),
                parameters.motionVectorView(),
                parameters.opaqueColorImage(),
                parameters.opaqueColorView(),
                parameters.reactiveMaskImage(),
                parameters.reactiveMaskView(),
                parameters.transparencyMaskImage(),
                parameters.transparencyMaskView(),
                parameters.hudlessColorImage(),
                parameters.hudlessColorView(),
                parameters.renderWidth(),
                parameters.renderHeight(),
                parameters.outputWidth(),
                parameters.outputHeight(),
                parameters.jitterX(),
                parameters.jitterY(),
                parameters.resetHistory(),
                parameters.frameIndex(),
                parameters.motionVectorScaleX(),
                parameters.motionVectorScaleY(),
                parameters.sharpness(),
                parameters.frameTimeDeltaMs(),
                parameters.cameraNear(),
                parameters.cameraFar(),
                parameters.cameraFovY(),
                parameters.viewSpaceToMeters(),
                parameters.cameraPositionX(),
                parameters.cameraPositionY(),
                parameters.cameraPositionZ(),
                parameters.cameraUpX(),
                parameters.cameraUpY(),
                parameters.cameraUpZ(),
                parameters.cameraRightX(),
                parameters.cameraRightY(),
                parameters.cameraRightZ(),
                parameters.cameraForwardX(),
                parameters.cameraForwardY(),
                parameters.cameraForwardZ()
        );
    }

    int createFrameGenerationSwapchain(
            VkDevice device,
            VkSwapchainCreateInfoKHR createInfo,
            VkAllocationCallbacks allocator,
            LongBuffer outSwapchain
    ) {
        if (!initialized || device == null || createInfo == null || outSwapchain == null) {
            return SWAPCHAIN_UNHANDLED;
        }
        return createFrameGenerationSwapchainNative(
                device.address(),
                createInfo.address(),
                allocator == null ? 0L : allocator.address(),
                memAddress(outSwapchain)
        );
    }

    boolean destroyFrameGenerationSwapchain(VkDevice device, long swapchain, VkAllocationCallbacks allocator) {
        return initialized
                && device != null
                && swapchain != 0L
                && destroyFrameGenerationSwapchainNative(
                        device.address(),
                        swapchain,
                        allocator == null ? 0L : allocator.address()
                );
    }

    int getFrameGenerationSwapchainImages(VkDevice device, long swapchain, IntBuffer imageCount, LongBuffer images) {
        if (!initialized || device == null || swapchain == 0L || imageCount == null) {
            return SWAPCHAIN_UNHANDLED;
        }
        return getFrameGenerationSwapchainImagesNative(
                device.address(),
                swapchain,
                memAddress(imageCount),
                memAddressSafe(images)
        );
    }

    int acquireNextFrameGenerationImage(
            VkDevice device,
            long swapchain,
            long timeout,
            long semaphore,
            long fence,
            IntBuffer imageIndex
    ) {
        if (!initialized || device == null || swapchain == 0L || imageIndex == null) {
            return SWAPCHAIN_UNHANDLED;
        }
        return acquireNextFrameGenerationImageNative(
                device.address(),
                swapchain,
                timeout,
                semaphore,
                fence,
                memAddress(imageIndex)
        );
    }

    int presentFrameGenerationSwapchain(VkQueue queue, VkPresentInfoKHR presentInfo) {
        if (!initialized || queue == null || presentInfo == null) {
            return SWAPCHAIN_UNHANDLED;
        }
        return presentFrameGenerationSwapchainNative(queue.address(), presentInfo.address());
    }

    void shutdown() {
        if (bridgeLoaded && initialized) {
            shutdownNative();
            initialized = false;
        }
    }

    private boolean loadBridge(String bridgePath) {
        if (bridgeLoaded && bridgePath.equals(lastBridgePath)) {
            return true;
        }

        try {
            System.load(bridgePath);
            bridgeLoaded = true;
            lastBridgePath = bridgePath;
            return true;
        } catch (UnsatisfiedLinkError | SecurityException exception) {
            SaltsAntiAliasing.LOGGER.warn("Unable to load AMD FSR JNI bridge from {}", bridgePath, exception);
            bridgeLoaded = false;
            initialized = false;
            return false;
        }
    }

    private static native int initializeNative(
            String runtimePath,
            String logPath,
            long vkInstance,
            long vkPhysicalDevice,
            long vkDevice,
            long vkGraphicsQueue,
            int graphicsQueueFamily,
            long vkComputeQueue,
            int computeQueueFamily,
            long vkTransferQueue,
            int transferQueueFamily
    );

    private static native boolean isUpscalingSupportedNative();

    private static native boolean isFrameGenerationSupportedNative();

    private static native boolean isFrameGenerationSwapchainActiveNative();

    private static native int queryOptimalSettingsNative(
            int qualityPreset,
            int outputWidth,
            int outputHeight,
            int[] outRenderSize
    );

    private static native int evaluateNative(
            long commandBuffer,
            int fsrVersion,
            boolean frameGeneration,
            long inputColorImage,
            long inputColorView,
            long outputColorImage,
            long outputColorView,
            long depthImage,
            long depthView,
            long motionVectorImage,
            long motionVectorView,
            long opaqueColorImage,
            long opaqueColorView,
            long reactiveMaskImage,
            long reactiveMaskView,
            long transparencyMaskImage,
            long transparencyMaskView,
            long hudlessColorImage,
            long hudlessColorView,
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
            float sharpness,
            float frameTimeDeltaMs,
            float cameraNear,
            float cameraFar,
            float cameraFovY,
            float viewSpaceToMeters,
            float cameraPositionX,
            float cameraPositionY,
            float cameraPositionZ,
            float cameraUpX,
            float cameraUpY,
            float cameraUpZ,
            float cameraRightX,
            float cameraRightY,
            float cameraRightZ,
            float cameraForwardX,
            float cameraForwardY,
            float cameraForwardZ
    );

    private static native int createFrameGenerationSwapchainNative(
            long vkDevice,
            long createInfoAddress,
            long allocatorAddress,
            long outSwapchainAddress
    );

    private static native boolean destroyFrameGenerationSwapchainNative(
            long vkDevice,
            long swapchain,
            long allocatorAddress
    );

    private static native int getFrameGenerationSwapchainImagesNative(
            long vkDevice,
            long swapchain,
            long imageCountAddress,
            long imagesAddress
    );

    private static native int acquireNextFrameGenerationImageNative(
            long vkDevice,
            long swapchain,
            long timeout,
            long semaphore,
            long fence,
            long imageIndexAddress
    );

    private static native int presentFrameGenerationSwapchainNative(long vkQueue, long presentInfoAddress);

    private static native void shutdownNative();
}
