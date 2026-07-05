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
import org.lwjgl.vulkan.VK12;
import org.lwjgl.vulkan.VkCommandBuffer;
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

    private VulkanSceneMsaaController() {
    }

    public static VulkanSceneMsaaController instance() {
        return INSTANCE;
    }

    public void beginSceneRendering(GameRenderer gameRenderer, AntiAliasingConfig config) {
        RenderSystem.assertOnRenderThread();
        clearFrameState();

        if (disabledAfterFailure || config.mode != AntiAliasingMode.MSAA) {
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

        try {
            int samples = ensureMsaaTargetWithFallback(
                    mainTarget.width,
                    mainTarget.height,
                    mainTarget.useDepth,
                    config.msaaSampleLevel
            );
            this.mainTarget = mainTarget;
            active = true;

            VulkanMsaaState.setPipelineSampleCount(samples);
            RenderSystem.getDevice().clearPipelineCache();
        } catch (RuntimeException exception) {
            disableAfterFailure("Disabling Vulkan MSAA scene rendering after a setup failure", exception);
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
            disableAfterFailure("Disabling Vulkan MSAA scene rendering after a resolve failure", exception);
        } finally {
            VulkanMsaaState.clearPipelineSampleCount();
            RenderSystem.getDevice().clearPipelineCache();
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

        for (MsaaSampleLevel level : MsaaSampleLevel.valuesDescending()) {
            int samples = level.samples();
            if (samples > requestedSamples) {
                continue;
            }

            try {
                ensureMsaaTarget(width, height, useDepth, samples);
                if (samples != requestedSamples) {
                    SaltsAntiAliasing.LOGGER.warn(
                            "Vulkan MSAA requested {}x but using {}x after allocation fallback",
                            requestedSamples,
                            samples
                    );
                }
                return samples;
            } catch (RuntimeException exception) {
                lastFailure = exception;
                destroyResources();
            }
        }

        throw new IllegalStateException("Unable to create a Vulkan MSAA scene target", lastFailure);
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

        VulkanCommandEncoder encoder = vulkanDevice().createCommandEncoder();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkCommandBuffer commandBuffer = encoder.allocateAndBeginTransientCommandBuffer();
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
            VulkanCommandEncoder.memoryBarrier(commandBuffer, stack);
            encoder.execute(commandBuffer);
        }
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

    private void disableAfterFailure(String message, RuntimeException exception) {
        disabledAfterFailure = true;
        destroyResources();
        VulkanMsaaState.clearPipelineSampleCount();
        clearFrameState();
        SaltsAntiAliasing.LOGGER.error(message, exception);
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
