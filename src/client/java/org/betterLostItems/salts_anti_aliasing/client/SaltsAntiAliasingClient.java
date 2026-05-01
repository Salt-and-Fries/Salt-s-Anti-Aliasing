package org.betterLostItems.salts_anti_aliasing.client;

import net.fabricmc.api.ClientModInitializer;
import org.betterLostItems.salts_anti_aliasing.client.render.common.RenderRuntime;
import org.betterLostItems.salts_anti_aliasing.SaltsAntiAliasing;

/**
 * Client entrypoint that bootstraps rendering, registers hotkeys, and wires optional HUD/debug
 * integrations.
 */
public final class SaltsAntiAliasingClient implements ClientModInitializer {
    private static RenderRuntime renderRuntime;

    /**
     * Returns runtime for callers that need to coordinate UI, mixin, or render behavior.
     * @return runtime value produced or selected by this code path
     */
    public static RenderRuntime runtime() {
        if (renderRuntime == null) {
            throw new IllegalStateException("Salt's Anti Aliasing runtime has not been initialized yet");
        }

        return renderRuntime;
    }

    /**
     * Returns runtime or null for callers that need to coordinate UI, mixin, or render behavior.
     * @return runtime or null value produced or selected by this code path
     */
    public static RenderRuntime runtimeOrNull() {
        return renderRuntime;
    }

    /**
     * Initializes client-side render state, HUD hooks, and the key binding used to cycle anti-aliasing
     * modes.
     */
    @Override
    public void onInitializeClient() {
        renderRuntime = RenderRuntime.bootstrap();
        SaltsAntiAliasing.LOGGER.info("Client foundation initialized with {}", renderRuntime.describePlan());
    }
}
