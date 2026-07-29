package org.betterLostItems.salts_anti_aliasing.client.render.vulkan.fsr;

import net.fabricmc.loader.api.FabricLoader;
import org.betterLostItems.salts_anti_aliasing.SaltsAntiAliasing;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Extracts bundled Windows x64 AMD FSR native files from the mod jar to a loadable directory.
 */
final class FsrBundledNativeResolver {
    private static final String PLATFORM_WINDOWS_X64 = "windows-x86_64";
    private static final String BRIDGE_FILE = "salts_fsr_bridge.dll";
    private static final String RUNTIME_FILE = "amd_fidelityfx_vk.dll";
    private static final String RESOURCE_ROOT =
            "assets/" + SaltsAntiAliasing.MOD_ID + "/native/" + PLATFORM_WINDOWS_X64 + "/";

    private FsrBundledNativeResolver() {
    }

    static Optional<FsrNativeConfiguration> resolve(String logPath) {
        if (!PLATFORM_WINDOWS_X64.equals(platformKey())) {
            return Optional.empty();
        }

        try {
            ResourceBytes bridge = readResource(BRIDGE_FILE);
            ResourceBytes runtime = readResource(RUNTIME_FILE);
            String bundleHash = hashText(bridge.sha256() + runtime.sha256()).substring(0, 16);
            Path extractDirectory = FabricLoader.getInstance()
                    .getGameDir()
                    .resolve(SaltsAntiAliasing.MOD_ID)
                    .resolve("native")
                    .resolve(modVersion())
                    .resolve(PLATFORM_WINDOWS_X64)
                    .resolve(bundleHash);

            Files.createDirectories(extractDirectory);
            Path bridgePath = extractIfNeeded(bridge, extractDirectory.resolve(BRIDGE_FILE));
            extractIfNeeded(runtime, extractDirectory.resolve(RUNTIME_FILE));
            return Optional.of(new FsrNativeConfiguration(
                    bridgePath.toAbsolutePath().toString(),
                    extractDirectory.toAbsolutePath().toString(),
                    logPath
            ));
        } catch (IOException | NoSuchAlgorithmException exception) {
            SaltsAntiAliasing.LOGGER.warn("Unable to extract bundled AMD FSR native runtime", exception);
            return Optional.empty();
        }
    }

    private static ResourceBytes readResource(String fileName) throws IOException, NoSuchAlgorithmException {
        String resourcePath = RESOURCE_ROOT + fileName;
        try (InputStream stream = FsrBundledNativeResolver.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (stream == null) {
                throw new IOException("Missing bundled FSR native resource: " + resourcePath);
            }

            byte[] bytes = stream.readAllBytes();
            return new ResourceBytes(fileName, bytes, sha256(bytes));
        }
    }

    private static Path extractIfNeeded(ResourceBytes resource, Path target) throws IOException, NoSuchAlgorithmException {
        if (Files.isRegularFile(target) && resource.sha256().equals(sha256(Files.readAllBytes(target)))) {
            return target;
        }

        Path temp = target.resolveSibling(target.getFileName() + ".tmp");
        Files.write(temp, resource.bytes());
        try {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        }
        return target;
    }

    private static String platformKey() {
        String os = System.getProperty("os.name", "").toLowerCase();
        String arch = System.getProperty("os.arch", "").toLowerCase();
        boolean windows = os.contains("win");
        boolean x64 = arch.equals("amd64") || arch.equals("x86_64");
        return windows && x64 ? PLATFORM_WINDOWS_X64 : "";
    }

    private static String modVersion() {
        return FabricLoader.getInstance()
                .getModContainer(SaltsAntiAliasing.MOD_ID)
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse("dev")
                .replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private static String sha256(byte[] bytes) throws NoSuchAlgorithmException {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private static String hashText(String value) throws NoSuchAlgorithmException {
        return sha256(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private record ResourceBytes(String fileName, byte[] bytes, String sha256) {
    }
}
