package org.betterLostItems.salts_anti_aliasing.client.render.api;

/**
 * Documents render target type behavior for Salt's Anti Aliasing. Backend-neutral rendering API shared
 * by the planner and backend implementations.
 */
public enum RenderTargetType {
    SCENE_COLOR,
    SCENE_DEPTH,
    INTERMEDIATE_COLOR,
    HISTORY_COLOR,
    AUXILIARY
}
