package org.betterLostItems.salts_anti_aliasing.client.render.vulkan;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.pipeline.*;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.shaders.*;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vulkan.VulkanDevice;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import org.betterLostItems.salts_anti_aliasing.SaltsAntiAliasing;
import org.betterLostItems.salts_anti_aliasing.client.SaltsAntiAliasingClient;
import org.betterLostItems.salts_anti_aliasing.client.config.AntiAliasingConfig;
import org.betterLostItems.salts_anti_aliasing.mixin.client.GpuDeviceAccessor;
import java.util.Optional;

/** Native-resolution hand coverage without injecting hand pixels into world temporal history. */
public final class VulkanHandAaController {
    private static final VulkanHandAaController INSTANCE = new VulkanHandAaController();
    private TextureTarget target;
    private RenderTarget destination;
    private int samples;

    /** Actual allocated hand coverage, available to diagnostics after a frame has resolved. */
    public int allocatedSamples() { return target == null ? 1 : samples; }
    private RenderPipeline copyPipeline;
    private String failedConfiguration;
    private String activeConfiguration;
    private VulkanGpuProfiler.Scope handTiming;

    public static VulkanHandAaController instance() { return INSTANCE; }
    public long allocatedTargetBytes() { return VulkanOwnedTargetMemory.bytes(target); }
    public void retry() { failedConfiguration = null; }

    private static boolean enabled(AntiAliasingConfig config) {
        var mode = config.mode;
        return config.tuning.upscalerHandAa.samples() > 1 && ((mode.usesDlssQualityControl() || mode.usesXessQualityControl() || mode.usesSgsr2QualityControl())
                || mode.usesFsrQualityControl() || mode.usesSpatialUpscaleQualityControl());
    }

    public void maintain(AntiAliasingConfig config) {
        Minecraft mc = Minecraft.getInstance();
        var runtime = SaltsAntiAliasingClient.runtimeOrNull();
        if (runtime != null) config = runtime.effectiveConfigSnapshot();
        if (!enabled(config) || mc.level == null || mc.player == null || mc.player.isSpectator()
                || !mc.options.getCameraType().isFirstPerson() || mc.gui.hud.isHidden()) {
            release();
            failedConfiguration = null;
        }
    }

    private GameRenderer handRenderer;

    public void beginHandRendering(GameRenderer renderer) { handRenderer = renderer; }

    public void redirectHandPass(com.mojang.blaze3d.systems.RenderPassDescriptor descriptor) {
        if (handRenderer == null || descriptor.colorAttachments.size() != 1 || descriptor.depthAttachment == null) return;
        var color = descriptor.colorAttachments.get(0);
        var depth = descriptor.depthAttachment;
        if (color == null || color.textureView() == null || depth.textureView() == null) return;
        RenderTarget main = handRenderer.mainRenderTarget();
        if (color.textureView().texture() != main.getColorTexture() || depth.textureView().texture() != main.getDepthTexture()) return;
        RenderTarget hand = destination == null ? begin(handRenderer, color.textureView(), depth.textureView()) : target;
        if (hand == null) return;
        descriptor.colorAttachments.set(0, new com.mojang.blaze3d.systems.RenderPassDescriptor.Attachment<>(
                hand.getColorTextureView(), color.clearValue()));
        descriptor.depthAttachment = new com.mojang.blaze3d.systems.RenderPassDescriptor.Attachment<>(
                hand.getDepthTextureView(), depth.clearValue());
    }

