package org.betterLostItems.salts_anti_aliasing.vulkanmod.gpu;

import org.betterLostItems.salts_anti_aliasing.client.config.AntiAliasingConfig;
import org.betterLostItems.salts_anti_aliasing.client.config.AntiAliasingMode;
import org.betterLostItems.salts_anti_aliasing.client.config.RenderTuning;
import org.lwjgl.vulkan.VkCommandBuffer;

import javax.imageio.ImageIO;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.*;

import static org.lwjgl.vulkan.VK10.*;

/** Executes the unchanged spatial algorithms with VulkanMod-owned scene inputs. */
public final class VulkanPostEffects implements AutoCloseable {
    private final VulkanContext context;
    private final VulkanFrameResources[] frames;
    private final Map<String, VulkanGraphicsPass> pipelines = new LinkedHashMap<>();
    private final Map<String, VulkanImageResource> targets = new LinkedHashMap<>();
    private String dimensions;
    private VkCommandBuffer commands;
    private VulkanFrameResources frame;
    private AntiAliasingConfig config;
    private TemporalView temporal;
    private boolean historyInitialized;
    private boolean sgsrHistoryInitialized;
    private int readSgsrHistory, stillFrames, outputWidth, outputHeight, outputFormat;
    private VendorUpscaler vendor;
    private VulkanImageResource opaque;
    private boolean masksInitialized;
    private ByteBuffer motionGeometry;
    public record ParticleBatch(VulkanImageResource atlas, ByteBuffer vertices) {}
    private List<ParticleBatch> particles = List.of();
    private VulkanImageResource debugMotion, debugReactive;
    public record DebugSample(AntiAliasingMode mode, int width, int height, ByteBuffer rgba) {}
    private java.util.function.Consumer<DebugSample> debugConsumer;
    private final DebugReadback[] debugReadbacks;
    private int debugFrames;
    private static final class DebugReadback implements AutoCloseable {
        VulkanHostBuffer buffer; int width, height; AntiAliasingMode mode; boolean pending;
        public void close() { if (buffer != null) buffer.close(); buffer = null; pending = false; }
    }

    public void vendor(VendorUpscaler vendor) { this.vendor = vendor; }
    public int framesInFlight() { return frames.length; }
    public void opaque(VulkanImageResource image) { opaque = image; }
    public void motionGeometry(ByteBuffer vertices) { motionGeometry = vertices; }
    public void particleGeometry(List<ParticleBatch> particles) { this.particles = List.copyOf(particles); }
    public void debugSamples(java.util.function.Consumer<DebugSample> consumer) { debugConsumer = consumer; }

    public VulkanPostEffects(VulkanContext context, int framesInFlight) {
        this.context = context;
        frames = new VulkanFrameResources[framesInFlight];
        Arrays.setAll(frames, index -> new VulkanFrameResources());
        debugReadbacks = new DebugReadback[framesInFlight];
        Arrays.setAll(debugReadbacks, index -> new DebugReadback());
    }

