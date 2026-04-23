package org.betterLostItems.salts_anti_aliasing.client.render.common;

import org.betterLostItems.salts_anti_aliasing.client.render.api.RenderPassSpec;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public final class PassManager {
    private final List<RenderPassSpec> orderedPasses = new ArrayList<>();

    public void replaceAll(Collection<RenderPassSpec> passes) {
        orderedPasses.clear();
        orderedPasses.addAll(passes);
    }

    public List<RenderPassSpec> passes() {
        return List.copyOf(orderedPasses);
    }

    public List<String> orderedPassIds() {
        return orderedPasses.stream().map(RenderPassSpec::id).toList();
    }
}
