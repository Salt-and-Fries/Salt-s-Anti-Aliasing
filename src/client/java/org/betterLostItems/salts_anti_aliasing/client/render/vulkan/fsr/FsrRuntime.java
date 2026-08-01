package org.betterLostItems.salts_anti_aliasing.client.render.vulkan.fsr;

import org.betterLostItems.salts_anti_aliasing.SaltsAntiAliasing;
import org.betterLostItems.salts_anti_aliasing.client.config.AntiAliasingConfig;
import org.betterLostItems.salts_anti_aliasing.client.config.FsrQualityPreset;
import org.betterLostItems.salts_anti_aliasing.client.render.vulkan.VulkanNativeDeviceInfo;
import org.betterLostItems.salts_anti_aliasing.client.render.vulkan.VulkanNativeDeviceRegistry;
import org.lwjgl.vulkan.KHRSwapchain;
import org.lwjgl.vulkan.VkAllocationCallbacks;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkPresentInfoKHR;
import org.lwjgl.vulkan.VkQueue;
import org.lwjgl.vulkan.VkSwapchainCreateInfoKHR;

import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.util.Objects;

/**
 * Process-wide AMD FSR state machine.
 */
public final class FsrRuntime {
    private static final FsrRuntime INSTANCE = new FsrRuntime();

    private final FsrNativeBridge bridge = new FsrNativeBridge();
    private FsrNativeConfiguration configuration = FsrNativeConfiguration.fromEnvironment();
    private FsrRuntimeStatus status = configuration.validate();
    private boolean frameGenerationSwapchainRequested;
    private boolean frameGenerationSwapchainStatusLogged;
    private boolean frameGenerationSwapchainRejected;
    private boolean frameGenerationSwapchainAvailabilityChanged;
    private boolean frameGenerationSurfaceReconfigurationRequested;
    private VulkanNativeDeviceInfo currentDeviceInfo;
    private long recoveryGeneration;
    private final FrameGenerationPresentTracker frameGenerationPresentTracker =
            new FrameGenerationPresentTracker();

    private FsrRuntime() {
    }

    public static FsrRuntime instance() {
        return INSTANCE;
    }

    public synchronized void configure(AntiAliasingConfig config) {
        FsrNativeConfiguration previousConfiguration = configuration;
        configuration = FsrNativeConfiguration.fromConfig(config);
        boolean previousRequest = frameGenerationSwapchainRequested;
        frameGenerationSwapchainRequested = config.mode.usesFsrFrameGeneration();
        boolean requestChanged = previousRequest != frameGenerationSwapchainRequested;
        boolean nativeConfigurationChanged = !configuration.equals(previousConfiguration);
        if (previousRequest && !frameGenerationSwapchainRequested) {
            bridge.disableFrameGeneration();
        }
        if (requestChanged || nativeConfigurationChanged) {
            frameGenerationSwapchainStatusLogged = false;
            frameGenerationSwapchainRejected = false;
            frameGenerationSwapchainAvailabilityChanged = false;
            resetFrameGenerationPresentTracking();
            recoveryGeneration++;
        }
        refresh();
    }

    public synchronized void preInitializeFromEnvironment() {
        if (configuration.hasAnySetting()) {
            refresh();
        }
    }

    public synchronized void onVulkanDeviceReady(VulkanNativeDeviceInfo deviceInfo) {
        if (!Objects.equals(deviceInfo, currentDeviceInfo)) {
            currentDeviceInfo = deviceInfo;
            frameGenerationSwapchainRejected = false;
            frameGenerationSwapchainStatusLogged = false;
            frameGenerationSwapchainAvailabilityChanged = false;
            resetFrameGenerationPresentTracking();
            recoveryGeneration++;
        }
        refresh(deviceInfo);
    }

    public synchronized FsrRuntimeStatus status() {
        if (frameGenerationSwapchainRejected && status.frameGenerationReady()) {
            return FsrRuntimeStatus.FRAME_GENERATION_SWAPCHAIN_UNAVAILABLE;
        }
        return status;
    }

