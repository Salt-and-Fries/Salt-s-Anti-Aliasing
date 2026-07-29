package org.betterLostItems.salts_anti_aliasing.client.render.vulkan;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.GpuDeviceBackend;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vulkan.VulkanCommandEncoder;
import com.mojang.blaze3d.vulkan.VulkanDevice;
import com.mojang.blaze3d.vulkan.VulkanGpuTexture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.betterLostItems.salts_anti_aliasing.SaltsAntiAliasing;
import org.betterLostItems.salts_anti_aliasing.client.config.AntiAliasingConfig;
import org.betterLostItems.salts_anti_aliasing.client.config.AntiAliasingMode;
import org.betterLostItems.salts_anti_aliasing.client.config.MsaaSampleLevel;
import org.betterLostItems.salts_anti_aliasing.mixin.client.GpuDeviceAccessor;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.KHRSynchronization2;
import org.lwjgl.vulkan.VK12;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkDependencyInfo;
import org.lwjgl.vulkan.VkImageMemoryBarrier2;
import org.lwjgl.vulkan.VkImageResolve;

/**
 * Owns native Vulkan multisample scene rendering and resolves the scene color into Minecraft's main
 * target after world rendering.
 */
public final class VulkanSceneMsaaController {
    private static final VulkanSceneMsaaController INSTANCE = new VulkanSceneMsaaController();
    private static final String TARGET_LABEL = "Salt's Vulkan MSAA Scene";

    private boolean disabledAfterFailure;
    private boolean active;
    private TextureTarget msaaTarget;
    private RenderTarget mainTarget;
    private int msaaTargetSamples = 1;
    private int lastReportedRequestedSamples;
    private int lastReportedActualSamples;
    private int failedWidth = -1;
    private int failedHeight = -1;
    private int failedRequestedSamples = -1;
    private boolean failedUseDepth;

    private VulkanSceneMsaaController() {
    }

    public static VulkanSceneMsaaController instance() {
        return INSTANCE;
    }

    public void beginSceneRendering(GameRenderer gameRenderer, AntiAliasingConfig config) {
        RenderSystem.assertOnRenderThread();
        clearFrameState();

        if (config.mode != AntiAliasingMode.MSAA) {
            leaveMsaaMode();
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }

        RenderTarget mainTarget = gameRenderer.mainRenderTarget();
        if (!mainTarget.useDepth || mainTarget.width <= 0 || mainTarget.height <= 0) {
            return;
        }

        int requestedSamples = MsaaSampleLevel.clamp(config.msaaSampleLevel).samples();
        if (disabledAfterFailure) {
            if (matchesFailedConfiguration(
                    mainTarget.width,
                    mainTarget.height,
                    mainTarget.useDepth,
                    requestedSamples
            )) {
                return;
            }
            clearFailureState();
        }

        try {
            ensureMsaaTargetWithFallback(
                    mainTarget.width,
                    mainTarget.height,
                    mainTarget.useDepth,
                    config.msaaSampleLevel
            );
            this.mainTarget = mainTarget;
            active = true;
        } catch (RuntimeException exception) {
            disableAfterFailure(
                    "Disabling Vulkan MSAA scene rendering for the current configuration after a setup failure",
                    exception,
                    mainTarget.width,
                    mainTarget.height,
                    mainTarget.useDepth,
                    requestedSamples
            );
        }
    }

    public void endSceneRendering(GameRenderer gameRenderer, AntiAliasingConfig config) {
        RenderSystem.assertOnRenderThread();
        if (!active) {
            clearFrameState();
            return;
        }

        RenderTarget mainTarget = this.mainTarget;
        TextureTarget msaaTarget = this.msaaTarget;

        try {
            active = false;
            if (mainTarget != null
                    && mainTarget.getColorTexture() != null
                    && msaaTarget != null
                    && msaaTarget.getColorTexture() != null) {
                resolveColor(msaaTarget, mainTarget);
            }
        } catch (RuntimeException exception) {
            int requestedSamples = MsaaSampleLevel.clamp(config.msaaSampleLevel).samples();
            disableAfterFailure(
                    "Disabling Vulkan MSAA scene rendering for the current configuration after a resolve failure",
                    exception,
                    mainTarget == null ? -1 : mainTarget.width,
                    mainTarget == null ? -1 : mainTarget.height,
                    mainTarget != null && mainTarget.useDepth,
                    requestedSamples
            );
        } finally {
            clearFrameState();
        }
    }

    public RenderTarget overrideMainTarget() {
        return active ? msaaTarget : null;
    }

