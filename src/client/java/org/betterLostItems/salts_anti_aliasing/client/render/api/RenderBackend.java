package org.betterLostItems.salts_anti_aliasing.client.render.api;

import java.util.Collection;
import java.util.List;
import java.util.Set;

public interface RenderBackend {
    RenderBackendType type();

    boolean isAvailable();

    Set<RenderCapability> capabilities();

    void declareTargets(Collection<RenderTargetDescriptor> targets);

    List<RenderTargetDescriptor> declaredTargets();

    default boolean supportsAll(Set<RenderCapability> requiredCapabilities) {
        return capabilities().containsAll(requiredCapabilities);
    }
}
