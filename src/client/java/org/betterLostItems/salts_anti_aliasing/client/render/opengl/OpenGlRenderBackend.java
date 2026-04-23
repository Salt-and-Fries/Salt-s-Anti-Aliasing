package org.betterLostItems.salts_anti_aliasing.client.render.opengl;

import org.betterLostItems.salts_anti_aliasing.client.render.api.RenderBackend;
import org.betterLostItems.salts_anti_aliasing.client.render.api.RenderBackendType;
import org.betterLostItems.salts_anti_aliasing.client.render.api.RenderCapability;
import org.betterLostItems.salts_anti_aliasing.client.render.api.RenderTargetDescriptor;

import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class OpenGlRenderBackend implements RenderBackend {
    private static final Set<RenderCapability> CAPABILITIES = EnumSet.of(
            RenderCapability.POST_PROCESSING,
            RenderCapability.SHARPENING,
            RenderCapability.MULTISAMPLE_AA,
            RenderCapability.INTERNAL_RESOLUTION,
            RenderCapability.SPATIAL_UPSCALING,
            RenderCapability.TEMPORAL_AA
    );

    private final Map<String, RenderTargetDescriptor> declaredTargets = new LinkedHashMap<>();

    @Override
    public RenderBackendType type() {
        return RenderBackendType.OPENGL;
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public Set<RenderCapability> capabilities() {
        return CAPABILITIES;
    }

    @Override
    public void declareTargets(Collection<RenderTargetDescriptor> targets) {
        declaredTargets.clear();
        for (RenderTargetDescriptor target : targets) {
            declaredTargets.put(target.id(), target);
        }
    }

    @Override
    public List<RenderTargetDescriptor> declaredTargets() {
        return List.copyOf(declaredTargets.values());
    }
}
