package org.betterLostItems.salts_anti_aliasing.client.compat;

import net.fabricmc.loader.api.FabricLoader;

/**
 * Documents loaded mods behavior for Salt's Anti Aliasing. Optional-mod compatibility glue that avoids
 * hard dependencies.
 */
public final class LoadedMods {
    private static final boolean SODIUM_LOADED = FabricLoader.getInstance().isModLoaded("sodium");

    /**
     * Creates a loaded mods with the collaborators or initial state supplied by the caller.
     */
    private LoadedMods() {
    }

    /**
     * Coordinates sodium loaded within the anti-aliasing render, configuration, or compatibility flow.
     * @return sodium loaded value produced or selected by this code path
     */
    public static boolean sodiumLoaded() {
        return SODIUM_LOADED;
    }
}