    public void apply(VkCommandBuffer commands, int frameIndex, AntiAliasingConfig config,
                      VulkanImageResource scene, VulkanImageResource sceneDepth, VulkanImageResource destination,
                      TemporalView temporal) {
        String next = scene.width + ":" + scene.height + ":" + destination.width + ":" + destination.height;
        if (!next.equals(dimensions)) { releaseTargets(); dimensions = next; }
        this.commands = commands; this.config = config; this.frame = frames[frameIndex]; this.temporal = temporal;
        outputWidth = destination.width; outputHeight = destination.height;
        outputFormat = destination.format;
        frame.beginAfterFence();
        debugMotion = debugReactive = null;
        var readback = debugReadbacks[frameIndex];
        if (readback.pending) {
            if (debugConsumer != null) debugConsumer.accept(new DebugSample(readback.mode, readback.width, readback.height, readback.buffer.read()));
            readback.pending = false;
        }
        VulkanImageResource color = scene;
        switch (config.mode.selectionMode()) {
            case OFF, MSAA -> {}
            case FXAA -> color = fxaa(color, "fxaa");
            case SMAA -> color = smaa(color, "smaa");
            case SSAA -> color = draw("ssaa_resolve", "ssaa", destination.width, destination.height,
                    Map.of("InSampler", sample(color, true)));
            case NIS_UPSCALE, FSR1_UPSCALE -> {
                color = preAa(color);
                color = draw(config.mode == AntiAliasingMode.NIS_UPSCALE ? "nis_upscale" : "fsr1_upscale",
                        "spatial", destination.width, destination.height, Map.of("InSampler", sample(color, true)));
            }
            case SGSR1_UPSCALE -> {
                color = preAa(color);
                color = draw("fsr_srgb_to_linear", "sgsr1_linear", color.width, color.height,
                        Map.of("InputSampler", sample(color, false)));
                color = draw(config.tuning.sgsr1EdgeDirection ? "sgsr1_edge" : "sgsr1_standard",
                        "sgsr1", destination.width, destination.height, Map.of("ps0", sample(color, true)));
                color = draw("fsr_linear_to_srgb", "sgsr1_display", color.width, color.height,
                        Map.of("InputSampler", sample(color, false)));
            }
            case TAA -> color = taa(color, sceneDepth);
            case SGSR2_SUPER_RESOLUTION -> color = sgsr2(color, sceneDepth);
            case DLSS_SUPER_RESOLUTION, XESS_SUPER_RESOLUTION, FSR2_SUPER_RESOLUTION, FSR3_SUPER_RESOLUTION, FSR3_SUPER_RESOLUTION_FRAME_GENERATION ->
                    color = nativeUpscale(color, sceneDepth);
            default -> throw new IllegalStateException("Temporal/native mode is not connected yet: " + config.mode);
        }
        if (config.sharpenStrength > 0 && !config.mode.usesNativeFsrSharpening()) color = draw("nis_sharpen", "sharpen", color.width, color.height,
                Map.of("InSampler", sample(color, true)));
        if (config.tuning.debugView == RenderTuning.DebugView.MOTION_VECTORS && debugMotion != null)
            color = temporalDebug(debugMotion, true);
        else if (config.tuning.debugView == RenderTuning.DebugView.REACTIVE_MASK && debugReactive != null)
            color = temporalDebug(debugReactive, false);
        if (config.debugViewsEnabled) {
            if (++debugFrames % 12 == 0) captureDebug(color, readback);
            color = draw("edge_debug", "debug_edges", outputWidth, outputHeight,
                    Map.of("InSampler", sample(color, true), "DepthSampler", sample(sceneDepth, false)));
        }
        if (color.image != destination.image) VulkanImageOps.blit(commands, color, destination, false);
        if (config.mode.usesHistoryBuffers()) temporal.resolved();
    }

    private VulkanImageResource taa(VulkanImageResource color, VulkanImageResource sourceDepth) {
        if (temporal == null || !temporal.captured()) throw new IllegalStateException("No valid temporal camera capture");
        var depth = canonicalDepth(sourceDepth);
        var history = target("taa_history_color", color.width, color.height, VK_FORMAT_R16G16B16A16_SFLOAT, VK_IMAGE_ASPECT_COLOR_BIT);
        var historyDepth = target("taa_history_depth", color.width, color.height, VK_FORMAT_D32_SFLOAT, VK_IMAGE_ASPECT_DEPTH_BIT);
        if (!historyInitialized) {
            history.clear(commands, 0, 0, 0, 0); historyDepth.clearDepth(commands, 0);
            historyInitialized = true;
        }
        var motion = draw("dlss_motion_vectors", "taa_motion", color.width, color.height,
                Map.of("SceneDepthSampler", sample(depth, false)));
        overlayMotion(depth, motion, false);
        debugMotion = motion;
        var resolved = draw("taa_resolve", "taa_resolve", color.width, color.height,
                Map.of("CurrentSampler", sample(color, true), "CurrentDepthSampler", sample(depth, false),
                        "MotionSampler", sample(motion, false), "HistorySampler", sample(history, true),
                        "HistoryDepthSampler", sample(historyDepth, false)));
        VulkanImageOps.blit(commands, resolved, history, false);
        VulkanImageOps.blit(commands, depth, historyDepth, false);
        return draw("taa_display", "taa_display", color.width, color.height, Map.of("InSampler", sample(resolved, true)));
    }

