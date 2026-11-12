package org.betterLostItems.salts_anti_aliasing.client.render.vulkan;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.resource.CrossFrameResourcePool;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.*;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.shaders.*;
import com.mojang.blaze3d.textures.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.resources.Identifier;
import org.betterLostItems.salts_anti_aliasing.SaltsAntiAliasing;
import org.betterLostItems.salts_anti_aliasing.client.config.*;
import org.betterLostItems.salts_anti_aliasing.client.metrics.MetricsDiagnostics;
import org.betterLostItems.salts_anti_aliasing.client.render.common.RenderingHealth;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryStack;
import java.nio.ByteBuffer;
import java.util.*;

/** Qualcomm's portable spatial filter and recommended SGSR 2 two-fragment-pass path. */
public final class VulkanSceneSgsrController {
    private static final VulkanSceneSgsrController INSTANCE = new VulkanSceneSgsrController();
    private static final Vector4f ZERO = new Vector4f();
    private final CrossFrameResourcePool preAaPool = new CrossFrameResourcePool(3);
    private final Map<String, RenderPipeline> pipelines = new HashMap<>();
    private TextureTarget scene, linearInput, spatialOutput, velocity, motionDepth;
    private final TextureTarget[] history = new TextureTarget[2];
    private RenderTarget main;
    private GpuBuffer spatialConfig, temporalConfig;
    private boolean active, disabled, resetPending = true;
    private int readHistory, stillFrames;
    private String lastConfiguration;
    private AntiAliasingMode mode = AntiAliasingMode.OFF;

    private VulkanSceneSgsrController() {}
    public static VulkanSceneSgsrController instance() { return INSTANCE; }
    public void retry() { disabled = false; resetPending = true; stillFrames = 0; }
    public int activeJitterPhaseCount() { return active && mode.usesSgsr2QualityControl() ? SgsrTemporalParameters.JITTER_PHASES : 0; }
    public RenderTarget motionTarget() { return motionDepth; }
    public long allocatedTargetBytes() { return VulkanOwnedTargetMemory.bytes(scene,linearInput,spatialOutput,velocity,motionDepth,history[0],history[1]); }

    public void beginSceneRendering(GameRenderer renderer, AntiAliasingConfig config) {
        RenderSystem.assertOnRenderThread();
        clearFrameState();
        if (!config.mode.usesSgsr()) { destroyResources(); return; }
        mode = config.mode;
        RenderTarget destination = renderer.mainRenderTarget();
        String signature = mode + ":" + config.sgsr1QualityPreset + ":" + config.sgsr2QualityPreset
                + ":" + destination.width + "x" + destination.height + ":" + config.tuning.sgsr1EdgeDirection
                + ":" + config.tuning.sgsr1EdgeThreshold + ":" + config.tuning.sgsr1EdgeSharpness;
        if (!signature.equals(lastConfiguration)) { retry(); lastConfiguration = signature; }
        if (disabled || Minecraft.getInstance().level == null || !destination.useDepth) return;
        try {
            if (destination.getColorTexture().getFormat() != GpuFormat.RGBA8_UNORM)
                throw new IllegalStateException("SGSR currently requires the standard SDR scene format");
            SgsrQualityPreset quality = mode.usesSgsr2QualityControl() ? config.sgsr2QualityPreset : config.sgsr1QualityPreset;
            ensureTargets(quality.width(destination.width), quality.width(destination.height), destination.width, destination.height);
            main = destination;
            active = true;
        } catch (RuntimeException e) { destroyTargets(); fail("SGSR scene setup failed", e); }
    }

    public void endSceneRendering(GameRenderer renderer, AntiAliasingConfig config) {
        RenderSystem.assertOnRenderThread();
        if (!active) return;
        active = false; // Resolve into the real main target, outside scene redirection.
        try {
            if (mode.usesSgsr2QualityControl() && !temporalResolve()) return;
            if (!mode.usesSgsr2QualityControl()) spatialResolve(config);
            RenderingHealth.success(mode, scene.width, scene.height);
        } catch (RuntimeException e) {
            try { VulkanColorBlitter.blitColor(scene,main); } catch (RuntimeException fallback) { e.addSuppressed(fallback); }
            fail("SGSR resolve failed",e);
        } finally { preAaPool.endFrame(); clearFrameState(); }
    }