    public GpuTexture overrideColorTexture(RenderTarget target) {
        RenderTarget redirectedTarget = mappedTarget(target);
        return redirectedTarget == null ? null : redirectedTarget.getColorTexture();
    }

    public GpuTextureView overrideColorTextureView(RenderTarget target) {
        RenderTarget redirectedTarget = mappedTarget(target);
        return redirectedTarget == null ? null : redirectedTarget.getColorTextureView();
    }

    public GpuTexture overrideDepthTexture(RenderTarget target) {
        RenderTarget redirectedTarget = mappedTarget(target);
        return redirectedTarget == null ? null : redirectedTarget.getDepthTexture();
    }

    public GpuTextureView overrideDepthTextureView(RenderTarget target) {
        RenderTarget redirectedTarget = mappedTarget(target);
        return redirectedTarget == null ? null : redirectedTarget.getDepthTextureView();
    }

    public boolean redirectCopyDepth(RenderTarget target, RenderTarget sourceTarget) {
        RenderTarget redirectedTarget = mappedTarget(target);
        RenderTarget redirectedSource = mappedTarget(sourceTarget);
        if (redirectedTarget == null && redirectedSource == null) {
            return false;
        }

        RenderTarget resolvedTarget = redirectedTarget != null ? redirectedTarget : target;
        RenderTarget resolvedSource = redirectedSource != null ? redirectedSource : sourceTarget;
        if (resolvedTarget == resolvedSource) {
            return true;
        }

        if (resolvedTarget == msaaTarget || resolvedSource == msaaTarget) {
            return true;
        }

        resolvedTarget.copyDepthFrom(resolvedSource);
        return true;
    }

    private int ensureMsaaTargetWithFallback(
            int width,
            int height,
            boolean useDepth,
            MsaaSampleLevel requestedLevel
    ) {
        RuntimeException lastFailure = null;
        int requestedSamples = MsaaSampleLevel.clamp(requestedLevel).samples();
        int supportedSamples = VulkanMsaaCapabilities.bestSupportedSceneSamples(
                vulkanDevice(),
                GpuFormat.RGBA8_UNORM,
                useDepth,
                requestedSamples
        );

        if (supportedSamples <= 1) {
            throw new IllegalStateException("Vulkan device does not support multisampled scene color/depth targets");
        }

        for (MsaaSampleLevel level : MsaaSampleLevel.valuesDescending()) {
            int samples = level.samples();
            if (samples > requestedSamples || samples > supportedSamples) {
                continue;
            }

            try {
                ensureMsaaTarget(width, height, useDepth, samples);
                reportSampleFallback(requestedSamples, samples);
                return samples;
            } catch (RuntimeException exception) {
                lastFailure = exception;
                destroyResources();
            }
        }

        throw new IllegalStateException("Unable to create a Vulkan MSAA scene target", lastFailure);
    }

    private void reportSampleFallback(int requestedSamples, int actualSamples) {
        if (actualSamples == requestedSamples) {
            lastReportedRequestedSamples = 0;
            lastReportedActualSamples = 0;
            return;
        }

        if (lastReportedRequestedSamples == requestedSamples && lastReportedActualSamples == actualSamples) {
            return;
        }

        lastReportedRequestedSamples = requestedSamples;
        lastReportedActualSamples = actualSamples;
        SaltsAntiAliasing.LOGGER.warn(
                "Vulkan MSAA requested {}x but using {}x after capability/allocation fallback",
                requestedSamples,
                actualSamples
        );
    }

    private void ensureMsaaTarget(int width, int height, boolean useDepth, int samples) {
        if (msaaTarget == null || msaaTarget.useDepth != useDepth || msaaTargetSamples != samples) {
            destroyResources();
            VulkanMsaaState.withTextureSampleCount(samples, () -> {
                msaaTarget = new TextureTarget(TARGET_LABEL, width, height, useDepth, GpuFormat.RGBA8_UNORM);
                return null;
            });
            msaaTargetSamples = samples;
            return;
        }

        if (msaaTarget.width != width || msaaTarget.height != height) {
            VulkanMsaaState.withTextureSampleCount(samples, () -> {
                msaaTarget.resize(width, height);
                return null;
            });
        }
    }

