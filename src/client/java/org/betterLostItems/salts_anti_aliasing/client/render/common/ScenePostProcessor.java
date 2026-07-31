package org.betterLostItems.salts_anti_aliasing.client.render.common;

import net.minecraft.client.renderer.GameRenderer;
import org.betterLostItems.salts_anti_aliasing.client.config.AntiAliasingConfig;

/**
 * Contract for scene post processor behavior so platform-specific code can depend on a small,
 * testable surface. Shared render orchestration code that decides which passes, targets, and
 * controllers are active for the selected mode.
 */
public interface ScenePostProcessor {
    /** Resolves temporal AA immediately after the world, before later scene-only effects. */
    void applyTemporalResolve(GameRenderer gameRenderer, AntiAliasingConfig config);

    /** Applies the final native-resolution AA and sharpening effects before the HUD. */
    void applyFinalEffects(GameRenderer gameRenderer, AntiAliasingConfig config);
}
