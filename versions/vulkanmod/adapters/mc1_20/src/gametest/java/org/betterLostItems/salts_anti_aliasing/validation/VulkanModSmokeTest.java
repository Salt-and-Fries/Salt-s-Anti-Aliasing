package org.betterLostItems.salts_anti_aliasing.validation;

import org.betterLostItems.salts_anti_aliasing.client.config.AntiAliasingMode;
import org.betterLostItems.salts_anti_aliasing.vulkanmod.SaltsVulkanModClient;

/** Development smoke test; this does not yet certify the full feature-parity matrix. */
public final class VulkanModSmokeTest  {
    public void runTest(LegacyContext context) {
        context.runOnClient(client -> SaltsVulkanModClient.configure(AntiAliasingMode.OFF));
        context.runOnClient(client -> SaltsVulkanModClient.configure(c -> c.recordMetrics = true));
        try (var world = context.worldBuilder().create()) {
            world.getServer().runCommand("gamemode creative @a");
            world.getServer().runCommand("time set noon");
            world.getServer().runCommand("fill -8 -60 5 8 -57 6 minecraft:stone_bricks");
            world.getServer().runCommand("fill -2 -60 3 0 -57 3 minecraft:blue_stained_glass");
            world.getServer().runCommand("summon minecraft:cow 2 -60 1 {NoAI:1b,Silent:1b,PersistenceRequired:1b,Tags:[\"salts_motion\"]}");
            world.getServer().runCommand("summon minecraft:item -3 -59 2 {Item:{id:\"minecraft:diamond_sword\",count:1},NoGravity:1b,Tags:[\"salts_motion\"]}");
            world.getServer().runCommand("summon minecraft:falling_block 4 -59 2 {BlockState:{Name:\"minecraft:gravel\"},NoGravity:1b,Time:-10000,Tags:[\"salts_motion\"]}");
            world.getServer().runCommand("tp @a 0 -56 -3 0 12");
            context.waitTicks(80);
            context.runOnClient(client -> client.gui.getChat().clearMessages(true));
            var baseline = screenshot(context, "salts-vulkanmod-off");
            String selectedModes = System.getenv("SALTS_VULKANMOD_TEST_MODES");
            if ("OFF".equals(selectedModes)) return;
            for (var mode : new AntiAliasingMode[]{AntiAliasingMode.MSAA, AntiAliasingMode.FXAA, AntiAliasingMode.SMAA, AntiAliasingMode.SSAA,
                    AntiAliasingMode.NIS_UPSCALE, AntiAliasingMode.SGSR1_UPSCALE, AntiAliasingMode.SGSR2_SUPER_RESOLUTION,
                    AntiAliasingMode.DLSS_SUPER_RESOLUTION, AntiAliasingMode.XESS_SUPER_RESOLUTION,
                    AntiAliasingMode.FSR2_SUPER_RESOLUTION, AntiAliasingMode.FSR3_SUPER_RESOLUTION,
                    AntiAliasingMode.FSR3_SUPER_RESOLUTION_FRAME_GENERATION,
                    AntiAliasingMode.FSR1_UPSCALE, AntiAliasingMode.TAA}) {
                if (selectedModes != null && !java.util.Arrays.asList(selectedModes.split(",")).contains(mode.name())) continue;
                context.runOnClient(client -> SaltsVulkanModClient.configure(mode));
                if (mode.usesDedicatedUpscalePass()) context.runOnClient(client -> SaltsVulkanModClient.configure(
                        c -> c.tuning.mipBiasMode = org.betterLostItems.salts_anti_aliasing.client.config.RenderTuning.MipBiasMode.AUTOMATIC));
                if (mode.usesHistoryBuffers()) world.getServer().runCommand("particle minecraft:campfire_cosy_smoke 1 -58 1 0.3 0.3 0.3 0.01 16 force");
                if (mode == AntiAliasingMode.MSAA) {
                    for (var samples : org.betterLostItems.salts_anti_aliasing.client.config.MsaaSampleLevel.values()) {
                        for (boolean coverage : new boolean[]{false, true}) {
                            context.runOnClient(client -> SaltsVulkanModClient.configure(c -> {
                                c.msaaSampleLevel = samples; c.msaaAlphaToCoverage = coverage;
                            }));
                            context.waitTicks(12);
                            context.runOnClient(client -> {
                                if (SaltsVulkanModClient.samples() < 2 || SaltsVulkanModClient.samples() > samples.samples())
                                    throw new AssertionError("Invalid hardware sample fallback for " + samples);
                            });
                        }
                    }
                    context.runOnClient(client -> SaltsVulkanModClient.configure(c -> {
                        c.msaaSampleLevel = org.betterLostItems.salts_anti_aliasing.client.config.MsaaSampleLevel.X4;
                        c.msaaAlphaToCoverage = false;
                    }));
                }
                if (mode.usesFsrFrameGeneration()) {
                    context.waitTicks(30);
                    for (int step = 1; step <= 12; step++) {
                        float yaw = step * .5f;
                        context.runOnClient(client -> client.player.setYRot(yaw));
                        context.waitTicks(3);
                    }
                    context.runOnClient(client -> client.player.setYRot(0));
                    context.waitTicks(30);
                } else context.waitTicks(60);
                if (mode.usesHistoryBuffers()) {
                    var moving = new java.util.concurrent.atomic.AtomicBoolean();
                    var particles = new java.util.concurrent.atomic.AtomicBoolean();
                    for (int step = 0; step < 4; step++) {
                        world.getServer().runCommand("execute as @e[tag=salts_motion] at @s run tp @s " + (step % 2 == 0 ? "~0.125" : "~-0.125") + " ~ ~");
                        context.waitTicks(2);
                        context.runOnClient(client -> {
                            var coverage = org.betterLostItems.salts_anti_aliasing.vulkanmod.motion.EntityMotionGeometry.coverage();
                            if (coverage.movingVertices() > 0) moving.set(true);
                            if (SaltsVulkanModClient.particleQuads() > 0) particles.set(true);
                        });
                    }
                    if (!moving.get()) throw new AssertionError(mode + " did not capture moving entity geometry");
                    if (!particles.get()) throw new AssertionError(mode + " did not render particle motion");
                    context.waitTicks(4);
                }
                context.runOnClient(client -> {
                    if (SaltsVulkanModClient.failure() != null) throw SaltsVulkanModClient.failure();
                    if (mode.usesHistoryBuffers() && TerrainProjectionChecks.checked() < 20)
                        throw new AssertionError("Temporal terrain projection was not verified");
                    if (SaltsVulkanModClient.resolvedFrames() < 30) throw new AssertionError("VulkanMod AA did not execute");
                    if (mode == AntiAliasingMode.MSAA && SaltsVulkanModClient.samples() < 2)
                        throw new AssertionError("MSAA did not select a multisample attachment");
                    if (mode == AntiAliasingMode.MSAA && SaltsVulkanModClient.worldMultisamplePasses() < 20)
                        throw new AssertionError("World draws did not enter multisample rendering");
                    if (mode.usesDedicatedUpscalePass() && SaltsVulkanModClient.handSamples() < 2)
                        throw new AssertionError("Upscaler hand AA did not allocate multisample coverage");
                    if (mode.usesDedicatedUpscalePass() && SaltsVulkanModClient.handMultisamplePasses() < 20)
                        throw new AssertionError("Hand draws did not enter multisample rendering");
                    if (mode.usesFsrQualityControl() && SaltsVulkanModClient.opaqueCaptures() < 20)
                        throw new AssertionError("FSR opaque capture did not execute");
                    if (mode.usesFsrFrameGeneration()) {
                        var fsr = org.betterLostItems.salts_anti_aliasing.client.render.vulkan.fsr.FsrRuntime.instance();
                        System.out.println("SALTS_VULKANMOD_FG game=" + fsr.renderedPresentCount() + " display=" + fsr.frameGenerationPresentCount());
                        if (!fsr.isFrameGenerationOutputConfirmed()) throw new AssertionError("No generated frames confirmed");
                    }
                    if (mode == AntiAliasingMode.SSAA && SaltsVulkanModClient.inputWidth() <= client.getWindow().getWidth())
                        throw new AssertionError("SSAA did not increase scene resolution");
                    if (mode.usesDedicatedUpscalePass() && SaltsVulkanModClient.inputWidth() > client.getWindow().getWidth())
                        throw new AssertionError("Upscaler did not reduce scene resolution");
                    System.out.println("SALTS_VULKANMOD_MODE " + mode + " input=" + SaltsVulkanModClient.inputWidth() + "x" + SaltsVulkanModClient.inputHeight());
                });
                var image = screenshot(context, "salts-vulkanmod-" + mode.name().toLowerCase(java.util.Locale.ROOT));
                double error = 0; int samples = 0;
                for (int y = 32; y < image.getHeight() * 2 / 3; y += 4)
                    for (int x = 16; x < image.getWidth() - 16; x += 4) {
                        int actual = image.getRGB(x, y), expected = baseline.getRGB(x, y);
                        for (int channel = 0; channel < 3; channel++) {
                            error += Math.abs((actual >> (channel * 8) & 255) - (expected >> (channel * 8) & 255)); samples++;
                        }
                    }
                if (error / samples > 35) throw new AssertionError(mode + " changed the world image unexpectedly: mean channel error=" + error / samples);
                if (mode.usesHistoryBuffers()) {
                    context.runOnClient(client -> SaltsVulkanModClient.configure(c ->
                            c.tuning.debugView = org.betterLostItems.salts_anti_aliasing.client.config.RenderTuning.DebugView.MOTION_VECTORS));
                    context.waitTicks(4);
                    var motionImage = screenshot(context, "salts-vulkanmod-motion-" + mode.name().toLowerCase(java.util.Locale.ROOT));
                    int blue = motionImage.getRGB(motionImage.getWidth() / 2, motionImage.getHeight() / 4) & 255;
                    if (Math.abs(blue - 128) > 3) throw new AssertionError(mode + " did not display its actual motion buffer");
                }
                context.runOnClient(client -> SaltsVulkanModClient.configure(c -> {
                    c.tuning.debugView = org.betterLostItems.salts_anti_aliasing.client.config.RenderTuning.DebugView.EDGES;
                    c.debugViewsEnabled = true;
                }));
                context.waitTicks(16);
                context.runOnClient(client -> {
                    if (!org.betterLostItems.salts_anti_aliasing.client.SaltsAntiAliasingClient.runtime().edgeDebugStats().available())
                        throw new AssertionError(mode + " did not complete edge-analysis readback");
                });
                ValidationScreenshots.capture(context, "salts-vulkanmod-edges-" + mode.name().toLowerCase(java.util.Locale.ROOT));
                exerciseQualities(context, mode);
                if (mode == AntiAliasingMode.NIS_UPSCALE) exerciseHandCoverage(context);
                if (mode.usesFsrFrameGeneration()) exerciseFrameGenerationLifecycle(context);
            }
            exerciseLifecycle(context);
            context.runOnClient(client -> SaltsVulkanModClient.configure(AntiAliasingMode.OFF));
            context.runOnClient(client -> org.betterLostItems.salts_anti_aliasing.client.SaltsAntiAliasingClient.runtime()
                    .setMode(AntiAliasingMode.FSR3_SUPER_RESOLUTION));
            context.setScreen(() -> new net.vulkanmod.config.gui.VOptionScreen(
                    net.minecraft.network.chat.Component.literal("Video Settings"), null));
            context.waitTicks(2);
            ValidationScreenshots.capture(context, "salts-vulkanmod-native-settings-entry");
            context.runOnClient(client -> {
                var entry = (org.betterLostItems.salts_anti_aliasing.vulkanmod.VulkanSettingsAccess) client.screen;
                client.screen.mouseClicked(entry.salts$settingsX() + 1, entry.salts$settingsY() + 1, 0);
                if (!(client.screen instanceof org.betterLostItems.salts_anti_aliasing.client.gui.AntiAliasingConfigScreen))
                    throw new AssertionError("VulkanMod settings entry did not open the complete controls");
            });
            context.setScreen(() -> new net.minecraft.client.gui.screens.VideoSettingsScreen(net.minecraft.client.Minecraft.getInstance().screen,
                    net.minecraft.client.Minecraft.getInstance().options));
            context.runOnClient(client -> {
                if (!press(client.screen, net.minecraft.network.chat.Component.translatable("screen.salts_anti_aliasing.config").getString()))
                    throw new AssertionError("Anti-aliasing entry is missing from Video Settings");
            });
            context.waitForScreen(org.betterLostItems.salts_anti_aliasing.client.gui.AntiAliasingConfigScreen.class);
            ValidationScreenshots.capture(context, "salts-vulkanmod-settings");
            context.runOnClient(client -> {
                String advanced = net.minecraft.network.chat.Component.translatable("options.salts_anti_aliasing.advanced",
                        net.minecraft.network.chat.Component.translatable("options.off")).getString();
                if (!press(client.screen, advanced)) throw new AssertionError("Advanced controls button is missing");
            });
            context.waitTicks(2);
            ValidationScreenshots.capture(context, "salts-vulkanmod-settings-advanced");
            context.setScreen(() -> new org.betterLostItems.salts_anti_aliasing.client.gui.CreditsAndLicensesScreen(null));
            context.waitTicks(2);
            context.runOnClient(client -> {
                client.screen.mouseScrolled(client.screen.width / 2d, client.screen.height / 2d, -8);
            });
            ValidationScreenshots.capture(context, "salts-vulkanmod-credits-scrolled");
            context.setScreen(() -> new org.betterLostItems.salts_anti_aliasing.client.gui.DlssSetupScreen(null));
            context.waitTicks(2);
            ValidationScreenshots.capture(context, "salts-vulkanmod-dlss-setup");
            context.setScreen(() -> null);
            context.waitTicks(5);
            context.runOnClient(client -> org.betterLostItems.salts_anti_aliasing.client.SaltsAntiAliasingClient.runtime().flushMetrics());
            var metrics = net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir().resolve("logs/salts_anti_aliasing_metrics_latest.json");
            try (var reader = java.nio.file.Files.newBufferedReader(metrics)) {
                var report = com.google.gson.JsonParser.parseReader(reader).getAsJsonObject();
                if (report.getAsJsonObject("overall").get("framesSampled").getAsLong() < 100)
                    throw new AssertionError("Metrics did not record rendered frames");
                if (selectedModes == null && report.getAsJsonObject("session").get("modesObserved").getAsInt() < 15)
                    throw new AssertionError("Metrics did not observe every rendering mode");
            } catch (java.io.IOException e) { throw new AssertionError("Cannot read the metrics report", e); }
        }
        try {
            java.nio.file.Files.writeString(net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir()
                    .resolve("salts-validation-result.json"), "{\"status\":\"PASS\"}");
        } catch (java.io.IOException error) { throw new AssertionError("Cannot write completed validation result", error); }
    }