    private void resolveColor(TextureTarget sourceTarget, RenderTarget target) {
        GpuTexture sourceTexture = sourceTarget.getColorTexture();
        GpuTexture targetTexture = target.getColorTexture();
        if (!(sourceTexture instanceof VulkanGpuTexture sourceVulkanTexture)
                || !(targetTexture instanceof VulkanGpuTexture targetVulkanTexture)) {
            throw new IllegalStateException("Vulkan MSAA resolve requires Vulkan texture objects");
        }

        int sourceSamples = sampleCount(sourceTexture);
        int destinationSamples = sampleCount(targetTexture);
        VulkanMsaaCompatibility.validateColorResolve(
                sourceSamples,
                destinationSamples,
                sourceTexture.getWidth(0),
                sourceTexture.getHeight(0),
                targetTexture.getWidth(0),
                targetTexture.getHeight(0),
                sourceTexture.getFormat() == targetTexture.getFormat()
        );
        if (sourceSamples != msaaTargetSamples) {
            throw new IllegalStateException(
                    "Vulkan MSAA resolve source has " + sourceSamples
                            + "x samples, but the scene target expects " + msaaTargetSamples + "x"
            );
        }

        VulkanCommandEncoder encoder = vulkanDevice().createCommandEncoder();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkCommandBuffer commandBuffer = encoder.allocateAndBeginTransientCommandBuffer();
            resolveBarrier(commandBuffer, stack, sourceVulkanTexture, targetVulkanTexture);
            VkImageResolve.Buffer resolveRegion = VkImageResolve.calloc(1, stack);
            resolveRegion.srcSubresource()
                    .aspectMask(VK12.VK_IMAGE_ASPECT_COLOR_BIT)
                    .mipLevel(0)
                    .baseArrayLayer(0)
                    .layerCount(1);
            resolveRegion.dstSubresource()
                    .aspectMask(VK12.VK_IMAGE_ASPECT_COLOR_BIT)
                    .mipLevel(0)
                    .baseArrayLayer(0)
                    .layerCount(1);
            resolveRegion.srcOffset().set(0, 0, 0);
            resolveRegion.dstOffset().set(0, 0, 0);
            resolveRegion.extent().set(target.width, target.height, 1);

            VK12.vkCmdResolveImage(
                    commandBuffer,
                    sourceVulkanTexture.vkImage(),
                    VK12.VK_IMAGE_LAYOUT_GENERAL,
                    targetVulkanTexture.vkImage(),
                    VK12.VK_IMAGE_LAYOUT_GENERAL,
                    resolveRegion
            );
            resolvedBarrier(commandBuffer, stack, sourceVulkanTexture, targetVulkanTexture);
            encoder.execute(commandBuffer);
        }
    }

    private static void resolveBarrier(
            VkCommandBuffer commandBuffer,
            MemoryStack stack,
            VulkanGpuTexture sourceTexture,
            VulkanGpuTexture targetTexture
    ) {
        VkImageMemoryBarrier2.Buffer barriers = VkImageMemoryBarrier2.calloc(2, stack);
        configureImageBarrier(
                barriers.get(0),
                sourceTexture,
                KHRSynchronization2.VK_PIPELINE_STAGE_2_COLOR_ATTACHMENT_OUTPUT_BIT_KHR,
                KHRSynchronization2.VK_ACCESS_2_COLOR_ATTACHMENT_WRITE_BIT_KHR,
                KHRSynchronization2.VK_PIPELINE_STAGE_2_RESOLVE_BIT_KHR,
                KHRSynchronization2.VK_ACCESS_2_TRANSFER_READ_BIT_KHR
        );
        configureImageBarrier(
                barriers.get(1),
                targetTexture,
                KHRSynchronization2.VK_PIPELINE_STAGE_2_COLOR_ATTACHMENT_OUTPUT_BIT_KHR,
                KHRSynchronization2.VK_ACCESS_2_COLOR_ATTACHMENT_WRITE_BIT_KHR,
                KHRSynchronization2.VK_PIPELINE_STAGE_2_RESOLVE_BIT_KHR,
                KHRSynchronization2.VK_ACCESS_2_TRANSFER_WRITE_BIT_KHR
        );
        pipelineBarrier(commandBuffer, stack, barriers);
    }

    private static void resolvedBarrier(
            VkCommandBuffer commandBuffer,
            MemoryStack stack,
            VulkanGpuTexture sourceTexture,
            VulkanGpuTexture targetTexture
    ) {
        VkImageMemoryBarrier2.Buffer barriers = VkImageMemoryBarrier2.calloc(2, stack);
        configureImageBarrier(
                barriers.get(0),
                sourceTexture,
                KHRSynchronization2.VK_PIPELINE_STAGE_2_RESOLVE_BIT_KHR,
                KHRSynchronization2.VK_ACCESS_2_TRANSFER_READ_BIT_KHR,
                KHRSynchronization2.VK_PIPELINE_STAGE_2_ALL_COMMANDS_BIT_KHR,
                KHRSynchronization2.VK_ACCESS_2_MEMORY_READ_BIT_KHR
        );
        configureImageBarrier(
                barriers.get(1),
                targetTexture,
                KHRSynchronization2.VK_PIPELINE_STAGE_2_RESOLVE_BIT_KHR,
                KHRSynchronization2.VK_ACCESS_2_TRANSFER_WRITE_BIT_KHR,
                KHRSynchronization2.VK_PIPELINE_STAGE_2_COLOR_ATTACHMENT_OUTPUT_BIT_KHR,
                KHRSynchronization2.VK_ACCESS_2_COLOR_ATTACHMENT_READ_BIT_KHR
                        | KHRSynchronization2.VK_ACCESS_2_COLOR_ATTACHMENT_WRITE_BIT_KHR
        );
        pipelineBarrier(commandBuffer, stack, barriers);
    }

    private static void configureImageBarrier(
            VkImageMemoryBarrier2 barrier,
            VulkanGpuTexture texture,
            long sourceStage,
            long sourceAccess,
            long destinationStage,
            long destinationAccess
    ) {
        barrier.sType$Default()
                .srcStageMask(sourceStage)
                .srcAccessMask(sourceAccess)
                .dstStageMask(destinationStage)
                .dstAccessMask(destinationAccess)
                .oldLayout(VK12.VK_IMAGE_LAYOUT_GENERAL)
                .newLayout(VK12.VK_IMAGE_LAYOUT_GENERAL)
                .srcQueueFamilyIndex(VK12.VK_QUEUE_FAMILY_IGNORED)
                .dstQueueFamilyIndex(VK12.VK_QUEUE_FAMILY_IGNORED)
                .image(texture.vkImage());
        barrier.subresourceRange()
                .aspectMask(VK12.VK_IMAGE_ASPECT_COLOR_BIT)
                .baseMipLevel(0)
                .levelCount(1)
                .baseArrayLayer(0)
                .layerCount(1);
    }

    private static void pipelineBarrier(
            VkCommandBuffer commandBuffer,
            MemoryStack stack,
            VkImageMemoryBarrier2.Buffer barriers
    ) {
        VkDependencyInfo dependencyInfo = VkDependencyInfo.calloc(stack)
                .sType$Default()
                .pImageMemoryBarriers(barriers);
        KHRSynchronization2.vkCmdPipelineBarrier2KHR(commandBuffer, dependencyInfo);
    }

    private static VulkanDevice vulkanDevice() {
        GpuDevice device = RenderSystem.getDevice();
        GpuDeviceBackend backend = ((GpuDeviceAccessor) device).saltsAntiAliasing$backend();
        if (backend instanceof VulkanDevice vulkanDevice) {
            return vulkanDevice;
        }

        throw new IllegalStateException("Minecraft is not running on the Vulkan backend");
    }

    private RenderTarget mappedTarget(RenderTarget target) {
        if (!active || target != mainTarget) {
            return null;
        }

        return msaaTarget;
    }

    private void disableAfterFailure(
            String message,
            RuntimeException exception,
            int width,
            int height,
            boolean useDepth,
            int requestedSamples
    ) {
        disabledAfterFailure = true;
        failedWidth = width;
        failedHeight = height;
        failedUseDepth = useDepth;
        failedRequestedSamples = requestedSamples;
        destroyResources();
        clearFrameState();
        SaltsAntiAliasing.LOGGER.error(message, exception);
    }

    private boolean matchesFailedConfiguration(
            int width,
            int height,
            boolean useDepth,
            int requestedSamples
    ) {
        return failedWidth == width
                && failedHeight == height
                && failedUseDepth == useDepth
                && failedRequestedSamples == requestedSamples;
    }

    private static int sampleCount(GpuTexture texture) {
        return texture instanceof VulkanSampledTexture sampledTexture
                ? sampledTexture.saltsAntiAliasing$sampleCount()
                : 1;
    }

    private void leaveMsaaMode() {
        if (msaaTarget != null) {
            destroyResources();
        }
        clearFailureState();
    }

    private void clearFailureState() {
        disabledAfterFailure = false;
        failedWidth = -1;
        failedHeight = -1;
        failedUseDepth = false;
        failedRequestedSamples = -1;
    }

    private void destroyResources() {
        if (msaaTarget != null) {
            msaaTarget.destroyBuffers();
            msaaTarget = null;
        }
        msaaTargetSamples = 1;
    }

    private void clearFrameState() {
        active = false;
        mainTarget = null;
    }
}
