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

/**
 * Documents open gl render backend behavior for Salt's Anti Aliasing. OpenGL backend code that owns
 * framebuffers, post chains, and GPU-side state.
 */
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

    /**
     * Coordinates type within the anti-aliasing render, configuration, or compatibility flow.
     * @return type value produced or selected by this code path
     */
    @Override
    public RenderBackendType type() {
        return RenderBackendType.OPENGL;
    }

    /**
     * Checks whether is available without mutating configuration or render state.
     * @return is available value produced or selected by this code path
     */
    @Override
    public boolean isAvailable() {
        return true;
    }

    /**
     * Coordinates capabilities within the anti-aliasing render, configuration, or compatibility flow.
     * @return capabilities value produced or selected by this code path
     */
    @Override
    public Set<RenderCapability> capabilities() {
        return CAPABILITIES;
    }

    /**
     * Coordinates declare targets within the anti-aliasing render, configuration, or compatibility
     * flow.
     * @param targets targets supplied by Minecraft or the caller
     */
    @Override
    public void declareTargets(Collection<RenderTargetDescriptor> targets) {
        declaredTargets.clear();
        for (RenderTargetDescriptor target : targets) {
            declaredTargets.put(target.id(), target);
        }
    }

    /**
     * Coordinates declared targets within the anti-aliasing render, configuration, or compatibility
     * flow.
     * @return declared targets value produced or selected by this code path
     */
    @Override
    public List<RenderTargetDescriptor> declaredTargets() {
        return List.copyOf(declaredTargets.values());
    }
}