    /** Called only when vanilla actually opens a first-person hand render pass. */
    public RenderTarget begin(GameRenderer renderer, GpuTextureView color, GpuTextureView depth) {
        destination = null;
        var runtime = SaltsAntiAliasingClient.runtimeOrNull();
        if (runtime == null) return null;
        AntiAliasingConfig config = runtime.effectiveConfigSnapshot();
        if (!enabled(config)) return null;
        RenderTarget main = renderer.mainRenderTarget();
        // Other presentation targets (for example a 3D HUD) retain vanilla's attachment pairing.
        if (color == null || depth == null || color.texture() != main.getColorTexture()
                || depth.texture() != main.getDepthTexture()
                || color.texture().getFormat() != GpuFormat.RGBA8_UNORM
                || depth.texture().getFormat() != GpuFormat.D32_FLOAT) return null;
        String signature = config.mode + ":" + main.width + ":" + main.height + ":"
                + config.tuning.upscalerHandAa + ":" + config.tuning.upscalerHandAlphaToCoverage;
        if (signature.equals(failedConfiguration)) return null;
        try {
            var device = ((GpuDeviceAccessor) RenderSystem.getDevice()).saltsAntiAliasing$backend();
            if (!(device instanceof VulkanDevice vulkan)) return null;
            // Pixel-art item faces meet alpha-tested atlas regions. Pixel-frequency shading
            // can discard covered subsamples there and reveal dotted seams through the item.
            if (!VulkanMsaaCapabilities.handSampleShadingEnabled(vulkan)) {
                failedConfiguration = signature;
                return null;
            }
            int wanted = VulkanMsaaCapabilities.bestSupportedSceneSamples(vulkan, GpuFormat.RGBA8_UNORM,
                    true, config.tuning.upscalerHandAa.samples());
            if (wanted <= 1) { failedConfiguration = signature; return null; }
            if (target == null || target.width != main.width || target.height != main.height || samples != wanted) {
                release();
                target = VulkanMsaaState.withTextureSampleCount(wanted, () -> new TextureTarget("Salt's Upscaler Hand AA", main.width, main.height, true, GpuFormat.RGBA8_UNORM));
                samples = wanted;
            }
            try (var timing = VulkanGpuProfiler.scope("HandAA.background")) {
                copyWorld(color);
                RenderSystem.getDevice().createCommandEncoder().clearDepthTexture(target.getDepthTexture(), 0.0);
            }
            destination = main;
            activeConfiguration = signature;
            VulkanMsaaState.beginHand(config.tuning.upscalerHandAlphaToCoverage);
            handTiming = VulkanGpuProfiler.scope("HandAA.render");
            return target;
        } catch (RuntimeException failure) {
            failedConfiguration = signature;
            destination = null;
            SaltsAntiAliasing.LOGGER.warn("Hand anti-aliasing unavailable for {}; using native hand rendering", signature, failure);
            release();
            return null;
        }
    }

    public void end() {
        handRenderer = null;
        RenderTarget output = destination;
        destination = null;
        VulkanMsaaState.endHand();
        if (handTiming != null) { handTiming.close(); handTiming = null; }
        if (output == null) return;
        try (var timing = VulkanGpuProfiler.scope("HandAA.resolve")) {
            VulkanSceneMsaaController.resolveColor(target, output, samples);
        } catch (RuntimeException failure) {
            failedConfiguration = activeConfiguration;
            SaltsAntiAliasing.LOGGER.warn("Hand AA resolve failed; using native hand rendering", failure);
            release();
        }
    }

    private void copyWorld(GpuTextureView source) {
        if (copyPipeline == null) {
            copyPipeline = RenderPipeline.builder(RenderPipelines.POST_PROCESSING_SNIPPET)
                    .withLocation(Identifier.parse("salts_anti_aliasing:pipeline/hand_background_copy"))
                    .withVertexShader(Identifier.withDefaultNamespace("core/screenquad"))
                    .withFragmentShader(Identifier.parse("salts_anti_aliasing:post/hand_background_copy"))
                    .withBindGroupLayout(BindGroupLayout.builder()
                            .withSampler("WorldSampler").build())
                    .withColorTargetState(0, new ColorTargetState(Optional.empty(), GpuFormat.RGBA8_UNORM,
                            ColorTargetState.WRITE_ALL))
                    .withPrimitiveTopology(PrimitiveTopology.TRIANGLES).build();
        }
        // Draw to populate every MSAA sample; Vulkan image blits cannot copy 1x into 4x images.
        try (var pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                () -> "Salt's Hand AA Background", target.getColorTextureView(), Optional.empty())) {
            pass.setPipeline(copyPipeline);
            RenderSystem.bindDefaultUniforms(pass);
            pass.bindTexture("WorldSampler", source, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
            pass.draw(3, 1, 0, 0);
        }
    }

    private void release() {
        destination = null;
        if (target != null) target.destroyBuffers();
        target = null;
        samples = 1;
    }
}
