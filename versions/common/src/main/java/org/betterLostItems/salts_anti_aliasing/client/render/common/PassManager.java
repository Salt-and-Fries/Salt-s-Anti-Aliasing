package org.betterLostItems.salts_anti_aliasing.client.render.common;

import org.betterLostItems.salts_anti_aliasing.client.render.api.RenderPassSpec;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Stores the currently active pass list in execution order so UI and debug code can describe the
 * configured pipeline consistently.
 */
public final class PassManager {
    private final List<RenderPassSpec> orderedPasses = new ArrayList<>();

    /**
     * Handles replace all as part of the anti-aliasing render, configuration, or compatibility
     * flow.
     * @param passes passes value supplied by the caller or Minecraft callback
     */
    public void replaceAll(Collection<RenderPassSpec> passes) {
        orderedPasses.clear();
        orderedPasses.addAll(passes);
    }

    /**
     * Handles passes as part of the anti-aliasing render, configuration, or compatibility flow.
     * @return active render passes in execution order
     */
    public List<RenderPassSpec> passes() {
        return List.copyOf(orderedPasses);
    }

    /**
     * Handles ordered pass ids as part of the anti-aliasing render, configuration, or compatibility
     * flow.
     * @return active render pass identifiers in execution order
     */
    public List<String> orderedPassIds() {
        return orderedPasses.stream().map(RenderPassSpec::id).toList();
    }
}
