package org.betterLostItems.salts_anti_aliasing.vulkanmod;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.Minecraft;
import net.vulkanmod.gl.GlTexture;
import net.vulkanmod.vulkan.Renderer;
import net.vulkanmod.vulkan.Vulkan;
import net.vulkanmod.vulkan.framebuffer.Framebuffer;
import net.vulkanmod.vulkan.framebuffer.RenderPass;
import net.vulkanmod.vulkan.texture.VulkanImage;
import org.betterLostItems.salts_anti_aliasing.client.config.AntiAliasingConfig;
import org.betterLostItems.salts_anti_aliasing.client.config.AntiAliasingMode;
import org.betterLostItems.salts_anti_aliasing.vulkanmod.gpu.*;
import org.betterLostItems.salts_anti_aliasing.vulkanmod.mixin.RendererAccessor;


import static org.lwjgl.vulkan.VK10.*;

/** Minecraft 1.21.2/1.21.3 boundary; algorithms and native allocations live in the shared backend. */
public final class SaltsVulkanModClient implements ClientModInitializer {
    private static AntiAliasingConfig config = new AntiAliasingConfig();
    private static VulkanContext context;
    private static VulkanPostEffects effects;
    private static NativeUpscalers nativeUpscalers;
    private static MultisampleWorld msaa;
    private static String msaaKey;
    private static MultisampleWorld hand;
    private static String handKey;
    private static boolean renderingHand;
    private static VulkanFrameResources[] handFrames;
    private static Boolean sampleShading;
    private static VulkanImageResource opaque;
    private static TextureTarget scene;
    private static RenderTarget output, redirectedScene;
    private static boolean renderingWorld;
    private static int inputWidth, inputHeight;
    private static RuntimeException failure;
    private static boolean reloadPending;
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("salts_anti_aliasing");
    private static long resolvedFrames;
    private static long opaqueCaptures;
    private static final TemporalView temporal = new TemporalView();
    private static final org.betterLostItems.salts_anti_aliasing.client.debug.EdgeDebugAnalyzer edgeAnalyzer =
            new org.betterLostItems.salts_anti_aliasing.client.debug.EdgeDebugAnalyzer();
    private static java.nio.ByteBuffer geometry;
    private static int particleQuads;
    public static AntiAliasingConfig configSnapshot() { return config.copy(); }
    public static int particleQuads() { return particleQuads; }
    private static int lastMipBias;
    public static int mipBiasQuarterSteps() { return renderingWorld ? lastMipBias : 0; }
    public static int lastMipBiasQuarterSteps() { return lastMipBias; }

    @Override public void onInitializeClient() {
        org.betterLostItems.salts_anti_aliasing.client.SaltsAntiAliasingClient.initialize(
                new org.betterLostItems.salts_anti_aliasing.client.render.common.RenderRuntime.Adapter() {
                    public void changed(AntiAliasingConfig next) {
                        boolean previousFg = config.mode.usesFsrFrameGeneration();
                        if (config.mode != next.mode || config.debugViewsEnabled != next.debugViewsEnabled) edgeAnalyzer.reset(next.mode);
                        if (config.mode != next.mode) { temporal.invalidate(); failure = null; }
                        config = next;
                        updateFrameGeneration(previousFg);
                    }
                    public boolean frameGenerationConnected() { return true; }
                    public org.betterLostItems.salts_anti_aliasing.client.debug.EdgeDebugStats edgeStats() { return edgeAnalyzer.latestStats(); }
                    public void retry() { failure = null; reloadPending = true; temporal.invalidate(); }
                    public String status() { return failure == null ? config.mode.displayName() + " / VulkanMod" : "AA unavailable; rendering the original scene: " + failure.getMessage(); }
                    public String allocations() { return String.format(java.util.Locale.ROOT, "AA backend images: %.1f MiB", VulkanImageResource.ownedBytes() / 1048576.0); }
                    public String motionSummary() { return "Moving vertices: " + org.betterLostItems.salts_anti_aliasing.vulkanmod.motion.EntityMotionGeometry.coverage().movingVertices() + " / particles: " + particleQuads; }
                    public String motionDetails() { return org.betterLostItems.salts_anti_aliasing.vulkanmod.motion.EntityMotionGeometry.coverage().toString(); }
                });
    }

