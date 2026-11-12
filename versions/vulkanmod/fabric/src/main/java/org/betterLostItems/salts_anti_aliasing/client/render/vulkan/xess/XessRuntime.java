package org.betterLostItems.salts_anti_aliasing.client.render.vulkan.xess;

import net.fabricmc.loader.api.FabricLoader;
import org.betterLostItems.salts_anti_aliasing.SaltsAntiAliasing;
import org.betterLostItems.salts_anti_aliasing.client.config.XessQualityPreset;
import org.betterLostItems.salts_anti_aliasing.client.metrics.MetricsDiagnostics;
import org.betterLostItems.salts_anti_aliasing.client.render.vulkan.VulkanNativeDeviceInfo;
import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/** Bundled, unmodified Intel runtime; requirements are queried before Vulkan device creation. */
public final class XessRuntime {
    private static final XessRuntime INSTANCE = new XessRuntime();
    private static final String ROOT = "assets/salts_anti_aliasing/native/xess/windows-x86_64/";
    private boolean attempted, loaded, deviceRequirementsAccepted, ready, contextCreated;
    private String message = "XeSS is waiting for Vulkan initialization";
    private String version = "";
    private String[] instanceRequirements = new String[0];
    private VulkanNativeDeviceInfo device;
    private XessQualityPreset lastPreset;
    private int lastWidth, lastHeight;
    private XessOptimalSettings settings;

    private XessRuntime() {}
    public static XessRuntime instance() { return INSTANCE; }
    public boolean isReady() { return ready; }
    public String message() { return message; }
    public String description() { return "Intel XeSS " + version + " / " + message; }

    public synchronized void preInitializeFromEnvironment() {
        if (attempted) return;
        attempted = true;
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String arch = System.getProperty("os.arch", "");
        if (!os.startsWith("windows") || !(arch.equals("amd64") || arch.equals("x86_64"))) {
            reject("Bundled XeSS requires Windows x64"); return;
        }
        try {
            Properties manifest = new Properties();
            try (InputStream in = resource("bundle.properties")) { manifest.load(in); }
            String identity = manifest.getProperty("salts_xess_bridge.dll.sha256") + manifest.getProperty("libxess.dll.sha256");
            Path directory = FabricLoader.getInstance().getGameDir().resolve("salts_anti_aliasing/native/xess")
                    .resolve(identity.substring(0,16) + identity.substring(64,80));
            Files.createDirectories(directory);
            for (String file : List.of("salts_xess_bridge.dll", "libxess.dll", "LICENSE-Intel-XeSS.txt", "third-party-programs.txt"))
                extract(directory, file, manifest.getProperty(file + ".sha256"));
            System.load(directory.resolve("salts_xess_bridge.dll").toAbsolutePath().toString());
            version = XessNativeBridge.load(directory.resolve("libxess.dll").toAbsolutePath().toString());
            instanceRequirements = XessNativeBridge.instanceRequirements();
            loaded = true;
            MetricsDiagnostics.info("xess.runtime", "runtime_loaded", "version", version,
                    "instanceRequirements", List.of(instanceRequirements));
        } catch (Exception | LinkageError e) {
            reject("XeSS runtime unavailable: " + e.getMessage());
            SaltsAntiAliasing.LOGGER.warn("Cannot initialize XeSS", e);
        }
    }

    public String[] instanceRequirements() { preInitializeFromEnvironment(); return instanceRequirements.clone(); }
    public String[] deviceRequirements(long instance, long physicalDevice) {
        if (!loaded) return new String[0];
        try { return XessNativeBridge.deviceRequirements(instance, physicalDevice); }
        catch (RuntimeException e) { reject("XeSS device requirements unavailable: " + e.getMessage()); return new String[0]; }
    }
    public void acceptDeviceRequirements() { if (loaded) deviceRequirementsAccepted = true; }
    public void reject(String reason) {
        if (!message.equals(reason)) {
            SaltsAntiAliasing.LOGGER.warn("XeSS unavailable: {}", reason);
            MetricsDiagnostics.warn("xess.runtime", "runtime_unavailable", "reason", reason);
        }
        ready = false; loaded = false; deviceRequirementsAccepted = false; message = reason;
    }

    public void onVulkanDeviceReady(VulkanNativeDeviceInfo info) {
        if (!deviceRequirementsAccepted || !info.complete()) return;
        device = info;
        try { ensureContext(); ready = true; message = "XeSS ready"; }
        catch (RuntimeException e) { reject("XeSS device initialization failed: " + e.getMessage()); }
        MetricsDiagnostics.info("xess.runtime", "device_initialized", "ready", ready, "status", message);
    }
    private void ensureContext() {
        if (!contextCreated) {
            if (device == null) throw new IllegalStateException("Vulkan device unavailable");
            XessNativeBridge.create(device.instance(), device.physicalDevice(), device.device());
            contextCreated = true;
        }
    }
    public XessOptimalSettings queryOptimalSettings(XessQualityPreset preset, int width, int height) {
        if (!ready) throw new IllegalStateException(message);
        ensureContext();
        if (settings == null || preset != lastPreset || width != lastWidth || height != lastHeight) {
            int[] size = XessNativeBridge.settings(width, height, preset.sdkValue());
            settings = new XessOptimalSettings(size[0], size[1]);
            lastPreset = preset; lastWidth = width; lastHeight = height;
            MetricsDiagnostics.info("xess.runtime", "settings_queried", "preset", preset,
                    "renderSize", settings, "outputWidth", width, "outputHeight", height);
        }
        return settings;
    }
    public int evaluate(long command, long[] handles, int[] formats, int width, int height,
                        float jitterX, float jitterY, boolean reset) {
        if (!ready || !contextCreated) throw new IllegalStateException(message);
        return XessNativeBridge.execute(command, handles, formats, width, height, jitterX, jitterY, reset);
    }
    public void releaseRenderResources() {
        if (contextCreated) { XessNativeBridge.destroy(); contextCreated = false; }
        settings = null;
    }
    public void shutdown() { releaseRenderResources(); ready = false; device = null; }

    private static InputStream resource(String file) throws IOException {
        InputStream in = XessRuntime.class.getClassLoader().getResourceAsStream(ROOT + file);
        if (in == null) throw new IOException("Missing XeSS bundle resource " + file);
        return in;
    }
    private static String hash(Path path) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream in = Files.newInputStream(path)) {
            byte[] buffer = new byte[65536]; int count;
            while ((count = in.read(buffer)) != -1) digest.update(buffer, 0, count);
        }
        return HexFormat.of().formatHex(digest.digest());
    }
    private static void extract(Path directory, String file, String expected) throws Exception {
        if (expected == null || !expected.matches("[0-9a-f]{64}")) throw new IOException("Invalid XeSS bundle manifest");
        Path target = directory.resolve(file);
        if (Files.isRegularFile(target) && expected.equals(hash(target))) return;
        Path temporary = Files.createTempFile(directory, file, ".tmp");
        try {
            try (InputStream in = resource(file)) { Files.copy(in, temporary, StandardCopyOption.REPLACE_EXISTING); }
            if (!expected.equals(hash(temporary))) throw new IOException("XeSS bundle checksum mismatch: " + file);
            try { Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException e) { Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temporary); }
    }
}
