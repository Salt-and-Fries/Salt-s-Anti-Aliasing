package org.betterLostItems.salts_anti_aliasing.vulkanmod;

import org.betterLostItems.salts_anti_aliasing.client.config.AntiAliasingConfig;
import org.betterLostItems.salts_anti_aliasing.client.config.AntiAliasingMode;
import org.betterLostItems.salts_anti_aliasing.client.render.api.RenderResolution;
import org.betterLostItems.salts_anti_aliasing.client.render.vulkan.dlss.*;
import org.betterLostItems.salts_anti_aliasing.client.render.vulkan.fsr.*;
import org.betterLostItems.salts_anti_aliasing.client.render.vulkan.xess.XessRuntime;
import org.betterLostItems.salts_anti_aliasing.vulkanmod.gpu.*;

import java.util.List;

import static org.lwjgl.vulkan.VK10.*;

/** Reuses the modern JNI contracts with raw VulkanMod resources and explicit SDK state transitions. */
public final class NativeUpscalers implements VendorUpscaler {
    private final VulkanContext context;
    private String settings;
    private long previousNanos;
    private long evaluatedFrames;

    public NativeUpscalers(VulkanContext context) { this.context = context; }
    public long evaluatedFrames() { return evaluatedFrames; }

    public RenderResolution size(AntiAliasingConfig config, int width, int height) {
        String key = config.mode + ":" + config.dlssQualityPreset + ":" + config.tuning.dlssModel + ":" + config.tuning.dlssColorInput
                + ":" + config.xessQualityPreset + ":" + config.fsrQualityPreset + ":" + width + ":" + height;
        if (settings != null && !settings.equals(key)) {
            VulkanContext.check(org.betterLostItems.salts_anti_aliasing.vulkanmod.gpu.VulkanContext.waitIdle(context.device()), "Retire previous native upscaler settings");
            retire();
        }
        settings = key;
        if (config.mode.usesDlssQualityControl()) {
            var runtime = DlssRuntime.instance();
            if (!runtime.isReady()) throw new IllegalStateException(runtime.status().message());
            var result = runtime.queryOptimalSettings(config.dlssQualityPreset, config.tuning.dlssModel,
                    config.tuning.dlssColorInput.linear(false), width, height);
            return new RenderResolution(result.renderWidth(), result.renderHeight());
        }
        if (config.mode.usesXessQualityControl()) {
            var runtime = XessRuntime.instance();
            if (!runtime.isReady()) throw new IllegalStateException(runtime.message());
            var result = runtime.queryOptimalSettings(config.xessQualityPreset, width, height);
            return new RenderResolution(result.renderWidth(), result.renderHeight());
        }
        if (config.mode.usesFsrQualityControl()) {
            var runtime = FsrRuntime.instance();
            if (!runtime.status().upscalingReady()) throw new IllegalStateException(runtime.status().message());
            var result = runtime.queryOptimalSettings(config.fsrQualityPreset, width, height);
            return new RenderResolution(result.renderWidth(), result.renderHeight());
        }
        throw new IllegalArgumentException("No native sizing rule for " + config.mode);
    }

    @Override public void evaluate(Frame frame) {
        var size = size(frame.config(), frame.output().width, frame.output().height);
        if (size.width() != frame.color().width || size.height() != frame.color().height)
            throw new IllegalStateException("Native SDK render dimensions changed during a frame");
        if ((frame.output().usage & VK_IMAGE_USAGE_STORAGE_BIT) == 0)
            throw new IllegalStateException("Native output format cannot be used as a storage image");
        boolean dlss = frame.config().mode.usesDlssQualityControl();
        int readLayout = dlss ? VK_IMAGE_LAYOUT_GENERAL : VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL;
        var inputs = List.of(frame.color(), frame.depth(), frame.motion(), frame.opaque(), frame.reactive(), frame.transparency());
        for (var input : inputs) input.synchronize(frame.commands(), readLayout);
        frame.output().synchronize(frame.commands(), VK_IMAGE_LAYOUT_GENERAL);
        frame.hudless().synchronize(frame.commands(), VK_IMAGE_LAYOUT_GENERAL);
        int result;
        if (dlss) result = dlss(frame);
        else if (frame.config().mode.usesXessQualityControl()) result = xess(frame);
        else result = fsr(frame);
        for (var input : inputs) input.synchronize(frame.commands(), VK_IMAGE_LAYOUT_GENERAL);
        frame.output().synchronize(frame.commands(), VK_IMAGE_LAYOUT_GENERAL);
        frame.hudless().synchronize(frame.commands(), VK_IMAGE_LAYOUT_GENERAL);
        if (result != 0) throw new IllegalStateException(frame.config().mode + " native evaluation failed: " + result);
        evaluatedFrames++;
    }