    private VulkanImageResource canonicalDepth(VulkanImageResource source) {
        var depth = target("canonical_depth", source.width, source.height, VK_FORMAT_D32_SFLOAT, VK_IMAGE_ASPECT_DEPTH_BIT);
        pipelines.computeIfAbsent("reverse_depth", key -> VulkanGraphicsPass.depth(context, "vulkanmod_reverse_depth", depth.format))
                .draw(commands, frame, List.of(depth), Map.of("InputSampler", sample(source, false)),
                        Map.of("DepthNormalization", floats(temporal == null ? 1 : temporal.depthScale(), 0, 0, 0)));
        return depth;
    }

    private VulkanImageResource nativeUpscale(VulkanImageResource color, VulkanImageResource sourceDepth) {
        if (vendor == null || temporal == null || !temporal.captured()) throw new IllegalStateException("Native backend/camera is unavailable");
        var depth = canonicalDepth(sourceDepth);
        var motion = draw("dlss_motion_vectors", "native_motion", color.width, color.height,
                Map.of("SceneDepthSampler", sample(depth, false)), VK_FORMAT_R16G16_SFLOAT);
        overlayMotion(depth, motion, false);
        debugMotion = motion;
        boolean linear = config.mode != AntiAliasingMode.DLSS_SUPER_RESOLUTION || config.tuning.dlssColorInput.linear(false);
        var input = linear ? draw("fsr_srgb_to_linear", "native_linear_input", color.width, color.height,
                Map.of("InputSampler", sample(color, false))) : color;
        var output = target(linear ? "native_linear_output" : "native_sdr_output", outputWidth, outputHeight,
                linear ? VK_FORMAT_R16G16B16A16_SFLOAT : VK_FORMAT_R8G8B8A8_UNORM, VK_IMAGE_ASPECT_COLOR_BIT);
        var reactive = target("native_reactive", color.width, color.height, VK_FORMAT_R8_UNORM, VK_IMAGE_ASPECT_COLOR_BIT);
        var transparency = target("native_transparency", color.width, color.height, VK_FORMAT_R8_UNORM, VK_IMAGE_ASPECT_COLOR_BIT);
        var hudless = target("native_hudless", outputWidth, outputHeight, VK_FORMAT_R8G8B8A8_UNORM, VK_IMAGE_ASPECT_COLOR_BIT);
        var presentationDepth = depth;
        var presentationMotion = motion;
        if (config.mode.usesFsrFrameGeneration()) {
            presentationDepth = target("fg_depth", depth.width, depth.height, depth.format, VK_IMAGE_ASPECT_DEPTH_BIT);
            presentationMotion = target("fg_motion", motion.width, motion.height, motion.format, VK_IMAGE_ASPECT_COLOR_BIT);
            VulkanImageOps.blit(commands, depth, presentationDepth, false);
            VulkanImageOps.blit(commands, motion, presentationMotion, false);
            presentationDepth.synchronize(commands, VK_IMAGE_LAYOUT_GENERAL);
            presentationMotion.synchronize(commands, VK_IMAGE_LAYOUT_GENERAL);
        }
        VulkanImageResource opaqueInput = input;
        if (config.mode.usesFsrQualityControl()) {
            if (opaque == null) throw new IllegalStateException("FSR opaque scene capture did not run");
            opaqueInput = linear ? draw("fsr_srgb_to_linear", "native_opaque_linear", color.width, color.height,
                    Map.of("InputSampler", sample(opaque, false))) : opaque;
            var previousColor = target("fsr_previous_color", input.width, input.height, input.format, VK_IMAGE_ASPECT_COLOR_BIT);
            var previousOpaque = target("fsr_previous_opaque", input.width, input.height, input.format, VK_IMAGE_ASPECT_COLOR_BIT);
            if (!masksInitialized || temporal.resetHistory()) {
                VulkanImageOps.blit(commands, input, previousColor, false);
                VulkanImageOps.blit(commands, opaqueInput, previousOpaque, false);
                masksInitialized = true;
            }
            pipelines.computeIfAbsent("fsr_temporal_masks", name -> new VulkanGraphicsPass(context, name, VK_FORMAT_R8_UNORM, VK_FORMAT_R8_UNORM))
                    .draw(commands, frame, List.of(reactive, transparency), Map.of(
                            "CurrentColorSampler", sample(input, false), "CurrentOpaqueSampler", sample(opaqueInput, false),
                            "PreviousColorSampler", sample(previousColor, true), "PreviousOpaqueSampler", sample(previousOpaque, true),
                            "MotionVectorSampler", sample(motion, false)),
                            Map.of("FsrTemporalMaskConfig", temporal.maskUniforms(config)));
            VulkanImageOps.blit(commands, input, previousColor, false);
            VulkanImageOps.blit(commands, opaqueInput, previousOpaque, false);
            debugReactive = reactive;
        } else {
            reactive.clear(commands, 0, 0, 0, 0); transparency.clear(commands, 0, 0, 0, 0);
        }
        nativeSdkBarrier();
        vendor.evaluate(new VendorUpscaler.Frame(context, commands, config, temporal, input, depth, motion, output,
                opaqueInput, reactive, transparency, hudless, presentationDepth, presentationMotion));
        nativeSdkBarrier();
        var display = linear ? draw("fsr_linear_to_srgb", "native_display", outputWidth, outputHeight,
                Map.of("InputSampler", sample(output, false))) : output;
        if (config.mode.usesFsrFrameGeneration()) {
            VulkanImageOps.blit(commands, display, hudless, false);
            hudless.synchronize(commands, VK_IMAGE_LAYOUT_GENERAL);
        }
        return display;
    }

