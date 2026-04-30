package org.betterLostItems.salts_anti_aliasing.client;

import net.fabricmc.api.ClientModInitializer;
import org.betterLostItems.salts_anti_aliasing.client.render.common.RenderRuntime;
import org.betterLostItems.salts_anti_aliasing.SaltsAntiAliasing;

public final class SaltsAntiAliasingClient implements ClientModInitializer {
    private static RenderRuntime renderRuntime;

    public static RenderRuntime runtime() {
        if (renderRuntime == null) {
            throw new IllegalStateException("Salt's Anti Aliasing runtime has not been initialized yet");
        }

        return renderRuntime;
    }

    public static RenderRuntime runtimeOrNull() {
        return renderRuntime;
    }

    @Override
    public void onInitializeClient() {
        renderRuntime = RenderRuntime.bootstrap();
        SaltsAntiAliasing.LOGGER.info("Client foundation initialized with {}", renderRuntime.describePlan());
    }
}
