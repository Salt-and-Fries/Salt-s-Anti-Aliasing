package org.betterLostItems.salts_anti_aliasing.client.render.vulkan.dlss;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

final class DlssClearCompatibilityTest {
    @TempDir Path directory;

    @Test
    void writesLoaderCompatibleTypesAndAnAbsoluteLibraryPathWithSpaces() {
        Path library = directory.resolve("directory with spaces/salts_dlss_bridge.dll");
        var root = JsonParser.parseString(DlssClearCompatibility.manifest(library)).getAsJsonObject();
        assertEquals("1.2.0", root.get("file_format_version").getAsString());
        assertFalse(root.has("layer"));
        assertEquals(1, root.getAsJsonArray("layers").size());
        var layer = root.getAsJsonArray("layers").get(0).getAsJsonObject();
        // Vulkan's implementation_version field is a STRING. A JSON number can
        // leave old Windows loaders with an unusable layer record / null library path.
        assertTrue(layer.get("implementation_version").getAsJsonPrimitive().isString());
        assertEquals(library.toAbsolutePath(), Path.of(layer.get("library_path").getAsString()));
        assertEquals("saltsLayerNegotiateLoaderLayerInterfaceVersion", layer.getAsJsonObject("functions")
                .get("vkNegotiateLoaderLayerInterfaceVersion").getAsString());
    }
}
