package org.betterLostItems.salts_anti_aliasing.client.render.common;

import org.betterLostItems.salts_anti_aliasing.client.render.api.RenderPassSpec;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Documents pass manager behavior for Salt's Anti Aliasing. Shared render orchestration code
 * independent of a specific graphics backend.
 */
public final class PassManager {
    private final List<RenderPassSpec> orderedPasses = new ArrayList<>();

    /**
     * Coordinates replace all within the anti-aliasing render, configuration, or compatibility flow.
     * @param passes passes supplied by Minecraft or the caller
     */
    public void replaceAll(Collection<RenderPassSpec> passes) {
        orderedPasses.clear();
        orderedPasses.addAll(passes);
    }

    /**
     * Coordinates passes within the anti-aliasing render, configuration, or compatibility flow.
     * @return passes value produced or selected by this code path
     */
    public List<RenderPassSpec> passes() {
        return List.copyOf(orderedPasses);
    }

    /**
     * Coordinates ordered pass ids within the anti-aliasing render, configuration, or compatibility
     * flow.
     * @return ordered pass ids value produced or selected by this code path
     */
    public List<String> orderedPassIds() {
        return orderedPasses.stream().map(RenderPassSpec::id).toList();
    }
}
