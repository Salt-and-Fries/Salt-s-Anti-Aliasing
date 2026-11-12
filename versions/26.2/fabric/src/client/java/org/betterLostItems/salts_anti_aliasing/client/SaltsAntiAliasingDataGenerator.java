package org.betterLostItems.salts_anti_aliasing.client;

import net.fabricmc.fabric.api.datagen.v1.DataGeneratorEntrypoint;
import net.fabricmc.fabric.api.datagen.v1.FabricDataGenerator;

/**
 * Data-generator entrypoint kept intentionally small because this mod ships hand-authored resources
 * instead of generated data packs.
 */
public final class SaltsAntiAliasingDataGenerator implements DataGeneratorEntrypoint {

    /**
     * Coordinates on initialize data generator within the anti-aliasing render, configuration, or compatibility flow.
     * @param fabricDataGenerator fabric data generator value supplied by the caller or Minecraft
     * callback
     */
    @Override
    public void onInitializeDataGenerator(FabricDataGenerator fabricDataGenerator) {
        FabricDataGenerator.Pack pack = fabricDataGenerator.createPack();
    }
}
