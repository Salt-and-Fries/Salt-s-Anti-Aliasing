package org.betterLostItems.salts_anti_aliasing.validation;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import org.betterLostItems.salts_anti_aliasing.client.SaltsAntiAliasingClient;
import org.betterLostItems.salts_anti_aliasing.client.config.AntiAliasingMode;
import org.betterLostItems.salts_anti_aliasing.client.config.RenderTuning;
import org.betterLostItems.salts_anti_aliasing.client.render.common.RenderingHealth;
import org.betterLostItems.salts_anti_aliasing.client.render.vulkan.VulkanHandAaController;
import org.betterLostItems.salts_anti_aliasing.client.render.vulkan.fsr.FsrRuntime;
import org.betterLostItems.salts_anti_aliasing.client.gui.AntiAliasingConfigScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.options.VideoSettingsScreen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.network.chat.Component;

/** Runs only in Loom's separate gametest mod, never in the distributable jar. */
public final class AntiAliasingParityTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        context.runOnClient(client -> {
            SaltsAntiAliasingClient.runtime().editSettings(config -> {
                config.mode = AntiAliasingMode.OFF;
                config.recordMetrics = true;
            });
            if (!SaltsAntiAliasingClient.runtime().isVulkanActive()) throw new AssertionError("Vulkan backend required");
        });
        try (var world = context.worldBuilder().create()) {
            world.getServer().runCommand("gamemode creative @a");
            world.getServer().runCommand("time set noon");
            world.getServer().runCommand("fill -8 -60 5 8 -57 6 minecraft:stone_bricks");
            world.getServer().runCommand("fill -5 -56 5 5 -56 5 minecraft:oak_leaves");
            world.getServer().runCommand("summon minecraft:armor_stand 0 -56 3");
            world.getServer().runCommand("tp @a 0 -56 -3 0 12");
            world.getServer().runCommand("give @a minecraft:diamond_sword");
            context.waitTicks(80);
            for (AntiAliasingMode mode : AntiAliasingMode.implementedModes()) {
                context.runOnClient(client -> {
                    SaltsAntiAliasingClient.runtime().editSettings(config -> {
                        config.mode = mode;
                        config.tuning.upscalerHandAa = RenderTuning.HandAa.MSAA_4X;
                        config.tuning.mipBiasMode = RenderTuning.MipBiasMode.AUTOMATIC;
                    });
                    SaltsAntiAliasingClient.runtime().retryRendering();
                });
                context.waitTicks(30);
                world.getServer().runCommand("particle minecraft:flame 1 -54 3 0.1 0.1 0.1 0.01 40 force");
                context.runOnClient(client -> client.player.setYRot(3.0f));
                context.waitTicks(30);
                context.runOnClient(client -> {
                    var runtime = SaltsAntiAliasingClient.runtime();
                    String health = RenderingHealth.describe(mode);
                    System.out.println("SALTS_GPU_VALIDATION " + mode + " | " + health);
                    if (runtime.activeMode() != mode || health.contains("fallback:") || health.endsWith("preparing")) {
                        throw new AssertionError(mode + " did not render: " + health);
                    }
                    if (mode.usesDedicatedUpscalePass() && VulkanHandAaController.instance().allocatedSamples() < 2) {
                        throw new AssertionError(mode + " did not allocate the requested hand AA target");
                    }
                    if (mode.usesFsrFrameGeneration()) {
                        var fsr = FsrRuntime.instance();
                        System.out.println("SALTS_GPU_FG game=" + fsr.renderedPresentCount()
                                + " display=" + fsr.frameGenerationPresentCount()
                                + " confirmed=" + fsr.isFrameGenerationOutputConfirmed());
                        if (!fsr.isFrameGenerationOutputConfirmed()) throw new AssertionError("No generated frames confirmed");
                    }
                });
                context.takeScreenshot("salts-26.2-" + mode.name().toLowerCase(java.util.Locale.ROOT));
            }
            context.setScreen(() -> new VideoSettingsScreen(null, Minecraft.getInstance(), Minecraft.getInstance().options));
            // Fabric's button helper visits layouts but not entries nested inside OptionsList.
            context.runOnClient(client -> {
                if (!openSettings(client.gui.screen())) throw new AssertionError("AA settings entry missing from video options");
            });
            context.waitForScreen(AntiAliasingConfigScreen.class);
            context.takeScreenshot("salts-26.2-settings");
            context.setScreen(() -> null);
            context.waitTicks(10);
        }
        try {
            java.nio.file.Files.writeString(java.nio.file.Path.of("salts-validation-result.json"),
                    "{\"status\":\"PASS\",\"minecraft\":\"26.2\"}\n");
        } catch (java.io.IOException failure) {
            throw new java.io.UncheckedIOException(failure);
        }
    }

    private static boolean openSettings(GuiEventListener listener) {
        if (listener instanceof Button button && button.getMessage().getString().equals(
                Component.translatable("screen.salts_anti_aliasing.config").getString())) {
            if (!button.active) throw new AssertionError("AA settings disabled on Vulkan");
            button.onPress(new MouseButtonInfo(0, 0));
            return true;
        }
        if (listener instanceof ContainerEventHandler container) {
            for (GuiEventListener child : container.children()) if (openSettings(child)) return true;
        }
        return false;
    }
}
