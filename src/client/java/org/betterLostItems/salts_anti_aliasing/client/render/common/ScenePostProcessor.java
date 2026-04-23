package org.betterLostItems.salts_anti_aliasing.client.render.common;

import net.minecraft.client.renderer.GameRenderer;
import org.betterLostItems.salts_anti_aliasing.client.config.AntiAliasingConfig;

public interface ScenePostProcessor {
    void apply(GameRenderer gameRenderer, AntiAliasingConfig config);
}
