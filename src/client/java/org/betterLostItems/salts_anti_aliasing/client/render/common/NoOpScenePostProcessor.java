package org.betterLostItems.salts_anti_aliasing.client.render.common;

import net.minecraft.client.renderer.GameRenderer;
import org.betterLostItems.salts_anti_aliasing.client.config.AntiAliasingConfig;

/**
 * Documents no op scene post processor behavior for Salt's Anti Aliasing. Shared render orchestration
 * code independent of a specific graphics backend.
 */
public enum NoOpScenePostProcessor implements ScenePostProcessor {
    INSTANCE;

    /**
     * Coordinates apply within the anti-aliasing render, configuration, or compatibility flow.
     * @param gameRenderer Minecraft renderer currently being intercepted or processed
     * @param config configuration being read, normalized, or applied
     */
    @Override
    public void apply(GameRenderer gameRenderer, AntiAliasingConfig config) {
    }
}
