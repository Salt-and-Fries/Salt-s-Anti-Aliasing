package org.betterLostItems.salts_anti_aliasing.client.render.vulkan.dlss;

import java.io.*;
import java.nio.*;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.stream.Collectors;

/** Validates and privately imports a known-compatible NVIDIA runtime without executing it. */
public final class DlssRuntimeFiles {
    public static final String SUPPORTED_VERSION = "2.14.1";
    public static final String DOWNLOAD_URL =
            "https://github.com/NVIDIA-RTX/Streamline/releases/tag/v2.14.1";
    private static final Map<String, String> HASHES =
            Map.of(
                    "nvngx_dlss.dll",
                            "3975567b8943c53acce397f2b72380092f84f162d00b0d2c7d08a1025c563983",
                    "sl.interposer.dll",
                            "8c87c9499461da561edd529aa9bf7831d67d7b94ebb1c1a5ed54ef4934e1ea4c",
                    "sl.common.dll",
                            "82924a8954dd671e09351c5de0eb87ad0eb25b944cc9f9ab955ca1d9950de15d",
                    "sl.dlss.dll",
                            "73bf52c0cfaa5900a8f3f4a91306e4625e7cca696dfb305aae44c9f97b582e1f",
                    "NvLowLatencyVk.dll",
                            "2a77dc3e1c724b7eea5755be0ae7423752e79a2459fae72181a9f00e3507e5d6");
    private static final long MAX_FILE_BYTES = 160L * 1024 * 1024;

    private DlssRuntimeFiles() {}

    public static boolean supportedPlatform() {
        return DlssBridgeResolver.supportedPlatform();
    }

    public record Validated(Path directory, String identity) {}

    public static Validated validate(Path selection) throws IOException {
        return validate(selection, HASHES);
    }

    static Validated validate(Path selection, Map<String, String> expected) throws IOException {
        Path directory = locate(selection);
        var missing =
                expected.keySet().stream()
                        .sorted()
                        .filter(name -> !Files.isRegularFile(directory.resolve(name)))
                        .toList();
        if (!missing.isEmpty())
            throw new IOException(
                    "Missing required DLSS files: "
                            + String.join(", ", missing)
                            + ". Select the extracted Streamline "
                            + SUPPORTED_VERSION
                            + " bin/x64 folder.");
        for (var entry : expected.entrySet()) {
            Path file = directory.resolve(entry.getKey());
            checkWindowsX64Dll(file);
            try (InputStream input = Files.newInputStream(file)) {
                if (!entry.getValue().equals(DlssBridgeResolver.hash(input)))
                    throw new IOException(
                            "Unsupported or modified file: "
                                    + entry.getKey()
                                    + ". Use the unmodified production files from Streamline "
                                    + SUPPORTED_VERSION
                                    + ".");
            } catch (NoSuchAlgorithmException impossible) {
                throw new AssertionError(impossible);
            }
        }
        String canonical =
                expected.entrySet().stream()
                        .sorted(Map.Entry.comparingByKey())
                        .map(e -> e.getKey() + "=" + e.getValue())
                        .collect(Collectors.joining("\n"));
        try {
            return new Validated(
                    directory,
                    DlssBridgeResolver.hash(
                            new ByteArrayInputStream(canonical.getBytes(StandardCharsets.UTF_8))));
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }

    private static Path locate(Path selection) throws IOException {
        Path path = selection.toAbsolutePath().normalize();
        if (Files.isRegularFile(path)) {
            if (!path.getFileName().toString().equalsIgnoreCase("nvngx_dlss.dll"))
                throw new IOException("Select nvngx_dlss.dll, or use Choose runtime folder.");
            return path.getParent();
        }
        if (!Files.isDirectory(path))
            throw new IOException("The selected runtime location does not exist.");
        Path sdk = path.resolve("bin/x64");
        return Files.isDirectory(sdk) && !Files.isRegularFile(path.resolve("nvngx_dlss.dll"))
                ? sdk
                : path;
    }

    static void checkWindowsX64Dll(Path path) throws IOException {
        long size = Files.size(path);
        if (size < 90 || size > MAX_FILE_BYTES)
            throw new IOException("Invalid Windows DLL: " + path.getFileName());
        try (var channel = FileChannel.open(path, StandardOpenOption.READ)) {
            ByteBuffer header = ByteBuffer.allocate(64).order(ByteOrder.LITTLE_ENDIAN);
            readFully(channel, header);
            long offset = Integer.toUnsignedLong(header.getInt(0x3c));
            if (header.getShort(0) != 0x5a4d || offset < 64 || offset > size - 26)
                throw new IOException("Invalid Windows DLL: " + path.getFileName());
            channel.position(offset);
            ByteBuffer pe = ByteBuffer.allocate(26).order(ByteOrder.LITTLE_ENDIAN);
            readFully(channel, pe);
            if (pe.getInt(0) != 0x00004550 || (pe.getShort(22) & 0x2000) == 0)
                throw new IOException("Not a Windows DLL: " + path.getFileName());
            if (Short.toUnsignedInt(pe.getShort(4)) != 0x8664
                    || Short.toUnsignedInt(pe.getShort(24)) != 0x20b)
                throw new IOException("Not a Windows x64 DLL: " + path.getFileName());
        }
    }

    private static void readFully(FileChannel channel, ByteBuffer buffer) throws IOException {
        while (buffer.hasRemaining())
            if (channel.read(buffer) < 0) throw new EOFException("Truncated DLL header");
    }

    public static Path install(Path selection, Path gameDirectory) throws IOException {
        return install(selection, gameDirectory, HASHES);
    }

    static Path install(Path selection, Path gameDirectory, Map<String, String> expected)
            throws IOException {
        Validated validated = validate(selection, expected);
        Path target =
                gameDirectory
                        .toAbsolutePath()
                        .resolve("salts_anti_aliasing/native/dlss/runtime")
                        .resolve(validated.identity());
        Files.createDirectories(target);
        for (var entry : expected.entrySet()) {
            Path source = validated.directory().resolve(entry.getKey());
            try {
                DlssBridgeResolver.install(
                        target.resolve(entry.getKey()),
                        entry.getValue(),
                        () -> Files.newInputStream(source));
            } catch (NoSuchAlgorithmException impossible) {
                throw new AssertionError(impossible);
            }
        }
        // Preserve available NVIDIA notices alongside the private runtime copy.
        copyNotice(
                validated.directory().resolve("nvngx_dlss.license.txt"),
                target.resolve("LICENSE-NVIDIA-DLSS.txt"));
        copyNotice(
                validated.directory().resolve("LICENSE-NVIDIA-DLSS.txt"),
                target.resolve("LICENSE-NVIDIA-DLSS.txt"));
        copyNotice(
                validated.directory().resolve("LICENSE-Streamline.txt"),
                target.resolve("LICENSE-Streamline.txt"));
        Path parent = validated.directory().getParent();
        if (parent != null && parent.getParent() != null)
            copyNotice(
                    parent.getParent().resolve("license.txt"),
                    target.resolve("LICENSE-Streamline.txt"));
        return target;
    }

    private static void copyNotice(Path source, Path target) throws IOException {
        if (!Files.isRegularFile(source) || Files.size(source) > 1024 * 1024) return;
        try (InputStream input = Files.newInputStream(source)) {
            String hash = DlssBridgeResolver.hash(input);
            DlssBridgeResolver.install(target, hash, () -> Files.newInputStream(source));
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }
}