    /** Vendor contexts reuse private buffers as well as the public input/output images. */
    private void nativeSdkBarrier() {
        try (var stack = org.lwjgl.system.MemoryStack.stackPush()) {
            var barrier = org.lwjgl.vulkan.VkMemoryBarrier.calloc(1, stack);
            barrier.get(0).sType$Default().srcAccessMask(VK_ACCESS_MEMORY_READ_BIT | VK_ACCESS_MEMORY_WRITE_BIT)
                    .dstAccessMask(VK_ACCESS_MEMORY_READ_BIT | VK_ACCESS_MEMORY_WRITE_BIT);
            vkCmdPipelineBarrier(commands, VK_PIPELINE_STAGE_ALL_COMMANDS_BIT, VK_PIPELINE_STAGE_ALL_COMMANDS_BIT,
                    0, barrier, null, null);
        }
    }

    private VulkanImageResource sgsr2(VulkanImageResource color, VulkanImageResource sourceDepth) {
        if (temporal == null || !temporal.captured()) throw new IllegalStateException("No valid SGSR camera capture");
        var depth = canonicalDepth(sourceDepth);
        var velocity = target("sgsr_velocity", color.width, color.height, VK_FORMAT_R16G16B16A16_SFLOAT, VK_IMAGE_ASPECT_COLOR_BIT);
        velocity.clear(commands, 0, 0, 0, 0);
        overlayMotion(depth, velocity, true);
        if (config.tuning.debugView == RenderTuning.DebugView.MOTION_VECTORS) {
            debugMotion = draw("dlss_motion_vectors", "sgsr_debug_motion", color.width, color.height,
                    Map.of("SceneDepthSampler", sample(depth, false)), VK_FORMAT_R16G16_SFLOAT);
            overlayMotion(depth, debugMotion, false);
        }
        var historyA = target("sgsr_history_0", outputWidth, outputHeight, VK_FORMAT_R16G16B16A16_SFLOAT, VK_IMAGE_ASPECT_COLOR_BIT);
        var historyB = target("sgsr_history_1", outputWidth, outputHeight, VK_FORMAT_R16G16B16A16_SFLOAT, VK_IMAGE_ASPECT_COLOR_BIT);
        if (!sgsrHistoryInitialized) {
            historyA.clear(commands, 0, 0, 0, 0); historyB.clear(commands, 0, 0, 0, 0);
            sgsrHistoryInitialized = true;
        }
        stillFrames = temporal.cameraStill() ? stillFrames + 1 : 0;
        var linear = draw("fsr_srgb_to_linear", "sgsr2_linear", color.width, color.height,
                Map.of("InputSampler", sample(color, false)));
        var motionDepth = draw("sgsr2_convert", "sgsr2_motion_depth", color.width, color.height,
                Map.of("InputDepth", sample(depth, false), "InputVelocity", sample(velocity, false)));
        var read = readSgsrHistory == 0 ? historyA : historyB;
        var write = readSgsrHistory == 0 ? historyB : historyA;
        var pass = pipelines.computeIfAbsent("sgsr2_upscale", name -> new VulkanGraphicsPass(context, name, write.format));
        pass.draw(commands, frame, List.of(write),
                Map.of("PrevOutput", sample(read, true), "MotionDepthClipAlphaBuffer", sample(motionDepth, false), "InputColor", sample(linear, false)),
                Map.of("Params", temporal.sgsrUniforms(outputWidth, outputHeight, stillFrames)));
        readSgsrHistory ^= 1;
        return draw("fsr_linear_to_srgb", "sgsr2_display", outputWidth, outputHeight, Map.of("InputSampler", sample(write, false)));
    }

