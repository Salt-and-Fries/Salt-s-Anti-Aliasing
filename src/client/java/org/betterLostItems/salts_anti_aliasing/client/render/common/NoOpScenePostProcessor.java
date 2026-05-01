package org.betterLostItems.salts_anti_aliasing.client.render.common;

import net.minecraft.client.renderer.GameRenderer;
import org.betterLostItems.salts_anti_aliasing.client.config.AntiAliasingConfig;

/**
 * Enumerates no op scene post processor values used by the anti-aliasing runtime and configuration
 * UI. Shared render orchestration code that decides which passes, targets, and controllers are
 * active for the selected mode.
 */
public enum NoOpScenePostProcessor implements ScenePostProcessor {
    INSTANCE;

    /**
     * Handles apply as part of the anti-aliasing render, configuration, or compatibility flow.
     * @param gameRenderer Minecraft game renderer whose scene target or post-processing phase is
     * being coordinated
     * @param config configuration object being normalized, copied, or committed
     */
    @Override
    public void apply(GameRenderer gameRenderer, AntiAliasingConfig config) {
    }
}
