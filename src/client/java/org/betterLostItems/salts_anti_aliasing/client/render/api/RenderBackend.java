package org.betterLostItems.salts_anti_aliasing.client.render.api;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Backend-neutral contract for render implementations.
 *
 * <p>The shared planner talks to this interface instead of to Vulkan internals,
 * Minecraft render targets, or mixins. A version jar can replace the backend
 * implementation while keeping the same pass vocabulary and config semantics.</p>
 */
public interface RenderBackend {
    /**
     * Identifies the family of renderer this backend drives.
     */
    RenderBackendType type();

    /**
     * Reports whether the backend can run on the current client.
     */
    boolean isAvailable();

    /**
     * Declares optional features available to the shared planner.
     */
    Set<RenderCapability> capabilities();

    /**
     * Receives the target set selected by the planner for the current mode.
     */
    void declareTargets(Collection<RenderTargetDescriptor> targets);

    /**
     * Returns the targets most recently declared to the backend.
     */
    List<RenderTargetDescriptor> declaredTargets();

    /**
     * Convenience capability check used by planners and future validation code.
     */
    default boolean supportsAll(Set<RenderCapability> requiredCapabilities) {
        return capabilities().containsAll(requiredCapabilities);
    }
}