    public static void configure(AntiAliasingMode mode) {
        configure(c -> {
            c.mode = mode; c.tuning.debugView = org.betterLostItems.salts_anti_aliasing.client.config.RenderTuning.DebugView.NONE;
            c.debugViewsEnabled = false;
        });
    }
    public static void configure(java.util.function.Consumer<AntiAliasingConfig> editor) {
        org.betterLostItems.salts_anti_aliasing.client.SaltsAntiAliasingClient.runtime().editSettingsLive(editor);
        failure = null; temporal.invalidate();
    }
    private static void updateFrameGeneration(boolean previous) {
        if (previous == config.mode.usesFsrFrameGeneration()) return;
        org.betterLostItems.salts_anti_aliasing.client.render.vulkan.fsr.FsrRuntime.instance().configure(config);
        if (org.betterLostItems.salts_anti_aliasing.client.render.vulkan.VulkanNativeDeviceRegistry.latest() != null)
            Renderer.scheduleSwapChainUpdate();
    }
    public static long opaqueCaptures() { return opaqueCaptures; }
    public static RuntimeException failure() { return failure; }
    public static long resolvedFrames() { return resolvedFrames; }

    public static int inputWidth() { return inputWidth; }
    public static int inputHeight() { return inputHeight; }
    public static int samples() { return config.mode == AntiAliasingMode.MSAA && msaa != null ? msaa.samples() : 1; }
    public static int handSamples() { return hand == null ? 1 : hand.samples(); }
    public static long worldMultisamplePasses() { return msaa == null ? 0 : msaa.begunPasses(); }
    public static long handMultisamplePasses() { return hand == null ? 0 : hand.begunPasses(); }
    public static MultisampleWorld multisample(Framebuffer framebuffer) {
        if ((!renderingWorld || config.mode != AntiAliasingMode.MSAA || msaa == null) && !renderingHand) return null;
        var image = mainFramebuffer().getColorAttachment();
        return framebuffer.getColorAttachment() != null && framebuffer.getColorAttachment().getId() == image.getId()
                ? renderingHand ? hand : msaa : null;
    }
    public static RenderTarget sceneOverride() { return renderingWorld ? redirectedScene : null; }
    public static boolean internalFramebuffer(Framebuffer framebuffer) {
        return renderingWorld && redirectedScene != null
                && net.vulkanmod.gl.GlFramebuffer.getFramebuffer(redirectedScene.frameBufferId).getFramebuffer() == framebuffer;
    }
    public static boolean temporalActive() { return renderingWorld && config.mode.usesHistoryBuffers(); }
    public static TemporalView temporal() { return temporal; }

    public static void resourcesReloaded() { reloadPending = true; }

    public static void beginWorld() {
        try { prepareWorld(); }
        catch (RuntimeException error) {
            failed(error);
            renderingWorld = false; redirectedScene = null;
            var renderer = Renderer.getInstance();
            resetPipelineCache(renderer);
            renderer.getMainPass().rebindMainTarget();
            Renderer.resetViewport(); Renderer.resetScissor();
        }
    }

    private static void failed(RuntimeException error) {
        failure = error;
        temporal.invalidate();
        LOGGER.error("{} failed; continuing with the original scene. Change mode or use Retry Rendering to recover.", config.mode, error);
    }

