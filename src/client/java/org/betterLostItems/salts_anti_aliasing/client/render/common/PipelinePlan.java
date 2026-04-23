package org.betterLostItems.salts_anti_aliasing.client.render.common;

import org.betterLostItems.salts_anti_aliasing.client.render.api.RenderBackendType;
import org.betterLostItems.salts_anti_aliasing.client.render.api.RenderPassSpec;
import org.betterLostItems.salts_anti_aliasing.client.render.api.RenderTargetDescriptor;

import java.util.List;

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
