package org.betterLostItems.salts_anti_aliasing.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.betterLostItems.salts_anti_aliasing.SaltsAntiAliasing;
import org.betterLostItems.salts_anti_aliasing.client.config.AntiAliasingMode;
import org.betterLostItems.salts_anti_aliasing.client.debug.EdgeDebugHud;
import org.betterLostItems.salts_anti_aliasing.client.render.common.RenderRuntime;
import org.lwjgl.glfw.GLFW;

/**
 * Client entrypoint that bootstraps the render runtime, registers key bindings, and connects
 * HUD/debug callbacks.
 */
public final class SaltsAntiAliasingClient implements ClientModInitializer {
    private static final Identifier KEY_CATEGORY = Identifier.parse(SaltsAntiAliasing.MOD_ID + ":main");
    private static final Identifier EDGE_DEBUG_HUD = Identifier.parse(SaltsAntiAliasing.MOD_ID + ":edge_debug_hud");
    private static final String CYCLE_MODE_KEY = "key.salts_anti_aliasing.cycle_mode";

    private static RenderRuntime renderRuntime;
    private KeyMapping cycleModeKey;

    /**
     * Returns runtime for callers that need to coordinate UI, mixin, or render behavior.
     * @return initialized render runtime
     */
    public static RenderRuntime runtime() {
        if (renderRuntime == null) {
            throw new IllegalStateException("Salt's Anti Aliasing runtime has not been initialized yet");
        }

        return renderRuntime;
    }

    /**
     * Returns runtime or null for callers that need to coordinate UI, mixin, or render behavior.
     * @return runtime when initialization has completed, or null during early startup
     */
    public static RenderRuntime runtimeOrNull() {
        return renderRuntime;
    }

    /**
     * Coordinates on initialize client within the anti-aliasing render, configuration, or compatibility flow.
     */
    @Override
    public void onInitializeClient() {
        renderRuntime = RenderRuntime.bootstrap();
        HudElementRegistry.addLast(EDGE_DEBUG_HUD, EdgeDebugHud::render);
        cycleModeKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                CYCLE_MODE_KEY,
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_O,
                KeyMapping.Category.register(KEY_CATEGORY)
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (cycleModeKey.consumeClick()) {
                if (!renderRuntime.canUseAntiAliasing()) {
                    if (client.player != null) {
                        client.player.sendOverlayMessage(
                                Component.literal("Salt's Anti Aliasing requires Minecraft's Vulkan graphics API")
                        );
                    }
                    continue;
                }

                AntiAliasingMode activeMode = renderRuntime.cycleMode();
                if (client.player != null) {
                    client.player.sendOverlayMessage(
                            Component.literal(
                                    "Salt's Anti Aliasing: " + activeMode.displayName() + " [" + renderRuntime.backendName() + "]"
                            )
                    );
                }
            }
        });

        SaltsAntiAliasing.LOGGER.info("Client foundation initialized with {}", renderRuntime.describePlan());
    }
}
