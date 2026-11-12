package org.betterLostItems.salts_anti_aliasing.client.render.vulkan.dlss;

import net.fabricmc.loader.api.FabricLoader;

import org.betterLostItems.salts_anti_aliasing.SaltsAntiAliasing;

import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;

/** Extracts only the mod's JNI bridge. NVIDIA executable files are supplied by the player. */
final class DlssBridgeResolver {
    private static Path bridge;

    private DlssBridgeResolver() {}

    static boolean supportedPlatform() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        return os.startsWith("windows") && (arch.equals("amd64") || arch.equals("x86_64"));
    }

    static synchronized Optional<Path> resolve() {
        if (!supportedPlatform()) return Optional.empty();
        if (bridge != null) return Optional.of(bridge);
        String resource =
                "assets/salts_anti_aliasing/native/dlss/windows-x86_64/salts_dlss_bridge.dll";
        try (InputStream input =
                DlssBridgeResolver.class.getClassLoader().getResourceAsStream(resource)) {
            if (input == null) return Optional.empty();
            byte[] bytes = input.readAllBytes();
            String hash = hash(new ByteArrayInputStream(bytes));
            Path directory =
                    FabricLoader.getInstance()
                            .getGameDir()
                            .resolve(SaltsAntiAliasing.MOD_ID)
                            .resolve("native/dlss/bridge")
                            .resolve(hash);
            Files.createDirectories(directory);
            Path target = directory.resolve("salts_dlss_bridge.dll");
            install(target, hash, () -> new ByteArrayInputStream(bytes));
            bridge = target.toAbsolutePath();
            return Optional.of(bridge);
        } catch (IOException | NoSuchAlgorithmException | RuntimeException exception) {
            SaltsAntiAliasing.LOGGER.warn("Unable to extract the mod's DLSS bridge", exception);
            return Optional.empty();
        }
    }

    @FunctionalInterface
    interface Source {
        InputStream open() throws IOException;
    }

    /** Atomic verified copy: changed inputs never replace a valid installed file. */
    static void install(Path target, String expectedHash, Source source)
            throws IOException, NoSuchAlgorithmException {
        if (Files.isRegularFile(target)) {
            try (InputStream existing = Files.newInputStream(target)) {
                if (expectedHash.equals(hash(existing))) return;
            }
        }
        Path temporary =
                Files.createTempFile(target.getParent(), target.getFileName().toString(), ".tmp");
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = source.open()) {
                if (input == null)
                    throw new IOException("Missing DLSS file: " + target.getFileName());
                try (var verified = new DigestInputStream(input, digest)) {
                    Files.copy(verified, temporary, StandardCopyOption.REPLACE_EXISTING);
                }
            }
            if (!expectedHash.equals(HexFormat.of().formatHex(digest.digest())))
                throw new IOException(
                        "DLSS file changed or failed verification: " + target.getFileName());
            try {
                Files.move(
                        temporary,
                        target,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    static String hash(InputStream input) throws IOException, NoSuchAlgorithmException {
        var digest = MessageDigest.getInstance("SHA-256");
        byte[] buffer = new byte[65536];
        int count;
        while ((count = input.read(buffer)) != -1) digest.update(buffer, 0, count);
        return HexFormat.of().formatHex(digest.digest());
    }
}