    private void spatialResolve(AntiAliasingConfig config) {
        if (config.tuning.spatialAa != RenderTuning.SpatialAa.OFF) {
            String aa = config.tuning.spatialAa == RenderTuning.SpatialAa.SMAA ? "smaa" : "fxaa";
            PostChain pre = Minecraft.getInstance().getShaderManager().getPostChain(id(aa),Set.of(PostChain.MAIN_TARGET_ID));
            if (pre == null) throw new IllegalStateException("SGSR 1 pre-AA shader unavailable");
            VulkanDynamicUniforms.updateTuning(pre,config);
            try(var timing=VulkanGpuProfiler.scope("SGSR1.pre_"+aa)) { pre.process(scene,preAaPool); }
        }
        // Qualcomm's Vulkan sample samples an sRGB view, which supplies linear RGB to its filter.
        VulkanFsrColorTransferRenderer.instance().decode(scene,linearInput);
        try (var stack = MemoryStack.stackPush()) {
            var data = stack.malloc(32);
            data.putFloat(1f/scene.width).putFloat(1f/scene.height).putFloat(scene.width).putFloat(scene.height);
            data.putFloat(config.tuning.sgsr1EdgeThreshold).putFloat(config.tuning.sgsr1EdgeSharpness).putFloat(0).putFloat(0).flip();
            spatialConfig = upload(spatialConfig,data,"SGSR 1 Config");
        }
        String shader = config.tuning.sgsr1EdgeDirection ? "sgsr1_edge" : "sgsr1_standard";
        try(var timing=VulkanGpuProfiler.scope("SGSR1.filter")) {
            draw(shader,spatialOutput,"Sgsr1Config",spatialConfig,Map.of("ps0",linearInput));
        }
        VulkanFsrColorTransferRenderer.instance().encode(spatialOutput,main);
        MetricsDiagnostics.infoOnce("sgsr1.active."+shader+scene.width+"x"+scene.height,
                "sgsr.controller","spatial_filter_succeeded","shader",shader,"width",scene.width,"height",scene.height);
    }

    private boolean temporalResolve() {
        var temporal = VulkanSceneTemporalController.instance();
        if (!temporal.hasValidFrameMatrices()) {
            VulkanColorBlitter.blitColor(scene,main); resetPending = true; return false;
        }
        VulkanFsrColorTransferRenderer.instance().decode(scene,linearInput);
        // Zero is the official static-surface sentinel; SGSR reconstructs camera motion from depth.
        RenderSystem.getDevice().createCommandEncoder().clearColorTexture(velocity.getColorTexture(),ZERO);
        try(var timing=VulkanGpuProfiler.scope("SGSR2.object_motion")) {
            VulkanEntityMotionRenderer.render(scene,velocity,temporal);
            VulkanParticleMotionRenderer.render(scene,velocity,temporal);
        }
        writeTemporalConfig(temporal);
        int writeHistory = readHistory ^ 1;
        try(var timing=VulkanGpuProfiler.scope("SGSR2.convert")) {
            draw("sgsr2_convert",motionDepth,"Params",temporalConfig,
                    Map.of("InputDepth",scene,"InputVelocity",velocity));
        }
        try(var timing=VulkanGpuProfiler.scope("SGSR2.upscale")) {
            draw("sgsr2_upscale",history[writeHistory],"Params",temporalConfig,
                    Map.of("PrevOutput",history[readHistory],"MotionDepthClipAlphaBuffer",motionDepth,"InputColor",linearInput));
        }
        VulkanFsrColorTransferRenderer.instance().encode(history[writeHistory],main);
        readHistory = writeHistory;
        resetPending = false;
        MetricsDiagnostics.infoOnce("sgsr2.active."+scene.width+"x"+scene.height+":"+main.width+"x"+main.height,
                "sgsr.controller","temporal_reconstruction_succeeded","width",scene.width,"height",scene.height,
                "outputWidth",main.width,"outputHeight",main.height,"variant","2-pass-fs");
        return true;
    }

