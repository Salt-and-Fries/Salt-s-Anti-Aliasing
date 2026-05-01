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
 * Implements open gl render backend behavior for Salt's Anti Aliasing. OpenGL implementation code
 * that owns render-target redirection, post-processing, and Minecraft framebuffer coordination.
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
     * Handles type as part of the anti-aliasing render, configuration, or compatibility flow.
     * @return backend type implemented by this object
     */
    @Override
    public RenderBackendType type() {
        return RenderBackendType.OPENGL;
    }

    /**
     * Checks is available without mutating runtime or configuration state.
     * @return whether the requested condition is true
     */
    @Override
    public boolean isAvailable() {
        return true;
    }

    /**
     * Handles capabilities as part of the anti-aliasing render, configuration, or compatibility
     * flow.
     * @return capability set advertised by this backend
     */
    @Override
    public Set<RenderCapability> capabilities() {
        return CAPABILITIES;
    }

    /**
     * Handles declare targets as part of the anti-aliasing render, configuration, or compatibility
     * flow.
     * @param targets targets value supplied by the caller or Minecraft callback
     */
    @Override
    public void declareTargets(Collection<RenderTargetDescriptor> targets) {
        declaredTargets.clear();
        for (RenderTargetDescriptor target : targets) {
            declaredTargets.put(target.id(), target);
        }
    }

    /**
     * Handles declared targets as part of the anti-aliasing render, configuration, or compatibility
     * flow.
     * @return render targets declared by the active backend
     */
    @Override
    public List<RenderTargetDescriptor> declaredTargets() {
        return List.copyOf(declaredTargets.values());
    }
}
