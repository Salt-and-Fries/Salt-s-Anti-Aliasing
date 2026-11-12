package org.betterLostItems.salts_anti_aliasing.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.betterLostItems.salts_anti_aliasing.client.render.common.RenderRuntime;

public final class SaltsAntiAliasingClient {
    private static RenderRuntime runtime;
    private SaltsAntiAliasingClient() {}
    public static RenderRuntime runtimeOrNull() { return runtime; }
    public static RenderRuntime runtime() {
        if (runtime == null) throw new IllegalStateException("Anti-aliasing runtime is not initialized");
        return runtime;
    }
    public static void initialize(RenderRuntime.Adapter adapter) {
        runtime = new RenderRuntime(adapter);
        net.fabricmc.fabric.api.resource.v1.ResourceLoader.get(net.minecraft.server.packs.PackType.CLIENT_RESOURCES)
                .registerReloader(Identifier.parse("salts_anti_aliasing:render_resources"),
                        (net.minecraft.server.packs.resources.ResourceManagerReloadListener) resources ->
                                org.betterLostItems.salts_anti_aliasing.vulkanmod.SaltsVulkanModClient.resourcesReloaded());
        net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry.addLast(Identifier.parse("salts_anti_aliasing:edge_debug_hud"),
                org.betterLostItems.salts_anti_aliasing.client.debug.EdgeDebugHud::render);
        var key = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.salts_anti_aliasing.cycle_mode",
                InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(),
                KeyMapping.Category.register(Identifier.parse("salts_anti_aliasing:main"))));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (key.consumeClick()) {
                var selected = runtime.cycleMode();
                if (client.player != null) client.player.displayClientMessage(
                        Component.literal("Salt's Anti Aliasing: " + selected.displayName() + " [VulkanMod]"), true);
            }
        });
    }
}