    private record QualityChange(String name, java.util.function.Consumer<org.betterLostItems.salts_anti_aliasing.client.config.AntiAliasingConfig> edit) {}

    private static void exerciseFrameGenerationLifecycle(LegacyContext context) {
        for (int[] size : new int[][]{{960, 540}, {1280, 720}}) {
            context.runOnClient(client -> client.getWindow().setWindowed(size[0], size[1]));
            context.waitFor(client -> client.getMainRenderTarget().width == size[0] && client.getMainRenderTarget().height == size[1]);
            context.waitTicks(24);
            context.runOnClient(client -> {
                if (SaltsVulkanModClient.failure() != null) throw SaltsVulkanModClient.failure();
                var fsr = org.betterLostItems.salts_anti_aliasing.client.render.vulkan.fsr.FsrRuntime.instance();
                if (!fsr.isFrameGenerationSwapchainOwned() || !fsr.isFrameGenerationOutputConfirmed())
                    throw new AssertionError("Frame generation did not recover after resize to " + size[0] + "x" + size[1]);
            });
        }
        var reload = new java.util.concurrent.atomic.AtomicReference<java.util.concurrent.CompletableFuture<Void>>();
        context.runOnClient(client -> reload.set(client.reloadResourcePacks()));
        context.waitFor(client -> reload.get().isDone() && client.getOverlay() == null, 600);
        context.waitTicks(24);
        context.runOnClient(client -> {
            if (SaltsVulkanModClient.failure() != null) throw SaltsVulkanModClient.failure();
            if (!org.betterLostItems.salts_anti_aliasing.client.render.vulkan.fsr.FsrRuntime.instance().isFrameGenerationOutputConfirmed())
                throw new AssertionError("Frame generation did not recover after resource reload");
        });
        ValidationScreenshots.capture(context, "salts-vulkanmod-frame-generation-reloaded");
    }

