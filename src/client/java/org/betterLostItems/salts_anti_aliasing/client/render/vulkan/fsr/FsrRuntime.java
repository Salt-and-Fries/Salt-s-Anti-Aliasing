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

    private FsrRuntime() {
    }

    public static FsrRuntime instance() {
        return INSTANCE;
    }

    public synchronized void configure(AntiAliasingConfig config) {
        configuration = FsrNativeConfiguration.fromConfig(config);
        boolean previousRequest = frameGenerationSwapchainRequested;
        frameGenerationSwapchainRequested = config.mode.usesFsrFrameGeneration();
        if (previousRequest != frameGenerationSwapchainRequested) {
            frameGenerationSwapchainStatusLogged = false;
        }
        refresh();
    }

    public synchronized void preInitializeFromEnvironment() {
        if (configuration.hasAnySetting()) {
            refresh();
        }
    }

    public synchronized void onVulkanDeviceReady(VulkanNativeDeviceInfo deviceInfo) {
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

    public synchronized boolean isFrameGenerationSwapchainRequested() {
        return frameGenerationSwapchainRequested;
    }

    public synchronized boolean consumeFrameGenerationSwapchainAvailabilityChanged() {
        boolean changed = frameGenerationSwapchainAvailabilityChanged;
        frameGenerationSwapchainAvailabilityChanged = false;
        return changed;
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
        return result == FsrNativeBridge.SWAPCHAIN_UNHANDLED
                ? KHRSwapchain.vkQueuePresentKHR(queue, presentInfo)
                : result;
    }

    public synchronized void shutdown() {
        bridge.shutdown();
    }

    private void refresh() {
        refresh(VulkanNativeDeviceRegistry.latest());
    }

    private void refresh(VulkanNativeDeviceInfo deviceInfo) {
        FsrRuntimeStatus previousStatus = status;
        status = bridge.configure(configuration, deviceInfo);
        if (status != previousStatus) {
            SaltsAntiAliasing.LOGGER.info("AMD FSR runtime status: {}", status.message());
        }
    }
}