    private static void prepareWorld() {
        org.betterLostItems.salts_anti_aliasing.vulkanmod.motion.EntityMotionGeometry.beginFrame(config);
        renderingWorld = false; redirectedScene = null;
        var client = Minecraft.getInstance();
        if (client.level == null || failure != null || !Renderer.isRecording()) return;
        if (reloadPending) { releaseGraphics(); reloadPending = false; temporal.invalidate(); }
        if (config.mode == AntiAliasingMode.OFF && config.sharpenStrength <= 0 && !config.debugViewsEnabled) { temporal.invalidate(); return; }
        if (context == null) {
            context = new VulkanContext(Vulkan.getVkDevice(), Vulkan.getAllocator());
            effects = new VulkanPostEffects(context, Renderer.getFramesNum());
            nativeUpscalers = new NativeUpscalers(context);
            effects.vendor(nativeUpscalers);
            effects.debugSamples(SaltsVulkanModClient::debugSample);
        }
        if (effects.framesInFlight() != Renderer.getFramesNum()) {
            effects.close();
            effects = new VulkanPostEffects(context, Renderer.getFramesNum());
            effects.vendor(nativeUpscalers);
            effects.debugSamples(SaltsVulkanModClient::debugSample);
            temporal.invalidate();
        }
        output = client.getMainRenderTarget();
        effects.opaque(null);
        if (config.mode == AntiAliasingMode.MSAA) {
            var renderer = Renderer.getInstance();
            renderer.endRenderPass();
            var color = borrow(output, false);
            var depth = borrow(output, true);
            String key = color.format + ":" + depth.format + ":" + color.width + ":" + color.height + ":" + config.msaaSampleLevel + ":" + config.msaaAlphaToCoverage;
            if (!key.equals(msaaKey)) {
                VulkanContext.check(org.betterLostItems.salts_anti_aliasing.vulkanmod.gpu.VulkanContext.waitIdle(context.device()), "Wait before changing MSAA attachments");
                if (msaa != null) msaa.close();
                msaa = new MultisampleWorld(context, mainFramebuffer(), color, depth,
                        config.msaaSampleLevel.samples(), config.msaaAlphaToCoverage, false);
                msaaKey = key;
            }
            msaa.newFrame(color, depth, net.vulkanmod.vulkan.VRenderSystem.clearColor);
        }
        if (config.usesInternalResolutionPath()) {
            int width = Math.max(1, Math.round(output.width * config.sceneRenderScale()));
            int height = Math.max(1, Math.round(output.height * config.sceneRenderScale()));
            if (config.mode.usesDlssQualityControl() || config.mode.usesXessQualityControl() || config.mode.usesFsrQualityControl()) {
                var resolution = nativeUpscalers.size(config, output.width, output.height);
                width = resolution.width(); height = resolution.height();
            }
            if (scene == null || scene.width != width || scene.height != height) {
                VulkanContext.check(org.betterLostItems.salts_anti_aliasing.vulkanmod.gpu.VulkanContext.waitIdle(context.device()), "Wait before resizing scene");
                if (scene != null) scene.destroyBuffers();
                scene = new TextureTarget(width, height, true, Minecraft.ON_OSX);
            }
            redirectedScene = scene;
        }
        renderingWorld = true;
        if (config.mode == AntiAliasingMode.MSAA || redirectedScene != null)
            Renderer.getInstance().getMainPass().rebindMainTarget();
        lastMipBias = TerrainTextureDetail.quarterSteps(config, redirectedScene == null ? output.width : redirectedScene.width, output.width);
        if (config.mode.usesHistoryBuffers()) {
            var input = redirectedScene == null ? output : redirectedScene;
            temporal.prepare(client.level, client.options.getCameraType(), client.getCameraEntity(), config,
                    input.width, input.height, output.width);
        } else temporal.invalidate();
    }

    private static void debugSample(VulkanPostEffects.DebugSample sample) {
        if (config.debugViewsEnabled && sample.mode() == config.mode)
            edgeAnalyzer.acceptRgba(sample.rgba(), sample.width(), sample.height(), sample.mode());
    }

