package org.betterLostItems.salts_anti_aliasing.client.render.vulkan;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.pipeline.*;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import com.mojang.renderpearl.backend.vulkan.VulkanDevice;
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
    private RenderPipeline copyPipeline;
    private String failedConfiguration;
    private String activeConfiguration;
    private VulkanGpuProfiler.Scope handTiming;

    public static VulkanHandAaController instance() { return INSTANCE; }
    public long allocatedTargetBytes() { return VulkanOwnedTargetMemory.bytes(target); }
    public void retry() { failedConfiguration = null; }

    private static boolean enabled(AntiAliasingConfig config) {
        var mode = config.mode;
        return config.tuning.upscalerHandAa.samples() > 1 && (mode.usesDlssQualityControl()
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
                target = VulkanMsaaState.withTextureSampleCount(wanted, () -> new TextureTarget(
                        "Salt's Upscaler Hand AA", main.width, main.height, GpuFormat.RGBA8_UNORM, GpuFormat.D32_FLOAT));
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
                            .withUniform("WorldSampler", UniformType.COMBINED_IMAGE_SAMPLER).build())
                    .withColorTargetState(0, new ColorTargetState(Optional.empty(), GpuFormat.RGBA8_UNORM,
                            ColorTargetState.WRITE_ALL))
                    .withPrimitiveTopology(PrimitiveTopology.TRIANGLES).build();
        }
        // Draw to populate every MSAA sample; Vulkan image blits cannot copy 1x into 4x images.
        try (var pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                () -> "Salt's Hand AA Background", target.getColorTextureView(), Optional.empty())) {
            pass.setPipeline(RenderSystem.getCompiledPipeline(copyPipeline));
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("WorldSampler", source, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
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