    public synchronized boolean isUpscalingReady() {
        return status().upscalingReady();
    }

    public synchronized boolean isFrameGenerationReady() {
        return status().frameGenerationReady();
    }

    public synchronized boolean isFrameGenerationSwapchainActive() {
        return bridge.isFrameGenerationSwapchainActive();
    }

    /**
     * Reports whether the current Vulkan swapchain is the FidelityFX proxy. This remains true
     * while frame-generation evaluation is temporarily disabled, because the proxy images still
     * require the SDK's shader-read presentation layout.
     */
    public synchronized boolean isFrameGenerationSwapchainOwned() {
        return bridge.isFrameGenerationSwapchainOwned();
    }

    public synchronized boolean isFrameGenerationSwapchainRequested() {
        return frameGenerationSwapchainRequested;
    }

    public synchronized boolean consumeFrameGenerationSwapchainAvailabilityChanged() {
        boolean changed = frameGenerationSwapchainAvailabilityChanged;
        frameGenerationSwapchainAvailabilityChanged = false;
        return changed;
    }

    public synchronized boolean consumeFrameGenerationSurfaceReconfigurationRequested() {
        boolean requested = frameGenerationSurfaceReconfigurationRequested;
        frameGenerationSurfaceReconfigurationRequested = false;
        return requested;
    }

    public synchronized FsrOptimalSettings queryOptimalSettings(
            FsrQualityPreset preset,
            int outputWidth,
            int outputHeight
    ) {
        return bridge.queryOptimalSettings(preset, outputWidth, outputHeight);
    }

    public synchronized int evaluate(FsrEvaluateParameters parameters) {
        return bridge.evaluate(parameters);
    }

    /**
     * Disables and drains native frame generation after a failed evaluation. Callers must submit
     * the command buffer containing that evaluation before invoking this method.
     */
    public synchronized void onFrameGenerationEvaluationFailure(int result) {
        bridge.disableFrameGeneration();
        boolean newlyRejected = !frameGenerationSwapchainRejected;
        frameGenerationSwapchainRejected = true;
        frameGenerationSwapchainStatusLogged = true;
        if (newlyRejected) {
            frameGenerationSwapchainAvailabilityChanged = true;
            SaltsAntiAliasing.LOGGER.warn(
                    "AMD FSR3 frame generation failed during evaluation with result {}; disabling it until the mode or Vulkan device changes",
                    result
            );
        }
    }

    /**
     * Changes whenever a new configuration, device, or successfully recreated proxy swapchain
     * makes retrying a previously failed FSR scene controller safe.
     */
    public synchronized long recoveryGeneration() {
        return recoveryGeneration;
    }

    public synchronized int createFrameGenerationSwapchain(
            VkDevice device,
            VkSwapchainCreateInfoKHR createInfo,
            VkAllocationCallbacks allocator,
            LongBuffer outSwapchain
    ) {
        if (!frameGenerationSwapchainRequested || !status.frameGenerationReady() || frameGenerationSwapchainRejected) {
            return KHRSwapchain.vkCreateSwapchainKHR(device, createInfo, allocator, outSwapchain);
        }

        int result = bridge.createFrameGenerationSwapchain(device, createInfo, allocator, outSwapchain);
        if (result == FsrNativeBridge.SWAPCHAIN_UNHANDLED) {
            frameGenerationSwapchainRejected = true;
            frameGenerationSwapchainAvailabilityChanged = true;
            if (!frameGenerationSwapchainStatusLogged) {
                SaltsAntiAliasing.LOGGER.warn(
                        "AMD FSR3 frame-generation swapchain is unavailable; using real FSR3 upscaling without frame generation"
                );
                frameGenerationSwapchainStatusLogged = true;
            }
            return KHRSwapchain.vkCreateSwapchainKHR(device, createInfo, allocator, outSwapchain);
        }
        if (result == 0) {
            frameGenerationSwapchainRejected = false;
            frameGenerationSwapchainAvailabilityChanged = false;
            resetFrameGenerationPresentTracking();
            recoveryGeneration++;
            if (!frameGenerationSwapchainStatusLogged) {
                SaltsAntiAliasing.LOGGER.info("AMD FSR3 frame-generation swapchain is active");
                frameGenerationSwapchainStatusLogged = true;
            }
            refresh();
        }
        return result;
    }