    public static void captureOpaque() {
        if (!renderingWorld || !config.mode.usesFsrQualityControl()) return;
        var renderer = Renderer.getInstance();
        var framebuffer = ((RendererAccessor) renderer).salts$boundFramebuffer(); var pass = renderer.getBoundRenderPass();
        renderer.endRenderPass();
        // The render pass's final layout is applied by endRenderPass, before borrowing its state.
        var source = borrow(redirectedScene == null ? output : redirectedScene, false);
        if (opaque == null || opaque.width != source.width || opaque.height != source.height || opaque.format != source.format) {
            VulkanContext.check(org.betterLostItems.salts_anti_aliasing.vulkanmod.gpu.VulkanContext.waitIdle(context.device()), "Wait before resizing opaque scene capture");
            if (opaque != null) opaque.close();
            opaque = VulkanImageResource.create(context, source.width, source.height, source.format, VK_IMAGE_ASPECT_COLOR_BIT, 1);
        }
        VulkanImageOps.blit(Renderer.getCommandBuffer(), source, opaque, false);
        source.transition(Renderer.getCommandBuffer(), VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL);
        effects.opaque(opaque);
        opaqueCaptures++;
        resetPipelineCache(renderer);
        if (framebuffer != null && pass != null) renderer.beginRendering(pass, framebuffer);
    }

    public static void beginHand() {
        afterWorld();
        var mode = config.mode;
        if (context == null || failure != null || config.tuning.upscalerHandAa.samples() <= 1
                || !(mode.usesDedicatedUpscalePass())) return;
        if (sampleShading == null) {
            try (var stack = org.lwjgl.system.MemoryStack.stackPush()) {
                var features = org.lwjgl.vulkan.VkPhysicalDeviceFeatures.calloc(stack);
                vkGetPhysicalDeviceFeatures(context.device().getPhysicalDevice(), features);
                sampleShading = features.sampleRateShading();
            }
        }
        if (!sampleShading) return;
        var renderer = Renderer.getInstance();
        renderer.endRenderPass();
        var color = borrow(output, false); var depth = borrow(output, true);
        String key = color.format + ":" + depth.format + ":" + color.width + ":" + color.height + ":" + config.tuning.upscalerHandAa
                + ":" + config.tuning.upscalerHandAlphaToCoverage + ":" + Renderer.getFramesNum();
        if (!key.equals(handKey)) {
            VulkanContext.check(org.betterLostItems.salts_anti_aliasing.vulkanmod.gpu.VulkanContext.waitIdle(context.device()), "Wait before changing hand coverage");
            if (handFrames != null) for (var frame : handFrames) frame.close();
            if (hand != null) hand.close();
            hand = new MultisampleWorld(context, mainFramebuffer(), color, depth,
                    config.tuning.upscalerHandAa.samples(), config.tuning.upscalerHandAlphaToCoverage, true);
            handFrames = new VulkanFrameResources[Renderer.getFramesNum()];
            java.util.Arrays.setAll(handFrames, index -> new VulkanFrameResources());
            handKey = key;
        }
        var frame = handFrames[Renderer.getCurrentFrame()];
        frame.beginAfterFence();
        hand.seedHand(Renderer.getCommandBuffer(), frame, color, depth);
        renderingHand = true;
        resetPipelineCache(renderer);
        renderer.getMainPass().rebindMainTarget();
    }

    public static void endHand() {
        if (!renderingHand) return;
        renderingHand = false;
        var renderer = Renderer.getInstance();
        renderer.endRenderPass();
        borrow(output, false).transition(Renderer.getCommandBuffer(), VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL);
        borrow(output, true).transition(Renderer.getCommandBuffer(), VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL);
        resetPipelineCache(renderer);
        renderer.getMainPass().rebindMainTarget();
        Renderer.resetViewport(); Renderer.resetScissor();
    }

    private static void resetPipelineCache(Renderer renderer) {
        var access = (RendererAccessor) renderer;
        access.salts$boundPipeline(null); access.salts$boundPipelineHandle(0);
    }
    private static Framebuffer mainFramebuffer() {
        return ((org.betterLostItems.salts_anti_aliasing.vulkanmod.mixin.MainPassAccessor) Renderer.getInstance().getMainPass()).salts$framebuffer();
    }

