package org.betterLostItems.salts_anti_aliasing.client.render.common;

import net.minecraft.client.renderer.GameRenderer;
import org.betterLostItems.salts_anti_aliasing.client.config.AntiAliasingConfig;

/**
 * Contract for scene post processor behavior so platform-specific code can depend on a small,
 * testable surface. Shared render orchestration code that decides which passes, targets, and
 * controllers are active for the selected mode.
 */
public interface ScenePostProcessor {
    /**
     * Handles apply as part of the anti-aliasing render, configuration, or compatibility flow.
     * @param gameRenderer Minecraft game renderer whose scene target or post-processing phase is
     * being coordinated
     * @param config configuration object being normalized, copied, or committed
     */
    void apply(GameRenderer gameRenderer, AntiAliasingConfig config);
}