    private int dlss(Frame frame) {
        var color = frame.color(); var output = frame.output(); var depth = frame.depth(); var motion = frame.motion(); var camera = frame.camera();
        return DlssRuntime.instance().evaluate(new DlssEvaluateParameters(frame.commands().address(),
                color.image, color.view, output.image, output.view, depth.image, depth.view, motion.image, motion.view,
                color.width, color.height, output.width, output.height, camera.jitterX(), camera.jitterY(),
                camera.resetHistory(), camera.frameIndex(), 1f / color.width, 1f / color.height,
                camera.currentArray(), camera.previousArray(), camera.projectionArray(), camera.cameraParameters(),
                new int[]{color.format, output.format, depth.format, motion.format},
                new int[]{color.usage, output.usage, depth.usage, motion.usage}));
    }

    private int xess(Frame frame) {
        var color = frame.color(); var output = frame.output(); var depth = frame.depth(); var motion = frame.motion();
        return XessRuntime.instance().evaluate(frame.commands().address(),
                new long[]{color.image, color.view, output.image, output.view, depth.image, depth.view, motion.image, motion.view},
                new int[]{color.format, output.format, depth.format, motion.format}, color.width, color.height,
                frame.camera().jitterX(), frame.camera().jitterY(), frame.camera().resetHistory());
    }

    private int fsr(Frame frame) {
        var color = frame.color(); var output = frame.output(); var depth = frame.depth(); var motion = frame.motion();
        var opaque = frame.opaque(); var reactive = frame.reactive(); var transparency = frame.transparency(); var hudless = frame.hudless();
        var camera = frame.camera(); var parameters = camera.cameraParameters();
        long now = System.nanoTime();
        float delta = previousNanos == 0 ? 16.6667f : Math.max(.01f, Math.min(250, (now - previousNanos) / 1_000_000f));
        previousNanos = now;
        boolean generate = frame.config().mode.usesFsrFrameGeneration() && FsrRuntime.instance().isFrameGenerationSwapchainOwned();
        if (generate) FsrRuntime.instance().setPresentationInputs(frame.presentationDepth().image, frame.presentationMotion().image);
        return FsrRuntime.instance().evaluate(new FsrEvaluateParameters(frame.commands().address(),
                frame.config().mode == AntiAliasingMode.FSR2_SUPER_RESOLUTION ? 2 : 3,
                generate,
                color.image, color.view, output.image, output.view, depth.image, depth.view, motion.image, motion.view,
                opaque.image, opaque.view, reactive.image, reactive.view, transparency.image, transparency.view, hudless.image, hudless.view,
                color.width, color.height, output.width, output.height, camera.jitterX(), camera.jitterY(),
                camera.resetHistory(), camera.frameIndex(), 1, 1, frame.config().sharpenStrength, delta,
                parameters[0], parameters[1], parameters[2], 1, parameters[4], parameters[5], parameters[6],
                parameters[7], parameters[8], parameters[9], parameters[10], parameters[11], parameters[12], parameters[13], parameters[14], parameters[15]));
    }

    @Override public void retire() {
        DlssRuntime.instance().releaseRenderResources();
        XessRuntime.instance().releaseRenderResources();
        FsrRuntime.instance().releaseRenderResources();
        settings = null; previousNanos = 0;
    }
}