    public static void afterWorld() {
        if (!renderingWorld) return;
        org.betterLostItems.salts_anti_aliasing.vulkanmod.motion.EntityMotionGeometry.endWorldCapture();
        renderingWorld = false;
        var source = redirectedScene != null ? redirectedScene : output;
        redirectedScene = null;
        inputWidth = source.width; inputHeight = source.height;
        Renderer renderer = Renderer.getInstance();
        Framebuffer framebuffer = ((RendererAccessor) renderer).salts$boundFramebuffer();
        RenderPass renderPass = renderer.getBoundRenderPass();
        renderer.endRenderPass();
        try {
            var destination = borrow(output, false);
            var sourceColor = source == output ? destination : borrow(source, false);
            var sourceDepth = borrow(source, true);
            effects.motionGeometry(geometry());
            effects.particleGeometry(particles());
            effects.apply(Renderer.getCommandBuffer(), Renderer.getCurrentFrame(), config, sourceColor, sourceDepth, destination, temporal);
            destination.transition(Renderer.getCommandBuffer(), VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL);
            sourceColor.transition(Renderer.getCommandBuffer(), VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL);
            if (source != output) {
                var depth = borrow(output, true);
                effects.copyDepth(sourceDepth, depth);
                depth.transition(Renderer.getCommandBuffer(), VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL);
            }
            sourceDepth.transition(Renderer.getCommandBuffer(), VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL);
            resolvedFrames++;
        } catch (RuntimeException error) {
            failed(error);
            // The world has already rendered at the input resolution. Keep that frame visible.
            var destination = borrow(output, false);
            var color = source == output ? destination : borrow(source, false);
            if (source != output) VulkanImageOps.blit(Renderer.getCommandBuffer(), color, destination, true);
            color.transition(Renderer.getCommandBuffer(), VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL);
            destination.transition(Renderer.getCommandBuffer(), VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL);
            borrow(source, true).transition(Renderer.getCommandBuffer(), VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL);
        }
        finally {
            var access = (RendererAccessor) renderer;
            access.salts$boundPipeline(null); access.salts$boundPipelineHandle(0);
            renderer.getMainPass().rebindMainTarget();
            Renderer.resetViewport(); Renderer.resetScissor();
        }
    }

    private static java.nio.ByteBuffer geometry() {
        if (!config.mode.usesHistoryBuffers() || temporal.resetHistory()) return null;
        int count = org.betterLostItems.salts_anti_aliasing.vulkanmod.motion.EntityMotionGeometry.forEachMatched((a, b, primitive) -> {});
        if (count == 0) return null;
        int bytes = count * 24;
        if (geometry == null || geometry.capacity() < bytes)
            geometry = java.nio.ByteBuffer.allocate(Integer.highestOneBit(bytes) << 1).order(java.nio.ByteOrder.nativeOrder());
        geometry.clear();
        org.betterLostItems.salts_anti_aliasing.vulkanmod.motion.EntityMotionGeometry.forEachMatched((now, old, primitive) -> {
            int[] order = primitive == 4 ? new int[]{0, 1, 2, 2, 3, 0} : new int[]{0, 1, 2};
            for (int q = 0; q < now.size(); q += primitive * 3) for (int vertex : order) {
                int offset = q + vertex * 3;
                for (int i = 0; i < 3; i++) geometry.putFloat(now.getFloat(offset + i));
                for (int i = 0; i < 3; i++) geometry.putFloat(old.getFloat(offset + i));
            }
        });
        return geometry.flip();
    }

