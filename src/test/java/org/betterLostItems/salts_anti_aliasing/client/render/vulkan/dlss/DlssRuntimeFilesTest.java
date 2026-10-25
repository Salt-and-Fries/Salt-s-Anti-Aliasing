package org.betterLostItems.salts_anti_aliasing.client.render.vulkan.dlss;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.*;
import java.nio.*;
import java.nio.file.*;
import java.util.*;

final class DlssRuntimeFilesTest {
    @TempDir Path root;
    private static final List<String> FILES =
            List.of(
                    "nvngx_dlss.dll",
                    "sl.interposer.dll",
                    "sl.common.dll",
                    "sl.dlss.dll",
                    "NvLowLatencyVk.dll");

    private byte[] dll(int marker) {
        var buffer = ByteBuffer.allocate(512).order(ByteOrder.LITTLE_ENDIAN);
        buffer.putShort(0, (short) 0x5a4d).putInt(0x3c, 128);
        buffer.putInt(128, 0x00004550)
                .putShort(132, (short) 0x8664)
                .putShort(150, (short) 0x2000)
                .putShort(152, (short) 0x20b);
        buffer.put(400, (byte) marker);
        return buffer.array();
    }

    private Map<String, String> fixture(Path folder) throws Exception {
        Files.createDirectories(folder);
        Map<String, String> hashes = new HashMap<>();
        for (int i = 0; i < FILES.size(); i++) {
            byte[] bytes = dll(i);
            Files.write(folder.resolve(FILES.get(i)), bytes);
            hashes.put(FILES.get(i), DlssBridgeResolver.hash(new ByteArrayInputStream(bytes)));
        }
        return hashes;
    }

    @Test
    void selectingMainDllOrSdkRootFindsTheSameCompleteSet() throws Exception {
        Path sdk = root.resolve("SDK"), folder = sdk.resolve("bin/x64");
        var hashes = fixture(folder);
        var file = DlssRuntimeFiles.validate(folder.resolve("nvngx_dlss.dll"), hashes);
        var whole = DlssRuntimeFiles.validate(sdk, hashes);
        assertEquals(folder, whole.directory());
        assertEquals(file.identity(), whole.identity());
    }

    @Test
    void missingCompanionsAreNamedBeforeAnyImport() throws Exception {
        Path folder = root.resolve("download");
        var hashes = fixture(folder);
        Files.delete(folder.resolve("sl.common.dll"));
        var failure =
                assertThrows(
                        IOException.class,
                        () -> DlssRuntimeFiles.install(folder, root.resolve("game"), hashes));
        assertTrue(failure.getMessage().contains("sl.common.dll"));
        assertFalse(Files.exists(root.resolve("game")));
    }

    @Test
    void rejectsWrongSelectedFileAndInvalidPeHeaders() throws Exception {
        Path folder = root.resolve("download");
        var hashes = fixture(folder);
        assertThrows(
                IOException.class,
                () -> DlssRuntimeFiles.validate(folder.resolve("sl.common.dll"), hashes));
        byte[] data = dll(1);
        ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).putInt(0x3c, -1);
        Files.write(folder.resolve("nvngx_dlss.dll"), data);
        assertThrows(IOException.class, () -> DlssRuntimeFiles.validate(folder, hashes));
    }

    @Test
    void rejects32BitDllEvenWhenItsChecksumMatches() throws Exception {
        Path folder = root.resolve("download");
        var hashes = fixture(folder);
        byte[] data = dll(2);
        ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).putShort(132, (short) 0x14c);
        Files.write(folder.resolve("nvngx_dlss.dll"), data);
        hashes.put("nvngx_dlss.dll", DlssBridgeResolver.hash(new ByteArrayInputStream(data)));
        assertTrue(
                assertThrows(IOException.class, () -> DlssRuntimeFiles.validate(folder, hashes))
                        .getMessage()
                        .contains("x64"));
    }

    @Test
    void rejectsModifiedRuntimeDespiteValidArchitecture() throws Exception {
        Path folder = root.resolve("download");
        var hashes = fixture(folder);
        Files.write(folder.resolve("nvngx_dlss.dll"), dll(99));
        assertTrue(
                assertThrows(IOException.class, () -> DlssRuntimeFiles.validate(folder, hashes))
                        .getMessage()
                        .contains("Unsupported or modified"));
    }

    @Test
    void privateCopySurvivesSourceChangesAndPreservesAvailableNotices() throws Exception {
        Path folder = root.resolve("download");
        var hashes = fixture(folder);
        Files.writeString(folder.resolve("nvngx_dlss.license.txt"), "Original NVIDIA terms");
        Path installed =
                DlssRuntimeFiles.install(
                        folder.resolve("nvngx_dlss.dll"), root.resolve("game"), hashes);
        assertNotEquals(folder, installed);
        for (String name : FILES)
            assertArrayEquals(
                    Files.readAllBytes(folder.resolve(name)),
                    Files.readAllBytes(installed.resolve(name)));
        assertEquals(
                "Original NVIDIA terms",
                Files.readString(installed.resolve("LICENSE-NVIDIA-DLSS.txt")));
        byte[] original = Files.readAllBytes(installed.resolve("nvngx_dlss.dll"));
        Files.write(folder.resolve("nvngx_dlss.dll"), dll(99));
        assertArrayEquals(original, Files.readAllBytes(installed.resolve("nvngx_dlss.dll")));
        assertThrows(
                IOException.class,
                () -> DlssRuntimeFiles.install(folder, root.resolve("game"), hashes));
        assertArrayEquals(original, Files.readAllBytes(installed.resolve("nvngx_dlss.dll")));
    }

    @Test
    void repairsDamagedPrivateCacheOnImport() throws Exception {
        Path folder = root.resolve("download");
        var hashes = fixture(folder);
        Path installed = DlssRuntimeFiles.install(folder, root.resolve("game"), hashes);
        Files.write(installed.resolve("sl.common.dll"), new byte[] {1, 2, 3});
        assertEquals(installed, DlssRuntimeFiles.install(folder, root.resolve("game"), hashes));
        assertArrayEquals(
                Files.readAllBytes(folder.resolve("sl.common.dll")),
                Files.readAllBytes(installed.resolve("sl.common.dll")));
    }

    @Test
    void windowsExecutablesCannotMasqueradeAsDlls() throws Exception {
        Path path = root.resolve("application.dll");
        byte[] data = dll(0);
        ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).putShort(150, (short) 0);
        Files.write(path, data);
        assertThrows(IOException.class, () -> DlssRuntimeFiles.checkWindowsX64Dll(path));
    }
}
