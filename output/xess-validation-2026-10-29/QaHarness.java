package salts.qa;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonArray;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import org.betterLostItems.salts_anti_aliasing.client.SaltsAntiAliasingClient;
import org.betterLostItems.salts_anti_aliasing.client.config.*;
import org.betterLostItems.salts_anti_aliasing.client.render.vulkan.VulkanSceneFsrController;
import org.betterLostItems.salts_anti_aliasing.client.render.vulkan.VulkanSceneDlssController;
import org.slf4j.LoggerFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.ByteOrder;

/** Disposable development-only controls; never added to the production mod. */
public final class QaHarness implements ClientModInitializer {
    private long lastId = -1;
    private int ticks;
    private boolean sweep;
    private boolean entityMotion;
    private boolean geometryMotion;
    private boolean chestMotion;
    private boolean probeMask;
    private float yaw;
    private float pitch = 10;
    private float amplitude = 8;
    private float pitchAmplitude;
    private int period = 240;
    private int probeDelay;
    private String probeName;

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
    }

    private void tick(Minecraft minecraft) {
        if (minecraft.player == null || SaltsAntiAliasingClient.runtimeOrNull() == null) return;
        ticks++;
        if (probeDelay > 0 && --probeDelay == 0) probe(minecraft);
        if (ticks % 5 == 0) {
            Path path = minecraft.gameDirectory.toPath().resolve("qa-control.json");
            if (Files.isRegularFile(path)) {
                try {
                    JsonObject request = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
                    long id = request.get("id").getAsLong();
                    if (id != lastId) {
                        apply(minecraft, request);
                        lastId = id;
                        LoggerFactory.getLogger("salts.qa").info("QA request {} applied: {}", id, request);
                    }
                } catch (Exception exception) {
                    LoggerFactory.getLogger("salts.qa").error("QA request failed", exception);
                }
            }
        }
        if (sweep) {
            minecraft.player.setYRot(yaw + amplitude * (float) Math.sin(ticks * Math.PI * 2 / period));
            minecraft.player.setXRot(pitch + pitchAmplitude * (float)Math.sin(ticks*Math.PI*2/period));
        }
        if(chestMotion && ticks%10==0) {
            var server=minecraft.getSingleplayerServer();int open=(ticks/10)%2;
            server.execute(()->server.overworld().blockEvent(new net.minecraft.core.BlockPos(-3,100,5),net.minecraft.world.level.block.Blocks.CHEST,1,open));
        }
        if(geometryMotion && ticks%3==0) {
            var server=minecraft.getSingleplayerServer();double x=2*Math.sin(ticks*Math.PI*2/100);
            server.execute(()->{
                server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),String.format(java.util.Locale.ROOT,"tp @e[tag=salts_qa_geometry,type=minecraft:item] %.6f 101 5",x-2));
                server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),String.format(java.util.Locale.ROOT,"tp @e[tag=salts_qa_geometry,type=minecraft:falling_block] %.6f 102 5",x+2));
            });
        }
        if (entityMotion && ticks % 3 == 0) {
            var server = minecraft.getSingleplayerServer();
            double x = 3 * Math.sin(ticks * Math.PI * 2 / 100);
            server.execute(() -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),
                    String.format(java.util.Locale.ROOT,"tp @e[tag=salts_qa_motion] %.6f 100 5",x)));
        }
    }

    private void apply(Minecraft minecraft, JsonObject request) {
        if(request.has("injectXessFailure")) {
            try {
                var controller=org.betterLostItems.salts_anti_aliasing.client.render.vulkan.VulkanSceneXessController.instance();
                var method=controller.getClass().getDeclaredMethod("disableAfterFailure",String.class,RuntimeException.class);
                method.setAccessible(true);
                method.invoke(controller,"QA injected XeSS controller failure",new IllegalStateException("QA recovery exercise"));
            } catch(ReflectiveOperationException e) { throw new RuntimeException(e); }
        }
        if(request.has("injectDlssFailure")) {
            try {
                var controller=VulkanSceneDlssController.instance();
                var method=controller.getClass().getDeclaredMethod("disableAfterFailure",String.class,RuntimeException.class);
                method.setAccessible(true);
                method.invoke(controller,"QA injected controller failure",new IllegalStateException("QA recovery exercise"));
            } catch(ReflectiveOperationException exception) { throw new RuntimeException(exception); }
        }
        if(request.has("dropdownDown")) {
            for(int k=0;k<request.get("dropdownDown").getAsInt();k++)
                minecraft.gui.screen().keyPressed(new net.minecraft.client.input.KeyEvent(com.mojang.blaze3d.platform.InputConstants.KEY_DOWN,0,0));
        }
        if(request.has("dropdownRow")) {
            try {
                var screen=minecraft.gui.screen();
                var field=screen.getClass().getDeclaredField("dropdownOverlay");field.setAccessible(true);
                var overlay=(net.minecraft.client.gui.components.AbstractWidget)field.get(screen);
                screen.mouseClicked(new net.minecraft.client.input.MouseButtonEvent(overlay.getX()+30,
                    overlay.getY()+11+20*request.get("dropdownRow").getAsInt(),new net.minecraft.client.input.MouseButtonInfo(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT,0)),false);
            } catch(ReflectiveOperationException e) {throw new RuntimeException(e);}
        }
        if(request.has("reload")) minecraft.reloadResourcePacks().whenComplete((v,e)->LoggerFactory.getLogger("salts.qa").info("QA resource reload completed",e));
        if(request.has("colorTest")) ColorTransferCheck.run(minecraft);
        if (request.has("areaTest") && request.get("areaTest").getAsBoolean()) AreaResolveCheck.run(minecraft);
        var runtime = SaltsAntiAliasingClient.runtime();
        if(request.has("flushPresented"))org.betterLostItems.salts_anti_aliasing.client.render.vulkan.fsr.FsrRuntime.instance().flushDiagnosticCapture();
        if (request.has("mode")) runtime.setMode(AntiAliasingMode.valueOf(request.get("mode").getAsString()));
        if(request.has("tuning")) runtime.editSettings(c->{
            var gson=new com.google.gson.Gson();
            JsonObject tuning=gson.toJsonTree(c.tuning).getAsJsonObject();
            request.getAsJsonObject("tuning").entrySet().forEach(e->tuning.add(e.getKey(),e.getValue()));
            c.tuning=gson.fromJson(tuning,RenderTuning.class);
        });
        if(request.has("retry"))runtime.retryRendering();
        if (request.has("sharpness")) runtime.setSharpenStrength(request.get("sharpness").getAsFloat());
        if (request.has("msaa")) runtime.setMsaaSampleLevel(MsaaSampleLevel.valueOf(request.get("msaa").getAsString()));
        if (request.has("atoc")) runtime.setMsaaAlphaToCoverage(request.get("atoc").getAsBoolean());
        if (request.has("ssaa")) runtime.setSsaaScaleLevel(SsaaScaleLevel.valueOf(request.get("ssaa").getAsString()));
        if (request.has("spatialQuality")) runtime.setUpscaleQualityPreset(NisUpscaleQualityPreset.valueOf(request.get("spatialQuality").getAsString()));
        if (request.has("dlssQuality")) runtime.setDlssQualityPreset(DlssQualityPreset.valueOf(request.get("dlssQuality").getAsString()));
        if (request.has("xessQuality")) runtime.setXessQualityPreset(XessQualityPreset.valueOf(request.get("xessQuality").getAsString()));
        if (request.has("fsrQuality")) runtime.setFsrQualityPreset(FsrQualityPreset.valueOf(request.get("fsrQuality").getAsString()));
        if (request.has("improvedTransparency")) minecraft.options.improvedTransparency().set(request.get("improvedTransparency").getAsBoolean());
        minecraft.options.bobView().set(false);
        if (request.has("yaw")) yaw = request.get("yaw").getAsFloat();
        if (request.has("pitch")) pitch = request.get("pitch").getAsFloat();
        if(request.has("pitchAmplitude"))pitchAmplitude=request.get("pitchAmplitude").getAsFloat();
        if (request.has("amplitude")) amplitude = request.get("amplitude").getAsFloat();
        if (request.has("period")) period = request.get("period").getAsInt();
        if (request.has("sweep")) sweep = request.get("sweep").getAsBoolean();
        if(request.has("geometryMotion"))geometryMotion=request.get("geometryMotion").getAsBoolean();
        if(request.has("chestMotion"))chestMotion=request.get("chestMotion").getAsBoolean();
        if (request.has("entityMotion")) entityMotion = request.get("entityMotion").getAsBoolean();
        if (request.has("probeMask")) probeMask = request.get("probeMask").getAsBoolean();
        if (request.has("probe")) {
            probeName = request.get("probe").getAsString();
            probeDelay = request.has("probeDelay") ? request.get("probeDelay").getAsInt() : 40;
        }
        if (!sweep && request.has("yaw")) {
            minecraft.player.setYRot(yaw);
            minecraft.player.setXRot(pitch);
        }
        if(request.has("unload")) { minecraft.disconnectWithSavingScreen(); return; }
        if (request.has("commands")) {
            var server = minecraft.getSingleplayerServer();
            if (server == null) throw new IllegalStateException("QA commands require the sandbox integrated server");
            var commands = request.getAsJsonArray("commands");
            server.execute(() -> commands.forEach(command -> server.getCommands()
                    .performPrefixedCommand(server.createCommandSourceStack(), command.getAsString())));
        }
    }

    private void probe(Minecraft minecraft) {
        try {
            boolean dlss = SaltsAntiAliasingClient.runtime().activeMode() == AntiAliasingMode.DLSS_SUPER_RESOLUTION;
            Object controller = SaltsAntiAliasingClient.runtime().activeMode().usesXessQualityControl() ? org.betterLostItems.salts_anti_aliasing.client.render.vulkan.VulkanSceneXessController.instance() : dlss ? VulkanSceneDlssController.instance() : VulkanSceneFsrController.instance();
            var field = controller.getClass().getDeclaredField(probeMask ? (dlss ? "colorRejectionTarget" : "reactiveMaskTarget") : "motionVectorTarget");
            field.setAccessible(true);
            var target = (RenderTarget) field.get(controller);
            if (target == null) throw new IllegalStateException("No motion target to measure");
            if (probeMask) { probeMask(minecraft, target); return; }
            int width = target.width;
            int height = target.height;
            String name = probeName;
            long requestId = lastId;
            GpuBuffer buffer = RenderSystem.getDevice().createBuffer(() -> "QA motion readback",
                    GpuBuffer.USAGE_COPY_DST | GpuBuffer.USAGE_MAP_READ, (long) width * height * 4);
            var encoder = RenderSystem.getDevice().createCommandEncoder();
            encoder.copyTextureToBuffer(target.getColorTexture(), buffer, 0, () -> {
                try (buffer; var view = buffer.map(true, false)) {
                    var data = view.data().order(ByteOrder.nativeOrder());
                    double maximum = 0;
                    double sum = 0;
                    int nonFinite = 0;
                    JsonArray grid = new JsonArray();
                    for (int pixel = 0; pixel < width * height; pixel++) {
                        float x = Float.float16ToFloat(data.getShort(pixel * 4));
                        float y = Float.float16ToFloat(data.getShort(pixel * 4 + 2));
                        if (!Float.isFinite(x) || !Float.isFinite(y)) nonFinite++;
                        double magnitude = Math.hypot(x, y);
                        maximum = Math.max(maximum, magnitude);
                        sum += magnitude;
                    }
                    for (int gy = 0; gy < 9; gy++) {
                        for (int gx = 0; gx < 16; gx++) {
                            int pixel = ((gy * height / 9 + height / 18) * width + gx * width / 16 + width / 32) * 4;
                            JsonArray velocity = new JsonArray();
                            velocity.add(Float.float16ToFloat(data.getShort(pixel)));
                            velocity.add(Float.float16ToFloat(data.getShort(pixel + 2)));
                            grid.add(velocity);
                        }
                    }
                    JsonObject result = new JsonObject();
                    result.addProperty("mode", SaltsAntiAliasingClient.runtime().activeMode().name());
                    result.addProperty("width", width);
                    result.addProperty("height", height);
                    result.addProperty("meanMotionPixels", sum / (width * height));
                    result.addProperty("maximumMotionPixels", maximum);
                    result.addProperty("nonFinitePixels", nonFinite);
                    result.add("motionGrid", grid);
                    Files.writeString(minecraft.gameDirectory.toPath().resolve("qa-motion-" + name + "-" + requestId + ".json"), result.toString());
                    LoggerFactory.getLogger("salts.qa").info("QA GPU motion {}: mean {}, max {}, nonfinite {}", name, sum / (width * height), maximum, nonFinite);
                } catch (Exception exception) {
                    LoggerFactory.getLogger("salts.qa").error("QA GPU readback failed", exception);
                }
            }, 0);
            encoder.submit();
        } catch (Exception exception) {
            LoggerFactory.getLogger("salts.qa").error("QA motion probe failed", exception);
        }
    }

    private void probeMask(Minecraft minecraft, RenderTarget target) {
        int width = target.width, height = target.height;
        String name = probeName;
        long requestId = lastId;
        boolean halfFormat = target.getColorTexture().getFormat() == com.mojang.renderpearl.api.GpuFormat.R16_FLOAT;
        var buffer = RenderSystem.getDevice().createBuffer(() -> "QA mask readback",
                GpuBuffer.USAGE_COPY_DST | GpuBuffer.USAGE_MAP_READ, (long) width * height * (halfFormat ? 2 : 1));
        var encoder = RenderSystem.getDevice().createCommandEncoder();
        encoder.copyTextureToBuffer(target.getColorTexture(), buffer, 0, () -> {
            try (buffer; var view = buffer.map(true, false)) {
                var data = view.data().order(ByteOrder.nativeOrder());
                double sum = 0, maximum = 0;
                int rejected = 0;
                for (int pixel = 0; pixel < width * height; pixel++) {
                    double value = halfFormat ? Float.float16ToFloat(data.getShort(pixel * 2)) : Byte.toUnsignedInt(data.get(pixel)) / 255.0;
                    sum += value;
                    maximum = Math.max(maximum, value);
                    if (value > 0.1) rejected++;
                }
                var result = new JsonObject();
                result.addProperty("width", width); result.addProperty("height", height);
                result.addProperty("meanMask", sum / (width * height));
                result.addProperty("maxMask", maximum);
                result.addProperty("rejectedFraction", rejected / (double)(width * height));
                Files.writeString(minecraft.gameDirectory.toPath().resolve("qa-mask-" + name + "-" + requestId + ".json"), result.toString());
                LoggerFactory.getLogger("salts.qa").info("QA GPU mask {}: {}", name, result);
            } catch (Exception exception) { LoggerFactory.getLogger("salts.qa").error("QA mask readback failed", exception); }
        }, 0);
        encoder.submit();
    }
}