    private static void exerciseHandCoverage(LegacyContext context) {
        var saved = SaltsVulkanModClient.configSnapshot();
        for (var hand : org.betterLostItems.salts_anti_aliasing.client.config.RenderTuning.HandAa.values()) {
            for (boolean coverage : new boolean[]{false, true}) {
                var before = new java.util.concurrent.atomic.AtomicLong();
                context.runOnClient(client -> {
                    before.set(SaltsVulkanModClient.handMultisamplePasses());
                    SaltsVulkanModClient.configure(c -> {
                        c.tuning.upscalerHandAa = hand;
                        c.tuning.upscalerHandAlphaToCoverage = coverage;
                    });
                });
                context.waitTicks(12);
                context.runOnClient(client -> {
                    if (SaltsVulkanModClient.failure() != null) throw SaltsVulkanModClient.failure();
                    if (hand.samples() == 1) {
                        if (SaltsVulkanModClient.handMultisamplePasses() != before.get())
                            throw new AssertionError("Hand multisampling still executes when disabled");
                    } else if (SaltsVulkanModClient.handSamples() < 2 || SaltsVulkanModClient.handSamples() > hand.samples()
                            || SaltsVulkanModClient.handMultisamplePasses() < 10)
                        throw new AssertionError("Hand coverage did not execute for " + hand + "/" + coverage);
                });
                ValidationScreenshots.capture(context, "salts-hand-" + hand.name().toLowerCase(java.util.Locale.ROOT) + "-" + coverage);
            }
        }
        context.runOnClient(client -> SaltsVulkanModClient.configure(c -> {
            c.tuning.upscalerHandAa = saved.tuning.upscalerHandAa;
            c.tuning.upscalerHandAlphaToCoverage = saved.tuning.upscalerHandAlphaToCoverage;
        }));
        context.waitTicks(4);
    }

