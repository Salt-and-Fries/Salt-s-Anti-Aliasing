package org.betterLostItems.salts_anti_aliasing.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import net.fabricmc.loader.api.FabricLoader;
import org.betterLostItems.salts_anti_aliasing.SaltsAntiAliasing;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;

/**
 * Fabric-backed persistence adapter for the shared anti-aliasing config.
 *
 * <p>The in-memory {@link AntiAliasingConfig} is plain data and can be reused by every
 * version jar. This manager is the modern Fabric implementation of loading, validating,
 * editing, and saving that data.</p>
 */
public final class ConfigManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path configPath;
    private AntiAliasingConfig config = new AntiAliasingConfig();

    /**
     * Creates a config manager with the collaborators or initial state supplied by the caller.
     * @param configPath config path supplied by Minecraft or the caller
     */
    private ConfigManager(Path configPath) {
        this.configPath = configPath;
        config.sanitize();
    }

    /**
     * Coordinates create default within the anti-aliasing render, configuration, or compatibility
     * flow.
     * @return create default value produced or selected by this code path
     */
    public static ConfigManager createDefault() {
        Path configDir = FabricLoader.getInstance().getConfigDir();
        return new ConfigManager(configDir.resolve(SaltsAntiAliasing.MOD_ID + ".json"));
    }

    /**
     * Loads config from disk, creating or repairing the file when needed.
     */
    public synchronized void load() {
        if (Files.notExists(configPath)) {
            save();
            return;
        }

        try (Reader reader = Files.newBufferedReader(configPath)) {
            AntiAliasingConfig loaded = GSON.fromJson(reader, AntiAliasingConfig.class);
            config = loaded == null ? new AntiAliasingConfig() : loaded;
            config.sanitize();
        } catch (IOException | JsonSyntaxException exception) {
            SaltsAntiAliasing.LOGGER.warn("Falling back to default config after failing to read {}", configPath, exception);
            config = new AntiAliasingConfig();
            config.sanitize();
            save();
        }
    }

    /**
     * Returns a defensive copy so renderer code can use a stable frame-local view.
     */
    public synchronized AntiAliasingConfig snapshot() {
        return config.copy();
    }

    /**
     * Coordinates mode within the anti-aliasing render, configuration, or compatibility flow.
     * @return mode value produced or selected by this code path
     */
    public synchronized AntiAliasingMode mode() {
        return config.mode;
    }

    /**
     * Coordinates record metrics enabled within the anti-aliasing render, configuration, or
     * compatibility flow.
     * @return record metrics enabled value produced or selected by this code path
     */
    public synchronized boolean recordMetricsEnabled() {
        return config.recordMetrics;
    }

    /**
     * Applies a mutation, re-sanitizes the config, and persists the new value.
     */
    public synchronized void edit(Consumer<AntiAliasingConfig> editor) {
        editor.accept(config);
        config.sanitize();
        save();
    }

    /**
     * Writes the current config to disk.
     */
    public synchronized void save() {
        try {
            Files.createDirectories(configPath.getParent());
            try (Writer writer = Files.newBufferedWriter(configPath)) {
                GSON.toJson(config, writer);
            }
        } catch (IOException exception) {
            SaltsAntiAliasing.LOGGER.error("Failed to save config to {}", configPath, exception);
        }
    }
}