    private VulkanImageResource preAa(VulkanImageResource source) {
        return switch (config.tuning.spatialAa) {
            case OFF -> source;
            case FXAA -> fxaa(source, "pre_fxaa");
            case SMAA -> smaa(source, "pre_smaa");
        };
    }

    private void overlayMotion(VulkanImageResource depth, VulkanImageResource motion, boolean sgsr) {
        if (temporal.resetHistory() || !config.tuning.entityMotion) return;
        if (motionGeometry != null && motionGeometry.hasRemaining()) pipelines.computeIfAbsent("geometry:" + motion.format + ":" + sgsr,
                        name -> VulkanGraphicsPass.motion(context, motion.format, sgsr))
                .draw(commands, frame, List.of(motion), Map.of("SceneDepthSampler", sample(depth, false)),
                        Map.of("EntityMotionConfig", temporal.geometryUniforms()), motionGeometry);
        if (particles.isEmpty()) return;
        var particleDepth = target("particle_motion_depth", depth.width, depth.height, VK_FORMAT_D32_SFLOAT, VK_IMAGE_ASPECT_DEPTH_BIT);
        particleDepth.clearDepth(commands, 0);
        var pass = pipelines.computeIfAbsent("particles:" + motion.format + ":" + sgsr,
                name -> VulkanGraphicsPass.particles(context, motion.format, sgsr));
        for (var batch : particles) {
            int previousLayout = batch.atlas().layout();
            pass.draw(commands, frame, List.of(motion, particleDepth), Map.of("SceneDepthSampler", sample(depth, false),
                            "ParticleAtlas", sample(batch.atlas(), true)),
                    Map.of("ParticleMotionConfig", temporal.geometryUniforms()), batch.vertices());
            batch.atlas().transition(commands, previousLayout);
        }
    }

    private VulkanImageResource temporalDebug(VulkanImageResource source, boolean motion) {
        var output = target("debug_temporal", outputWidth, outputHeight, VK_FORMAT_R8G8B8A8_UNORM, VK_IMAGE_ASPECT_COLOR_BIT);
        pipelines.computeIfAbsent("temporal_debug", name -> new VulkanGraphicsPass(context, name, output.format))
                .draw(commands, frame, List.of(output), Map.of("InSampler", sample(source, false)),
                        Map.of("TemporalDebugConfig", floats(motion ? 1 : 0, 0, 1, 1)));
        return output;
    }

    private void captureDebug(VulkanImageResource source, DebugReadback readback) {
        var reduced = target("debug_readback", Math.max(1, outputWidth / 4), Math.max(1, outputHeight / 4),
                VK_FORMAT_R8G8B8A8_UNORM, VK_IMAGE_ASPECT_COLOR_BIT);
        VulkanImageOps.blit(commands, source, reduced, false);
        int bytes = Math.multiplyExact(Math.multiplyExact(reduced.width, reduced.height), 4);
        if (readback.buffer == null) readback.buffer = new VulkanHostBuffer(context, bytes, VK_BUFFER_USAGE_TRANSFER_DST_BIT);
        reduced.transition(commands, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL);
        try (var stack = org.lwjgl.system.MemoryStack.stackPush()) {
            var copy = org.lwjgl.vulkan.VkBufferImageCopy.calloc(1, stack);
            copy.imageSubresource().aspectMask(VK_IMAGE_ASPECT_COLOR_BIT).mipLevel(0).baseArrayLayer(0).layerCount(1);
            copy.imageExtent().set(reduced.width, reduced.height, 1);
            vkCmdCopyImageToBuffer(commands, reduced.image, reduced.layout(), readback.buffer.buffer, copy);
        }
        readback.width = reduced.width; readback.height = reduced.height; readback.mode = config.mode; readback.pending = true;
    }