    private static void exerciseQualities(LegacyContext context, AntiAliasingMode mode) {
        var changes = new java.util.ArrayList<QualityChange>();
        if (mode.usesDlssQualityControl()) for (var value : org.betterLostItems.salts_anti_aliasing.client.config.DlssQualityPreset.values())
            changes.add(new QualityChange(value.name(), c -> c.dlssQualityPreset = value));
        if (mode.usesXessQualityControl()) for (var value : org.betterLostItems.salts_anti_aliasing.client.config.XessQualityPreset.values())
            changes.add(new QualityChange(value.name(), c -> c.xessQualityPreset = value));
        if (mode.usesFsrQualityControl()) for (var value : org.betterLostItems.salts_anti_aliasing.client.config.FsrQualityPreset.values())
            changes.add(new QualityChange(value.name(), c -> c.fsrQualityPreset = value));
        if (mode.usesSgsr()) for (var value : org.betterLostItems.salts_anti_aliasing.client.config.SgsrQualityPreset.values())
            changes.add(new QualityChange(value.name(), c -> { c.sgsr1QualityPreset = value; c.sgsr2QualityPreset = value; }));
        if (mode == AntiAliasingMode.NIS_UPSCALE || mode == AntiAliasingMode.FSR1_UPSCALE)
            for (var value : org.betterLostItems.salts_anti_aliasing.client.config.NisUpscaleQualityPreset.values())
                changes.add(new QualityChange(value.name(), c -> c.nisUpscaleQualityPreset = value));
        if (mode == AntiAliasingMode.SSAA) for (var value : org.betterLostItems.salts_anti_aliasing.client.config.SsaaScaleLevel.values())
            changes.add(new QualityChange(value.name(), c -> c.ssaaScaleLevel = value));
        if (changes.isEmpty()) return;
        var saved = SaltsVulkanModClient.configSnapshot();
        context.runOnClient(client -> SaltsVulkanModClient.configure(c -> {
            c.debugViewsEnabled = false;
            c.tuning.debugView = org.betterLostItems.salts_anti_aliasing.client.config.RenderTuning.DebugView.NONE;
        }));
        for (var change : changes) {
            long before = SaltsVulkanModClient.resolvedFrames();
            context.runOnClient(client -> SaltsVulkanModClient.configure(change.edit()));
            context.waitTicks(8);
            context.runOnClient(client -> {
                if (SaltsVulkanModClient.failure() != null) throw SaltsVulkanModClient.failure();
                if (SaltsVulkanModClient.resolvedFrames() <= before) throw new AssertionError(mode + "/" + change.name() + " did not render");
                if (SaltsVulkanModClient.inputWidth() < 1 || SaltsVulkanModClient.inputHeight() < 1)
                    throw new AssertionError(mode + "/" + change.name() + " has invalid input dimensions");
            });
            System.out.println("SALTS_VULKANMOD_QUALITY " + mode + "/" + change.name());
        }
        context.runOnClient(client -> SaltsVulkanModClient.configure(c -> {
            c.dlssQualityPreset = saved.dlssQualityPreset; c.xessQualityPreset = saved.xessQualityPreset;
            c.fsrQualityPreset = saved.fsrQualityPreset; c.sgsr1QualityPreset = saved.sgsr1QualityPreset; c.sgsr2QualityPreset = saved.sgsr2QualityPreset;
            c.ssaaScaleLevel = saved.ssaaScaleLevel; c.nisUpscaleQualityPreset = saved.nisUpscaleQualityPreset;
        }));
        context.waitTicks(4);
    }