    public synchronized void destroyFrameGenerationSwapchain(
            VkDevice device,
            long swapchain,
            VkAllocationCallbacks allocator
    ) {
        if (!bridge.destroyFrameGenerationSwapchain(device, swapchain, allocator)) {
            KHRSwapchain.vkDestroySwapchainKHR(device, swapchain, allocator);
        } else {
            resetFrameGenerationPresentTracking();
            refresh();
        }
    }

    public synchronized int getFrameGenerationSwapchainImages(
            VkDevice device,
            long swapchain,
            IntBuffer imageCount,
            LongBuffer images
    ) {
        int result = bridge.getFrameGenerationSwapchainImages(device, swapchain, imageCount, images);
        return result == FsrNativeBridge.SWAPCHAIN_UNHANDLED
                ? KHRSwapchain.vkGetSwapchainImagesKHR(device, swapchain, imageCount, images)
                : result;
    }

    public synchronized int acquireNextFrameGenerationImage(
            VkDevice device,
            long swapchain,
            long timeout,
            long semaphore,
            long fence,
            IntBuffer imageIndex
    ) {
        int result = bridge.acquireNextFrameGenerationImage(device, swapchain, timeout, semaphore, fence, imageIndex);
        return result == FsrNativeBridge.SWAPCHAIN_UNHANDLED
                ? KHRSwapchain.vkAcquireNextImageKHR(device, swapchain, timeout, semaphore, fence, imageIndex)
                : result;
    }

    public synchronized int presentFrameGenerationSwapchain(VkQueue queue, VkPresentInfoKHR presentInfo) {
        int result = bridge.presentFrameGenerationSwapchain(queue, presentInfo);
        if (result == FsrNativeBridge.SWAPCHAIN_UNHANDLED) {
            return KHRSwapchain.vkQueuePresentKHR(queue, presentInfo);
        }

        if (result == 0 || result == KHRSwapchain.VK_SUBOPTIMAL_KHR) {
            long sdkPresentCount = bridge.frameGenerationPresentCount();
            if (frameGenerationPresentTracker.recordSuccessfulGamePresent(sdkPresentCount)) {
                SaltsAntiAliasing.LOGGER.info(
                        "AMD FSR3 frame generation confirmed: {} display presents from {} rendered game frames",
                        frameGenerationPresentTracker.sdkPresentCount(),
                        frameGenerationPresentTracker.gamePresentCount()
                );
            }
        }
        return result;
    }

    public synchronized long frameGenerationPresentCount() {
        return frameGenerationPresentTracker.sdkPresentCount();
    }

    public synchronized boolean isFrameGenerationOutputConfirmed() {
        return frameGenerationPresentTracker.outputConfirmed();
    }

    public synchronized void shutdown() {
        bridge.shutdown();
        currentDeviceInfo = null;
        frameGenerationSwapchainRejected = false;
        frameGenerationSwapchainStatusLogged = false;
        frameGenerationSwapchainAvailabilityChanged = false;
        frameGenerationSurfaceReconfigurationRequested = false;
        resetFrameGenerationPresentTracking();
        recoveryGeneration++;
        status = configuration.validate();
    }

    private void refresh() {
        refresh(VulkanNativeDeviceRegistry.latest());
    }

    private void refresh(VulkanNativeDeviceInfo deviceInfo) {
        FsrRuntimeStatus previousStatus = status;
        status = bridge.configure(configuration, deviceInfo);
        if (bridge.consumeSurfaceReconfigurationRequested()) {
            frameGenerationSurfaceReconfigurationRequested = true;
        }
        if (status != previousStatus) {
            SaltsAntiAliasing.LOGGER.info("AMD FSR runtime status: {}", status.message());
        }
    }

    private void resetFrameGenerationPresentTracking() {
        frameGenerationPresentTracker.reset();
    }
}
