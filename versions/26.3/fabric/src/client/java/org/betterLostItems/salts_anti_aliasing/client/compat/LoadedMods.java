package org.betterLostItems.salts_anti_aliasing.client.compat;

import net.fabricmc.loader.api.FabricLoader;

/**
 * Implements loaded mods behavior for Salt's Anti Aliasing. Compatibility glue code that cooperates
 * with optional mods without making them hard dependencies.
 */
public final class LoadedMods {
    private static final boolean SODIUM_LOADED = FabricLoader.getInstance().isModLoaded("sodium");

    /**
     * Creates a loaded mods instance with the collaborators or initial state supplied by the
     * caller.
     */
    private LoadedMods() {
    }

    /**
     * Handles sodium loaded as part of the anti-aliasing render, configuration, or compatibility
     * flow.
     * @return whether the operation or state is enabled
     */
    public static boolean sodiumLoaded() {
        return SODIUM_LOADED;
    }
}
