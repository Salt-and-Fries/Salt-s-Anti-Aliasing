package org.betterLostItems.salts_anti_aliasing.client.config;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

final class ConfigPersistenceTest {
    @TempDir Path directory;

    @Test
    void livePreviewCommitsOnSaveAndResetKeepsNativeOverrides() throws Exception {
        Path path = directory.resolve("config.json");
        Files.writeString(
                path,
                """
                {"configVersion":2,"mode":"FXAA","dlssBridgePath":"custom/bridge.dll",
                "dlssApplicationId":42,"fsrRuntimePath":"custom/amd_fidelityfx_vk.dll"}
                """);
        var constructor = ConfigManager.class.getDeclaredConstructor(Path.class);
        constructor.setAccessible(true);
        var manager = constructor.newInstance(path);
        manager.load();
        String migrated = Files.readString(path);
        manager.editLive(
                c -> {
                    c.mode = AntiAliasingMode.SMAA;
                    c.tuning.smaaQuality = RenderTuning.SmaaQuality.HIGH;
                });
        assertEquals(AntiAliasingMode.SMAA, manager.snapshot().mode);
        assertEquals(migrated, Files.readString(path));
        manager.save();
        assertTrue(Files.readString(path).contains("HIGH"));
        manager.resetControls();
        var reset = manager.snapshot();
        assertEquals(AntiAliasingMode.OFF, reset.mode);
        assertEquals("custom/bridge.dll", reset.dlssBridgePath);
        assertEquals(42, reset.dlssApplicationId);
        assertEquals("custom/amd_fidelityfx_vk.dll", reset.fsrRuntimePath);
    }
}