    private void writeTemporalConfig(VulkanSceneTemporalController temporal) {
        float[] current = temporal.currentViewProjectionArray(), previous = temporal.previousViewProjectionArray();
        boolean reset = resetPending || temporal.resetHistoryThisFrame();
        boolean still = !reset && SgsrTemporalParameters.cameraStill(current,previous);
        stillFrames = still ? stillFrames+1 : 0;
        float[] reprojection = SgsrTemporalParameters.clipToPrevious(current,previous).get(new float[16]);
        try (var stack = MemoryStack.stackPush()) {
            ByteBuffer data = stack.malloc(SgsrTemporalParameters.UNIFORM_BYTES);
            for (float v : reprojection) data.putFloat(v);
            data.putFloat(scene.width).putFloat(scene.height).putFloat(main.width).putFloat(main.height);
            data.putFloat(1f/scene.width).putFloat(1f/scene.height).putFloat(1f/main.width).putFloat(1f/main.height);
            data.putFloat(temporal.currentJitterTexelX()).putFloat(-temporal.currentJitterTexelY());
            data.putFloat((float)main.width/scene.width).putFloat(SgsrTemporalParameters.varianceScale(scene.width,scene.height,main.width,main.height));
            data.putFloat((float)Math.tan(temporal.cameraFovY()*.5)*scene.width/scene.height);
            data.putFloat(stillFrames>5 ? .3f : 0f).putFloat(reset ? 1f : 0f).putInt(still ? 1 : 0).flip();
            temporalConfig = upload(temporalConfig,data,"SGSR 2 Params");
        }
    }