    private static java.util.List<VulkanPostEffects.ParticleBatch> particles() {
        particleQuads = 0;
        if (!config.mode.usesHistoryBuffers() || temporal.resetHistory() || !config.tuning.entityMotion) return java.util.List.of();
        var groups = new java.util.LinkedHashMap<net.minecraft.resources.ResourceLocation, it.unimi.dsi.fastutil.floats.FloatArrayList>();
        org.betterLostItems.salts_anti_aliasing.vulkanmod.motion.ParticleMotionGeometry.forEach((now, old) -> {
            var data = groups.computeIfAbsent(now.layer().textureAtlasLocation(), key -> new it.unimi.dsi.fastutil.floats.FloatArrayList());
            float[] u = {now.u1(), now.u1(), now.u0(), now.u0()}, v = {now.v1(), now.v0(), now.v0(), now.v1()};
            for (int vertex : new int[]{0, 1, 2, 2, 3, 0}) {
                for (int i = 0; i < 3; i++) data.add(now.positions()[vertex * 3 + i]);
                for (int i = 0; i < 3; i++) data.add(old.positions()[vertex * 3 + i]);
                data.add(u[vertex]); data.add(v[vertex]); data.add(now.alpha());
                data.add(now.layer().translucent() ? config.tuning.dlssParticleAlphaThreshold : .1f);
            }
            particleQuads++;
        });
        var batches = new java.util.ArrayList<VulkanPostEffects.ParticleBatch>();
        for (var group : groups.entrySet()) {
            var texture = Minecraft.getInstance().getTextureManager().getTexture(group.getKey());
            var nativeTexture = GlTexture.getTexture(texture.getId());
            var image = nativeTexture.getVulkanImage();
            var atlas = VulkanImageResource.borrow(context, image.getId(), image.getImageView(), image.width, image.height,
                    image.format, image.aspect, 1, image.getCurrentLayout(), image::setCurrentLayout, ((org.betterLostItems.salts_anti_aliasing.vulkanmod.mixin.VulkanImageAccessor) image).salts$usage(), image.mipLevels);
            var vertices = java.nio.ByteBuffer.allocate(group.getValue().size() * 4).order(java.nio.ByteOrder.nativeOrder());
            for (float value : group.getValue()) vertices.putFloat(value);
            batches.add(new VulkanPostEffects.ParticleBatch(atlas, vertices.flip()));
        }
        return batches;
    }

    private static VulkanImageResource borrow(RenderTarget target, boolean depth) {
        // MainTarget's GL ids are aliases and its depth id is never allocated by VulkanMod.
        VulkanImage image = target == output
                ? depth ? mainFramebuffer().getDepthAttachment() : mainFramebuffer().getColorAttachment()
                : GlTexture.getTexture(depth ? target.getDepthTextureId() : target.getColorTextureId()).getVulkanImage();
        context.debugName(VK_OBJECT_TYPE_IMAGE, image.getId(),
                "VulkanMod " + (target == scene ? "AA scene" : "main target") + (depth ? " depth" : " color"));
        return VulkanImageResource.borrow(context, image.getId(), image.getImageView(), image.width, image.height,
                image.format, image.aspect, 1,
                image.getCurrentLayout(), image::setCurrentLayout, ((org.betterLostItems.salts_anti_aliasing.vulkanmod.mixin.VulkanImageAccessor) image).salts$usage() == 0 ? SwapchainBridge.usage(image.getId()) : ((org.betterLostItems.salts_anti_aliasing.vulkanmod.mixin.VulkanImageAccessor) image).salts$usage());
    }

    public static void close() {
        org.betterLostItems.salts_anti_aliasing.client.gui.LegacyCursors.close();
        var runtime = org.betterLostItems.salts_anti_aliasing.client.SaltsAntiAliasingClient.runtimeOrNull();
        if (runtime != null) runtime.shutdownMetrics();
        releaseGraphics();
    }

    private static void releaseGraphics() {
        if (context != null) VulkanContext.check(org.betterLostItems.salts_anti_aliasing.vulkanmod.gpu.VulkanContext.waitIdle(context.device()), "Wait before releasing AA resources");
        if (effects != null) effects.close();
        if (handFrames != null) for (var frame : handFrames) frame.close();
        if (hand != null) hand.close();
        if (opaque != null) opaque.close();
        if (msaa != null) msaa.close();
        MultisampleWorld.clearPasses();
        if (scene != null) scene.destroyBuffers();
        effects = null; scene = null; output = redirectedScene = null; context = null;
        msaa = null; msaaKey = null;
        hand = null; handKey = null; handFrames = null; sampleShading = null;
        opaque = null;
        nativeUpscalers = null;
        renderingWorld = renderingHand = false;
    }
}