    public void copyDepth(VulkanImageResource source, VulkanImageResource destination) {
        if (frame == null) throw new IllegalStateException("No post-processing frame is active");
        if (source.format == destination.format && (source.usage & VK_IMAGE_USAGE_TRANSFER_SRC_BIT) != 0
                && (destination.usage & VK_IMAGE_USAGE_TRANSFER_DST_BIT) != 0) {
            VulkanImageOps.blit(commands, source, destination, false);
        } else {
            var pass = pipelines.computeIfAbsent("depth_copy:" + destination.format,
                    key -> VulkanGraphicsPass.depth(context, "vulkanmod_copy_depth", destination.format));
            pass.draw(commands, frame, List.of(destination), Map.of("InputSampler", sample(source, false)), Map.of());
        }
    }

    private VulkanImageResource fxaa(VulkanImageResource source, String label) {
        return draw("fxaa", label, source.width, source.height, Map.of("InSampler", sample(source, false)));
    }

    private VulkanImageResource smaa(VulkanImageResource source, String label) {
        var edges = draw("smaa_edge_detect", label + "_edges", source.width, source.height,
                Map.of("InSampler", sample(source, false)));
        var weights = draw("smaa_weight_blend", label + "_weights", source.width, source.height,
                Map.of("EdgesSampler", sample(edges, true), "AreaSampler", sample(lookup("smaa_area"), true),
                        "SearchSampler", sample(lookup("smaa_search"), true)));
        return draw("smaa_neighborhood_blend", label + "_color", source.width, source.height,
                Map.of("ColorSampler", sample(source, true), "WeightsSampler", sample(weights, true)));
    }

    private VulkanImageResource draw(String shader, String targetName, int width, int height,
                                     Map<String, VulkanGraphicsPass.Sample> inputs) {
        return draw(shader, targetName, width, height, inputs, VK_FORMAT_R16G16B16A16_SFLOAT);
    }

    private VulkanImageResource draw(String shader, String targetName, int width, int height,
                                     Map<String, VulkanGraphicsPass.Sample> inputs, int format) {
        var output = target(targetName, width, height, format, VK_IMAGE_ASPECT_COLOR_BIT);
        var pass = pipelines.computeIfAbsent(shader + ":" + format, name -> new VulkanGraphicsPass(context, shader, output.format));
        var input = java.util.stream.Stream.of("InSampler", "InputSampler", "ps0", "EdgesSampler", "ColorSampler", "SceneDepthSampler", "CurrentSampler", "InputDepth")
                .map(inputs::get).filter(Objects::nonNull).findFirst().orElseThrow().image();
        var tuning = config.tuning;
        var smaa = tuning.smaaQuality;
        Map<String, ByteBuffer> uniforms = new LinkedHashMap<>(Map.of(
                "SamplerInfo", floats(width, height, input.width, input.height),
                "FxaaConfig", shader.equals("edge_debug")
                        ? floats(tuning.fxaaSubpixel, tuning.fxaaEdgeThreshold, tuning.fxaaMinContrast, 6, 1.25f, 3.25f, .26f, 1.85f)
                        : floats(tuning.fxaaSubpixel, tuning.fxaaEdgeThreshold, tuning.fxaaMinContrast, 0),
                "SmaaConfig", floats(tuning.smaaThreshold, switch (smaa) {case LOW -> 4; case MEDIUM -> 8; case HIGH -> 16; case ULTRA -> 32;},
                        switch (smaa) {case LOW, MEDIUM -> 0; case HIGH -> 8; case ULTRA -> 16;}, smaa == RenderTuning.SmaaQuality.LOW || smaa == RenderTuning.SmaaQuality.MEDIUM ? 0 : 25),
                "SsaaConfig", floats(tuning.ssaaLinearLight ? 1 : 0, tuning.ssaaFilter.ordinal(), 0, 0),
                "NisSharpenConfig", floats(config.sharpenStrength, tuning.sharpener.ordinal(), tuning.sharpenLimit, 0),
                "Sgsr1Config", floats(1f / input.width, 1f / input.height, input.width, input.height,
                        tuning.sgsr1EdgeThreshold, tuning.sgsr1EdgeSharpness, 0, 0)));
        if (temporal != null && temporal.captured()) {
            uniforms.put("DlssMotionConfig", temporal.motionUniforms());
            uniforms.put("TaaConfig", temporal.taaUniforms(config));
            uniforms.put("TaaDisplayConfig", floats(tuning.debugView.ordinal(), 0, 0, 0));
            uniforms.put("Params", temporal.sgsrUniforms(outputWidth, outputHeight, stillFrames));
        }
        pass.draw(commands, frame, List.of(output), inputs, uniforms);
        return output;
    }