    private static void exerciseLifecycle(LegacyContext context) {
        context.runOnClient(client -> SaltsVulkanModClient.configure(AntiAliasingMode.TAA));
        context.waitTicks(12);
        context.runOnClient(client -> client.getWindow().setWindowed(960, 540));
        context.waitFor(client -> client.getMainRenderTarget().width == 960 && client.getMainRenderTarget().height == 540);
        context.waitTicks(12);
        context.runOnClient(client -> {
            if (SaltsVulkanModClient.failure() != null) throw SaltsVulkanModClient.failure();
            if (SaltsVulkanModClient.inputWidth() != 960) throw new AssertionError("AA did not resize its scene");
        });
        var reloaded = new java.util.concurrent.atomic.AtomicReference<java.util.concurrent.CompletableFuture<Void>>();
        context.runOnClient(client -> reloaded.set(client.reloadResourcePacks()));
        context.waitFor(client -> reloaded.get().isDone() && client.getOverlay() == null, 600);
        reloaded.get().join();
        context.waitTicks(16);
        context.runOnClient(client -> {
            if (SaltsVulkanModClient.failure() != null) throw SaltsVulkanModClient.failure();
            org.betterLostItems.salts_anti_aliasing.client.SaltsAntiAliasingClient.runtime().retryRendering();
        });
        context.waitTicks(12);
        context.runOnClient(client -> {
            if (SaltsVulkanModClient.failure() != null) throw SaltsVulkanModClient.failure();
            client.getWindow().setWindowed(1280, 720);
        });
        context.waitFor(client -> client.getMainRenderTarget().width == 1280 && client.getMainRenderTarget().height == 720);
        context.waitTicks(12);
        screenshot(context, "salts-vulkanmod-after-reload-resize-retry");
        System.out.println("SALTS_VULKANMOD_LIFECYCLE resize/reload/retry passed");
    }

    private static boolean press(net.minecraft.client.gui.components.events.GuiEventListener listener, String label) {
        if (listener instanceof net.minecraft.client.gui.components.Button button && button.getMessage().getString().equals(label)) {
            if (!button.active) throw new AssertionError("Disabled settings button: " + label);
            button.onPress();
            return true;
        }
        if (listener instanceof net.minecraft.client.gui.components.events.ContainerEventHandler container)
            for (var child : container.children()) if (press(child, label)) return true;
        return false;
    }

    private static java.awt.image.BufferedImage screenshot(LegacyContext context, String name) {
        return ValidationScreenshots.capture(context, name);
    }
}
