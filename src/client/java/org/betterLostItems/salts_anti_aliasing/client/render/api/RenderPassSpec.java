package org.betterLostItems.salts_anti_aliasing.client.render.api;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Documents render pass spec behavior for Salt's Anti Aliasing. Backend-neutral rendering API shared
 * by the planner and backend implementations.
 */
public record RenderPassSpec(
        String id,
        Set<RenderCapability> requiredCapabilities,
        List<String> readTargets,
        List<String> writeTargets
) {
    public RenderPassSpec {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Render pass id cannot be blank");
        }

        requiredCapabilities = requiredCapabilities.isEmpty()
                ? EnumSet.noneOf(RenderCapability.class)
                : EnumSet.copyOf(requiredCapabilities);
        readTargets = List.copyOf(readTargets);
        writeTargets = List.copyOf(writeTargets);
    }
}
