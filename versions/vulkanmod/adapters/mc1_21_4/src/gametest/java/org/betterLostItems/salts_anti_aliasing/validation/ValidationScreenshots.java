package org.betterLostItems.salts_anti_aliasing.validation;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.vulkanmod.vulkan.Renderer;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

/** Captures the normal rendered frame; this test API version renders screenshots outside VulkanMod's frame scope. */
public final class ValidationScreenshots {
    private record Request(Path path, CompletableFuture<BufferedImage> image) {}
    private static Request pending;
    private static int counter;
    private ValidationScreenshots() {}
    public static BufferedImage capture(ClientGameTestContext context, String name) {
        var future = new CompletableFuture<BufferedImage>();
        context.runOnClient(client -> {
            if (pending != null) throw new IllegalStateException("Screenshot request still pending");
            var path = net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir().resolve("screenshots")
                    .resolve(String.format(java.util.Locale.ROOT, "%04d_%s.png", counter++, name));
            pending = new Request(path, future);
        });
        context.waitFor(client -> future.isDone(), 600);
        return future.join();
    }
    public static void afterRender() {
        if (pending == null || !Renderer.isRecording()) return;
        var request = pending; pending = null;
        try {
            try (var image = Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget())) {
                java.nio.file.Files.createDirectories(request.path().getParent());
                image.writeToFile(request.path());
                request.image().complete(javax.imageio.ImageIO.read(request.path().toFile()));
            }
        } catch (Exception error) { request.image().completeExceptionally(error); }
    }
}
