package org.betterLostItems.salts_anti_aliasing.client.render.api;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Immutable value object carrying render pass spec data between render-planning and runtime code.
 * Backend-neutral render API code shared by Vulkan runtime code and pipeline planning.
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
