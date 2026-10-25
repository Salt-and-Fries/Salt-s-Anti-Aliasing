package org.betterLostItems.salts_anti_aliasing.client.render.vulkan.fsr;

import org.betterLostItems.salts_anti_aliasing.SaltsAntiAliasing;
import org.betterLostItems.salts_anti_aliasing.client.config.AntiAliasingConfig;
import org.betterLostItems.salts_anti_aliasing.client.config.FsrQualityPreset;
import org.betterLostItems.salts_anti_aliasing.client.metrics.MetricsDiagnostics;
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
        MetricsDiagnostics.info(
                "fsr.runtime",
                "configuration_applied",
                "configurationChanged", nativeConfigurationChanged,
                "previousConfiguration", previousConfiguration,
                "configuration", configuration,
                "validationStatus", configuration.validate(),
                "frameGenerationRequested", frameGenerationSwapchainRequested,
                "frameGenerationRequestChanged", requestChanged,
                "recoveryGeneration", recoveryGeneration
        );
        refresh();
    }

    public synchronized void preInitializeFromEnvironment() {
        MetricsDiagnostics.info(
                "fsr.runtime",
                "environment_preinitialization_requested",
                "hasAnySetting", configuration.hasAnySetting(),
                "configuration", configuration,
                "validationStatus", configuration.validate()
        );
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
        MetricsDiagnostics.info(
                "fsr.runtime",
                "vulkan_device_ready",
                "deviceInfo", deviceInfo,
                "coreHandlesComplete", deviceInfo != null && deviceInfo.complete(),
                "frameGenerationQueuesComplete", deviceInfo != null && deviceInfo.frameGenerationQueuesComplete(),
                "recoveryGeneration", recoveryGeneration
        );
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
        FsrOptimalSettings settings = bridge.queryOptimalSettings(preset, outputWidth, outputHeight);
        MetricsDiagnostics.infoOnce(
                "fsr.optimal-settings." + preset + "." + outputWidth + "x" + outputHeight,
                "fsr.runtime",
                "optimal_settings_queried",
                "preset", preset,
                "outputWidth", outputWidth,
                "outputHeight", outputHeight,
                "settings", settings,
                "runtimeStatus", status()
        );
        return settings;
    }

    public synchronized int evaluate(FsrEvaluateParameters parameters) {
        int result = bridge.evaluate(parameters);
        if (result != 0) {
            MetricsDiagnostics.warnOnce(
                    "fsr.evaluate." + parameters.fsrVersion() + "." + parameters.frameGeneration() + "."
                            + result + "." + parameters.renderWidth() + "x" + parameters.renderHeight() + "."
                            + parameters.outputWidth() + "x" + parameters.outputHeight(),
                    "fsr.evaluate",
                    "evaluation_failed",
                    "result", result,
                    "parameters", parameters,
                    "runtimeStatus", status(),
                    "recoveryGeneration", recoveryGeneration
            );
        }
        return result;
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
            MetricsDiagnostics.warn(
                    "fsr.frame_generation",
                    "evaluation_disabled_after_failure",
                    "result", result,
                    "status", status,
                    "recoveryGeneration", recoveryGeneration
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
            MetricsDiagnostics.infoOnce(
                    "fsr.swapchain.passthrough." + frameGenerationSwapchainRequested + "."
                            + status + "." + frameGenerationSwapchainRejected,
                    "fsr.frame_generation",
                    "swapchain_creation_passthrough",
                    "frameGenerationRequested", frameGenerationSwapchainRequested,
                    "status", status,
                    "swapchainRejected", frameGenerationSwapchainRejected,
                    "minimumImageCount", createInfo.minImageCount(),
                    "imageFormat", createInfo.imageFormat(),
                    "imageColorSpace", createInfo.imageColorSpace(),
                    "imageExtent", createInfo.imageExtent().width() + "x" + createInfo.imageExtent().height(),
                    "imageUsage", createInfo.imageUsage(),
                    "presentMode", createInfo.presentMode()
            );
            return KHRSwapchain.vkCreateSwapchainKHR(device, createInfo, allocator, outSwapchain);
        }

        MetricsDiagnostics.info(
                "fsr.frame_generation",
                "swapchain_creation_attempted",
                "minimumImageCount", createInfo.minImageCount(),
                "imageFormat", createInfo.imageFormat(),
                "imageColorSpace", createInfo.imageColorSpace(),
                "imageExtent", createInfo.imageExtent().width() + "x" + createInfo.imageExtent().height(),
                "imageArrayLayers", createInfo.imageArrayLayers(),
                "imageUsage", createInfo.imageUsage(),
                "sharingMode", createInfo.imageSharingMode(),
                "presentMode", createInfo.presentMode(),
                "oldSwapchain", hex(createInfo.oldSwapchain())
        );
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
            MetricsDiagnostics.warn(
                    "fsr.frame_generation",
                    "swapchain_unavailable_fallback",
                    "nativeResult", result,
                    "fallback", "regular Vulkan swapchain",
                    "status", status
            );
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
            MetricsDiagnostics.info(
                    "fsr.frame_generation",
                    "swapchain_created",
                    "result", result,
                    "swapchain", outSwapchain.remaining() > 0 ? hex(outSwapchain.get(outSwapchain.position())) : "unavailable",
                    "recoveryGeneration", recoveryGeneration,
                    "status", status()
            );
        } else {
            MetricsDiagnostics.warn(
                    "fsr.frame_generation",
                    "swapchain_creation_failed",
                    "result", result,
                    "status", status,
                    "recoveryGeneration", recoveryGeneration
            );
        }
        return result;
    }

    public synchronized void destroyFrameGenerationSwapchain(
            VkDevice device,
            long swapchain,
            VkAllocationCallbacks allocator
    ) {
        boolean destroyedByBridge = bridge.destroyFrameGenerationSwapchain(device, swapchain, allocator);
        MetricsDiagnostics.info(
                "fsr.frame_generation",
                "swapchain_destroyed",
                "swapchain", hex(swapchain),
                "destroyedByFidelityFx", destroyedByBridge
        );
        if (!destroyedByBridge) {
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
            if(frameGenerationPresentTracker.gamePresentCount()%120==0) {
                MetricsDiagnostics.info("fsr.frame_generation","present_counts",
                        "gameFrames",frameGenerationPresentTracker.gamePresentCount(),
                        "displayPresentCounter",sdkPresentCount,
                        "applicationPresentIntervalMs",frameGenerationPresentTracker.lastIntervalMs());
            }
            if (frameGenerationPresentTracker.recordSuccessfulGamePresent(sdkPresentCount)) {
                SaltsAntiAliasing.LOGGER.info(
                        "AMD FSR3 frame generation confirmed: {} display presents from {} rendered game frames",
                        frameGenerationPresentTracker.sdkPresentCount(),
                        frameGenerationPresentTracker.gamePresentCount()
                );
                MetricsDiagnostics.info(
                        "fsr.frame_generation",
                        "output_confirmed",
                        "sdkPresentCount", frameGenerationPresentTracker.sdkPresentCount(),
                        "gamePresentCount", frameGenerationPresentTracker.gamePresentCount()
                );
            }
        } else if (result != KHRSwapchain.VK_ERROR_OUT_OF_DATE_KHR) {
            MetricsDiagnostics.warnOnce(
                    "fsr.present." + result,
                    "fsr.frame_generation",
                    "present_failed",
                    "result", result,
                    "sdkPresentCount", frameGenerationPresentTracker.sdkPresentCount(),
                    "gamePresentCount", frameGenerationPresentTracker.gamePresentCount()
            );
        }
        return result;
    }

    public synchronized void setPresentationInputs(long depth,long motion) { bridge.setPresentationInputs(depth,motion); }

    public synchronized void flushDiagnosticCapture() { bridge.flushDiagnosticCapture(); }

    public synchronized long renderedPresentCount() { return frameGenerationPresentTracker.gamePresentCount(); }

    /** Both rates use the same measured window; post-FG includes real and generated presents. */
    public synchronized String decorateDebugFps(String original) {
        if (!isFrameGenerationSwapchainActive()) return original;
        int marker=original.indexOf(" fps");
        if (marker<0) return original;
        double rendered=frameGenerationPresentTracker.renderedFps();
        double presented=frameGenerationPresentTracker.presentedFps();
        String prefix=rendered>=0 && presented>=0
                ? Math.round(rendered)+" fps pre-FG | "+Math.round(presented)+" fps post-FG"
                : original.substring(0,marker)+" fps pre-FG | -- fps post-FG";
        return prefix+original.substring(marker+4);
    }

    public synchronized long frameGenerationPresentCount() {
        return frameGenerationPresentTracker.sdkPresentCount();
    }

    public synchronized boolean isFrameGenerationOutputConfirmed() {
        return frameGenerationPresentTracker.outputConfirmed();
    }

    public synchronized void releaseRenderResources() { bridge.releaseRenderResources(); MetricsDiagnostics.info("lifecycle","native_feature_retired","feature","FSR"); }

    public synchronized void shutdown() {
        MetricsDiagnostics.info(
                "fsr.runtime",
                "shutdown",
                "statusBeforeShutdown", status(),
                "configuration", configuration,
                "frameGenerationSwapchainRequested", frameGenerationSwapchainRequested,
                "frameGenerationOutputConfirmed", frameGenerationPresentTracker.outputConfirmed(),
                "sdkPresentCount", frameGenerationPresentTracker.sdkPresentCount(),
                "gamePresentCount", frameGenerationPresentTracker.gamePresentCount(),
                "recoveryGeneration", recoveryGeneration
        );
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
            MetricsDiagnostics.info(
                    "fsr.frame_generation",
                    "surface_reconfiguration_requested",
                    "status", status,
                    "deviceInfo", deviceInfo
            );
        }
        if (status != previousStatus) {
            SaltsAntiAliasing.LOGGER.info("AMD FSR runtime status: {}", status.message());
            MetricsDiagnostics.info(
                    "fsr.runtime",
                    "status_changed",
                    "previousStatus", previousStatus,
                    "currentStatus", status,
                    "message", status.message(),
                    "deviceInfo", deviceInfo,
                    "configuration", configuration,
                    "frameGenerationRequested", frameGenerationSwapchainRequested,
                    "frameGenerationRejected", frameGenerationSwapchainRejected,
                    "recoveryGeneration", recoveryGeneration
            );
        }
    }

    private void resetFrameGenerationPresentTracking() {
        frameGenerationPresentTracker.reset();
    }

    private static String hex(long value) {
        return value == 0L ? "0x0" : "0x" + Long.toUnsignedString(value, 16);
    }
}