    private VulkanImageResource target(String name, int width, int height, int format, int aspect) {
        var image = targets.computeIfAbsent(name, key -> {
            var created = VulkanImageResource.create(context, width, height, format, aspect, 1);
            context.debugName(VK_OBJECT_TYPE_IMAGE, created.image, "Salt's AA " + name);
            return created;
        });
        if (image.width != width || image.height != height || image.format != format)
            throw new IllegalStateException("Intermediate target reused with a different shape: " + name);
        return image;
    }

    private VulkanImageResource lookup(String name) {
        var cached = targets.get(name);
        if (cached != null) return cached;
        String path = "/assets/salts_anti_aliasing/textures/effect/" + name + ".png";
        try (var input = VulkanPostEffects.class.getResourceAsStream(path)) {
            if (input == null) throw new IllegalArgumentException("Missing lookup texture " + path);
            var png = ImageIO.read(input);
            if (png == null) throw new IllegalArgumentException("Invalid lookup texture " + path);
            var rgba = ByteBuffer.allocate(png.getWidth() * png.getHeight() * 4);
            for (int y = 0; y < png.getHeight(); y++) for (int x = 0; x < png.getWidth(); x++) {
                int pixel = png.getRGB(x, y);
                rgba.put((byte) (pixel >> 16)).put((byte) (pixel >> 8)).put((byte) pixel).put((byte) (pixel >> 24));
            }
            var image = VulkanImageResource.create(context, png.getWidth(), png.getHeight(), VK_FORMAT_R8G8B8A8_UNORM, VK_IMAGE_ASPECT_COLOR_BIT, 1);
            try { VulkanImageOps.upload(context, commands, frame, image, rgba.flip()); }
            catch (RuntimeException e) { image.close(); throw e; }
            targets.put(name, image);
            return image;
        } catch (IOException e) { throw new IllegalStateException("Cannot decode lookup texture " + path, e); }
    }

    private static VulkanGraphicsPass.Sample sample(VulkanImageResource image, boolean linear) {
        return new VulkanGraphicsPass.Sample(image, linear);
    }

    private static ByteBuffer floats(float... values) {
        var bytes = ByteBuffer.allocate(values.length * 4).order(ByteOrder.nativeOrder());
        for (float value : values) bytes.putFloat(value);
        return bytes.flip();
    }

    private void releaseTargets() {
        VulkanContext.check(org.betterLostItems.salts_anti_aliasing.vulkanmod.gpu.VulkanContext.waitIdle(context.device()), "Wait before resizing post-processing targets");
        if (vendor != null) vendor.retire();
        for (var frame : frames) frame.close();
        for (var readback : debugReadbacks) readback.close();
        for (var image : targets.values()) image.close();
        targets.clear();
        historyInitialized = false;
        sgsrHistoryInitialized = false; readSgsrHistory = stillFrames = 0;
        masksInitialized = false;
    }

    @Override public void close() {
        releaseTargets();
        for (var pipeline : pipelines.values()) pipeline.close();
        pipelines.clear();
    }
}
