package org.betterLostItems.salts_anti_aliasing.client.render.vulkan.dlss;

import com.google.gson.Gson;
import net.fabricmc.loader.api.FabricLoader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.List;
import java.util.LinkedHashMap;

/** Private process-local Vulkan layer manifest; contains only this project's bridge path. */
final class DlssClearCompatibility {
    private DlssClearCompatibility() {}

    static Path prepare(String bridgePath) throws IOException {
        Path directory = FabricLoader.getInstance().getGameDir()
                .resolve("salts_anti_aliasing/native/dlss/vulkan-layer");
        Files.createDirectories(directory);
        Files.writeString(directory.resolve("dlss-clear-layer.json"), manifest(Path.of(bridgePath)));
        return directory;
    }

    static String manifest(Path bridge) {
        var manifest = new LinkedHashMap<String, Object>();
        manifest.put("file_format_version", "1.2.0");
        manifest.put("layers", List.of(Map.of(
                "name", "VK_LAYER_SALTS_dlss_clear_compat",
                "type", "GLOBAL",
                "library_path", bridge.toAbsolutePath().toString().replace('\\', '/'),
                "api_version", "1.2.0",
                "implementation_version", "1",
                "description", "Salt's DLSS clear synchronization compatibility",
                "functions", Map.of(
                        "vkGetInstanceProcAddr", "saltsLayerGetInstanceProcAddr",
                        "vkGetDeviceProcAddr", "saltsLayerGetDeviceProcAddr",
                        "vkNegotiateLoaderLayerInterfaceVersion", "saltsLayerNegotiateLoaderLayerInterfaceVersion"))));
        return new Gson().toJson(manifest);
    }
}
