package org.betterLostItems.salts_anti_aliasing.client.render.vulkan.dlss;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;

class DlssBridgeResolverTest {
    @TempDir Path directory;
    private static final byte[] BYTES = {1, 2, 3, 4};

    private static String hash() throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(BYTES));
    }

    @Test
    void installsAndReusesVerifiedBytes() throws Exception {
        Path target = directory.resolve("runtime.dll");
        DlssBridgeResolver.install(target, hash(), () -> new ByteArrayInputStream(BYTES));
        assertArrayEquals(BYTES, Files.readAllBytes(target));
        DlssBridgeResolver.install(
                target,
                hash(),
                () -> {
                    fail("Verified cache should not be extracted again");
                    return null;
                });
    }

    @Test
    void repairsCorruptedCache() throws Exception {
        Path target = directory.resolve("runtime.dll");
        Files.write(target, new byte[] {9});
        DlssBridgeResolver.install(target, hash(), () -> new ByteArrayInputStream(BYTES));
        assertArrayEquals(BYTES, Files.readAllBytes(target));
    }

    @Test
    void rejectsMismatchedPayloadWithoutOverwritingExistingFile() throws Exception {
        Path target = directory.resolve("runtime.dll");
        Files.write(target, new byte[] {9});
        assertThrows(
                IOException.class,
                () ->
                        DlssBridgeResolver.install(
                                target, hash(), () -> new ByteArrayInputStream(new byte[] {8})));
        assertArrayEquals(new byte[] {9}, Files.readAllBytes(target));
        try (var paths = Files.list(directory)) {
            assertEquals(1, paths.count());
        }
    }
}
