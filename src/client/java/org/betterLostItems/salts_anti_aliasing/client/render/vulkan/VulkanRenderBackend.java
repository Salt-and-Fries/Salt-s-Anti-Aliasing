package org.betterLostItems.salts_anti_aliasing.client.render.vulkan;

import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import org.betterLostItems.salts_anti_aliasing.client.render.api.RenderBackend;
import org.betterLostItems.salts_anti_aliasing.client.render.api.RenderBackendType;
import org.betterLostItems.salts_anti_aliasing.client.render.api.RenderCapability;
import org.betterLostItems.salts_anti_aliasing.client.render.api.RenderTargetDescriptor;
import org.betterLostItems.salts_anti_aliasing.client.render.vulkan.dlss.DlssRuntime;
import org.betterLostItems.salts_anti_aliasing.client.render.vulkan.fsr.FsrRuntime;

import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Vulkan-facing backend descriptor for Minecraft's 26.2 GPU abstraction.
 */
public final class VulkanRenderBackend implements RenderBackend {
    private static final Set<RenderCapability> BASE_CAPABILITIES = EnumSet.of(
            RenderCapability.POST_PROCESSING,
            RenderCapability.SHARPENING,
            RenderCapability.MULTISAMPLE_AA,
            RenderCapability.INTERNAL_RESOLUTION,
            RenderCapability.SPATIAL_UPSCALING,
            RenderCapability.TEMPORAL_AA
    );

    private final Map<String, RenderTargetDescriptor> declaredTargets = new LinkedHashMap<>();

    /**
     * Handles type as part of the anti-aliasing render, configuration, or compatibility flow.
     * @return backend type implemented by this object
     */
    @Override
    public RenderBackendType type() {
        return RenderBackendType.VULKAN;
    }

    /**
     * Checks is available without mutating runtime or configuration state.
     * @return whether the requested condition is true
     */
    @Override
    public boolean isAvailable() {
        GpuDevice device = RenderSystem.tryGetDevice();
        if (device != null && device.getDeviceInfo() != null) {
            return isVulkanBackend(device.getDeviceInfo().backendName());
        }

        return isVulkanBackend(RenderSystem.getBackendDescription());
    }

    /**
     * Handles capabilities as part of the anti-aliasing render, configuration, or compatibility
     * flow.
     * @return capability set advertised by this backend
     */
    @Override
    public Set<RenderCapability> capabilities() {
        EnumSet<RenderCapability> capabilities = EnumSet.copyOf(BASE_CAPABILITIES);
        if (DlssRuntime.instance().isReady()) {
            capabilities.add(RenderCapability.VENDOR_UPSCALING);
        }
        if (FsrRuntime.instance().isUpscalingReady()) {
            capabilities.add(RenderCapability.FSR_UPSCALING);
        }
        if (FsrRuntime.instance().isFrameGenerationReady()) {
            capabilities.add(RenderCapability.FSR_FRAME_GENERATION);
        }
        return capabilities;
    }

    /**
     * Handles declare targets as part of the anti-aliasing render, configuration, or compatibility
     * flow.
     * @param targets targets value supplied by the caller or Minecraft callback
     */
    @Override
    public void declareTargets(Collection<RenderTargetDescriptor> targets) {
        declaredTargets.clear();
        for (RenderTargetDescriptor target : targets) {
            declaredTargets.put(target.id(), target);
        }
    }

    /**
     * Handles declared targets as part of the anti-aliasing render, configuration, or compatibility
     * flow.
     * @return render targets declared by the active backend
     */
    @Override
    public List<RenderTargetDescriptor> declaredTargets() {
        return List.copyOf(declaredTargets.values());
    }

    /**
     * Checks whether a backend label names Minecraft's Vulkan renderer.
     */
    private static boolean isVulkanBackend(String backendName) {
        return backendName != null && backendName.toLowerCase(Locale.ROOT).contains("vulkan");
    }
}