    private void draw(String shader, RenderTarget target, String block, GpuBuffer buffer, Map<String,? extends RenderTarget> inputs) {
        RenderPipeline pipeline = pipelines.get(shader);
        if (pipeline == null) {
            var layout = BindGroupLayout.builder().withUniform(block,UniformType.UNIFORM_BUFFER);
            // Deterministic binding order; sampler declarations are resolved by name by Blaze3D.
            for (String name : new TreeSet<>(inputs.keySet())) layout.withSampler(name);
            pipeline = RenderPipeline.builder(RenderPipelines.POST_PROCESSING_SNIPPET)
                    .withLocation(id("pipeline/"+shader)).withVertexShader(Identifier.withDefaultNamespace("core/screenquad"))
                    .withFragmentShader(id("post/"+shader)).withBindGroupLayout(layout.build())
                    .withColorTargetState(new ColorTargetState(Optional.empty(),GpuFormat.RGBA16_FLOAT,ColorTargetState.WRITE_ALL))
                    .withPrimitiveTopology(PrimitiveTopology.TRIANGLES).build();
            if (!RenderSystem.getDevice().precompilePipeline(pipeline).isValid()) throw new IllegalStateException("Unable to compile "+shader);
            pipelines.put(shader,pipeline);
        }
        try (var pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                () -> "Salt's "+shader,target.getColorTextureView(),Optional.empty())) {
            pass.setPipeline(pipeline);
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform(block,buffer);
            for (var input : inputs.entrySet()) {
                boolean depth = input.getKey().equals("InputDepth");
                boolean linear = input.getKey().equals("PrevOutput") || input.getKey().equals("ps0");
                pass.bindTexture(input.getKey(),depth ? input.getValue().getDepthTextureView() : input.getValue().getColorTextureView(),
                        RenderSystem.getSamplerCache().getClampToEdge(linear ? FilterMode.LINEAR : FilterMode.NEAREST));
            }
            pass.draw(3,1,0,0);
        }
    }

    private void ensureTargets(int width, int height, int outputWidth, int outputHeight) {
        boolean temporal = mode.usesSgsr2QualityControl();
        if (scene != null && (scene.width!=width || scene.height!=height
                || (history[0]!=null && (history[0].width!=outputWidth || history[0].height!=outputHeight))
                || (velocity!=null)!=temporal || (spatialOutput!=null && (spatialOutput.width!=outputWidth || spatialOutput.height!=outputHeight))))
            destroyTargets();
        if (scene != null) return;
        scene = new TextureTarget("Salt's SGSR Scene", width, height, true, GpuFormat.RGBA8_UNORM);
        linearInput = new TextureTarget("Salt's SGSR Linear Input", width, height, false, GpuFormat.RGBA16_FLOAT);
        if (temporal) {
            velocity = new TextureTarget("Salt's SGSR Dynamic Velocities", width, height, true, GpuFormat.RGBA16_FLOAT);
            motionDepth = new TextureTarget("Salt's SGSR Motion Depth", width, height, false, GpuFormat.RGBA16_FLOAT);
            for (int i=0;i<2;i++) {
                history[i] = new TextureTarget("Salt's SGSR History "+i, outputWidth, outputHeight, false, GpuFormat.RGBA16_FLOAT);
                RenderSystem.getDevice().createCommandEncoder().clearColorTexture(history[i].getColorTexture(),ZERO);
            }
        } else spatialOutput = new TextureTarget("Salt's SGSR Spatial Output", outputWidth, outputHeight, false, GpuFormat.RGBA16_FLOAT);
        resetPending = true; readHistory = stillFrames = 0;
    }

    private static GpuBuffer upload(GpuBuffer buffer, ByteBuffer data, String label) {
        if (buffer == null) return RenderSystem.getDevice().createBuffer(() -> label,GpuBuffer.USAGE_UNIFORM|GpuBuffer.USAGE_COPY_DST,data);
        RenderSystem.getDevice().createCommandEncoder().writeToBuffer(buffer.slice(),data); return buffer;
    }
    private void fail(String message, RuntimeException e) {
        disabled = true; resetPending = true;
        RenderingHealth.failure(mode,e.getMessage());
        MetricsDiagnostics.error("sgsr.controller","renderer_disabled",e,"mode",mode,"message",message);
        SaltsAntiAliasing.LOGGER.error(message,e);
        clearFrameState();
    }
    private void destroyTargets() {
        if (velocity!=null) VulkanParticleMotionRenderer.release();
        for (var target : new RenderTarget[]{scene,linearInput,spatialOutput,velocity,motionDepth,history[0],history[1]})
            if (target!=null) target.destroyBuffers();
        scene=linearInput=spatialOutput=velocity=motionDepth=history[0]=history[1]=null;
        resetPending=true; readHistory=stillFrames=0;
    }
    private void destroyResources() {
        destroyTargets(); preAaPool.clear();
        if (spatialConfig!=null) spatialConfig.close();
        if (temporalConfig!=null) temporalConfig.close();
        spatialConfig=temporalConfig=null;
    }
    public void release() { clearFrameState(); destroyResources(); }
    private void clearFrameState() { active=false; main=null; }
    private static Identifier id(String name) { return Identifier.parse("salts_anti_aliasing:"+name); }

    public RenderTarget overrideMainTarget() { return active ? scene : null; }
    private RenderTarget mapped(RenderTarget target) { return active && target==main ? scene : null; }
    public GpuTexture overrideColorTexture(RenderTarget target) { var t=mapped(target); return t==null ? null : t.getColorTexture(); }
    public GpuTextureView overrideColorTextureView(RenderTarget target) { var t=mapped(target); return t==null ? null : t.getColorTextureView(); }
    public GpuTexture overrideDepthTexture(RenderTarget target) { var t=mapped(target); return t==null ? null : t.getDepthTexture(); }
    public GpuTextureView overrideDepthTextureView(RenderTarget target) { var t=mapped(target); return t==null ? null : t.getDepthTextureView(); }
    public boolean redirectCopyDepth(RenderTarget target, RenderTarget source) {
        RenderTarget a=mapped(target), b=mapped(source);
        if(a==null && b==null) return false;
        a=a==null?target:a; b=b==null?source:b;
        if(a!=b && a.getDepthTexture()!=null && b.getDepthTexture()!=null) a.copyDepthFrom(b);
        return true;
    }
}
