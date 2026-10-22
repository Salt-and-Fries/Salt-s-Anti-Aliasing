package org.betterLostItems.salts_anti_aliasing.client.render.vulkan.dlss;

import net.fabricmc.loader.api.FabricLoader;
import org.betterLostItems.salts_anti_aliasing.SaltsAntiAliasing;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;
import java.util.Properties;

/** Extracts the production runtime included by release builds; explicit paths still take precedence. */
final class DlssBundledNativeResolver {
    private static final String ROOT = "assets/salts_anti_aliasing/native/dlss/windows-x86_64/";
    private static final String[] FILES = {"salts_dlss_bridge.dll", "sl.interposer.dll", "sl.common.dll",
            "sl.dlss.dll", "nvngx_dlss.dll", "NvLowLatencyVk.dll", "LICENSE-Streamline.txt",
            "LICENSE-NVIDIA-DLSS.txt", "NOTICE.txt", "END-USER-TERMS.txt"};
    private static boolean attempted;
    private static Path directory;

    private DlssBundledNativeResolver() {}

    static synchronized Optional<DlssNativeConfiguration> resolve(String logPath, int applicationId) {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        if (!os.startsWith("windows") || !(arch.equals("amd64") || arch.equals("x86_64"))) return Optional.empty();
        if (!attempted) {
            attempted = true;
            try (InputStream manifest = resource("bundle.properties")) {
                if (manifest == null) return Optional.empty();
                var properties = new Properties();
                properties.load(manifest);
                String bundleId = properties.getProperty("bundleId", "");
                if (!bundleId.matches("[a-f0-9]{64}")) throw new IOException("Invalid DLSS bundle ID");
                Path target = FabricLoader.getInstance().getGameDir().resolve(SaltsAntiAliasing.MOD_ID)
                        .resolve("native/dlss/windows-x86_64").resolve(bundleId);
                Files.createDirectories(target);
                for (String file : FILES) {
                    String expectedHash = properties.getProperty(file + ".sha256", "");
                    if (!expectedHash.matches("[a-f0-9]{64}")) throw new IOException("Missing DLSS checksum: " + file);
                    install(target.resolve(file), expectedHash, () -> resource(file));
                }
                directory = target.toAbsolutePath();
                SaltsAntiAliasing.LOGGER.info("Bundled DLSS runtime extracted to {}", directory);
            } catch (IOException | NoSuchAlgorithmException | RuntimeException exception) {
                SaltsAntiAliasing.LOGGER.warn("Bundled DLSS runtime unavailable; other modes remain usable", exception);
            }
        }
        if (directory == null) return Optional.empty();
        return Optional.of(new DlssNativeConfiguration(directory.resolve("salts_dlss_bridge.dll").toString(),
                directory.toString(), logPath, applicationId));
    }

    private static InputStream resource(String file) {
        return DlssBundledNativeResolver.class.getClassLoader().getResourceAsStream(ROOT + file);
    }

    @FunctionalInterface
    interface Source { InputStream open() throws IOException; }

    /** Verify both cached and newly extracted bytes; never replace a valid file with an incomplete copy. */
    static void install(Path target, String expectedHash, Source source) throws IOException, NoSuchAlgorithmException {
        if (Files.isRegularFile(target)) {
            try (InputStream existing = Files.newInputStream(target)) {
                if (expectedHash.equals(hash(existing))) return;
            }
        }
        Path temporary = Files.createTempFile(target.getParent(), target.getFileName().toString(), ".tmp");
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = source.open()) {
                if (input == null) throw new IOException("Missing DLSS resource " + target.getFileName());
                try (var verified = new DigestInputStream(input, digest)) {
                    Files.copy(verified, temporary, StandardCopyOption.REPLACE_EXISTING);
                }
            }
            if (!expectedHash.equals(HexFormat.of().formatHex(digest.digest()))) throw new IOException("DLSS checksum mismatch: " + target.getFileName());
            try { Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException ignored) { Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temporary); }
    }

    private static String hash(InputStream input) throws IOException, NoSuchAlgorithmException {
        var digest = MessageDigest.getInstance("SHA-256");
        byte[] buffer = new byte[65536];
        int count;
        while ((count = input.read(buffer)) != -1) digest.update(buffer, 0, count);
        return HexFormat.of().formatHex(digest.digest());
    }
}
