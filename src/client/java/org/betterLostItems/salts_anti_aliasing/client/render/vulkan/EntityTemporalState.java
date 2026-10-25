package org.betterLostItems.salts_anti_aliasing.client.render.vulkan;

/** Stable entity identity carried through Minecraft's extracted render state. */
public interface EntityTemporalState {
    int saltsAntiAliasing$entityId();

    void saltsAntiAliasing$entityId(int id);
}
