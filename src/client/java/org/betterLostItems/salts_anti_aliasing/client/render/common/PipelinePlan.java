package org.betterLostItems.salts_anti_aliasing.client.render.common;

import org.betterLostItems.salts_anti_aliasing.client.render.api.RenderBackendType;
import org.betterLostItems.salts_anti_aliasing.client.render.api.RenderPassSpec;
import org.betterLostItems.salts_anti_aliasing.client.render.api.RenderTargetDescriptor;

import java.util.List;

/**
 * Documents pipeline plan behavior for Salt's Anti Aliasing. Shared render orchestration code
 * independent of a specific graphics backend.
 */
public record PipelinePlan(
        RenderBackendType backendType,
        List<RenderTargetDescriptor> targets,
        List<RenderPassSpec> passes
) {
    public PipelinePlan {
        targets = List.copyOf(targets);
        passes = List.copyOf(passes);
    }
}
