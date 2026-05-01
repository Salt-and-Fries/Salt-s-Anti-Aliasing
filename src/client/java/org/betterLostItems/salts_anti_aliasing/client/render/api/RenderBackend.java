package org.betterLostItems.salts_anti_aliasing.client.render.api;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Backend-facing contract consumed by the core pass planner.
 *
 * <p>This interface describes what a renderer can do without saying how it does it.
 * Modern OpenGL, a future mid-version adapter, and a legacy FBO adapter can all expose
 * the same capabilities while allocating and executing resources differently.</p>
 */
public interface RenderBackend {
    /**
     * Human-readable backend family used for logs, metrics, and diagnostics.
     */
    RenderBackendType type();

    /**
     * Whether this backend can be selected on the current client.
     */
    boolean isAvailable();

    /**
     * Feature set that the planner may use when building a pipeline.
     */
    Set<RenderCapability> capabilities();

    /**
     * Receives the current render target declarations after planning.
     */
    void declareTargets(Collection<RenderTargetDescriptor> targets);

    /**
     * Returns the last target declarations accepted by the backend.
     */
    List<RenderTargetDescriptor> declaredTargets();

    /**
     * Convenience check used while validating each planned pass.
     */
    default boolean supportsAll(Set<RenderCapability> requiredCapabilities) {
        return capabilities().containsAll(requiredCapabilities);
    }
}
