package org.betterLostItems.salts_anti_aliasing.client.render.vulkan.dlss;

/**
 * Runtime availability for the optional DLSS stack.
 */
public enum DlssRuntimeStatus {
    NOT_CONFIGURED("DLSS is not configured"),
    BRIDGE_PATH_MISSING("DLSS JNI bridge path is missing"),
    BRIDGE_LOAD_FAILED("DLSS JNI bridge failed to load"),
    PLUGIN_PATH_MISSING("NVIDIA Streamline plugin path is missing"),
    APPLICATION_ID_MISSING("NVIDIA application ID is missing"),
    VULKAN_DEVICE_MISSING("Vulkan device information is not available yet"),
    INITIALIZATION_FAILED("NVIDIA Streamline initialization failed"),
    UNSUPPORTED("DLSS is not supported by this GPU, driver, or Streamline runtime"),
    READY("DLSS is ready");

    private final String message;

    DlssRuntimeStatus(String message) {
        this.message = message;
    }

    public boolean ready() {
        return this == READY;
    }

    public String message() {
        return message;
    }
}
