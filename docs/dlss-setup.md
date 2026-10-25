# Connecting a user-downloaded DLSS runtime

Requirements: Windows x64, a supported NVIDIA RTX GPU and driver, Minecraft's Vulkan backend,
and this mod. Gradle `runClient` supports the same setup as a launcher installation.

1. Open **Video Settings → Anti Aliasing Settings** and choose **DLSS Super Resolution** in the
   method dropdown. DLSS is always selectable. Until it is ready, its controls are disabled and
   **Set up DLSS** appears directly below Advanced controls.
2. Click **Download NVIDIA runtime**. It opens NVIDIA's official
   [Streamline 2.14.1 release](https://github.com/NVIDIA-RTX/Streamline/releases/tag/v2.14.1).
   Download **`streamline-sdk-v2.14.1.zip`** from Assets and extract it completely. The ARM and
   Source code archives do not contain the required Windows x64 runtime set.
3. Click **Connect nvngx_dlss.dll...** and select `bin/x64/nvngx_dlss.dll` from the extracted SDK.
   Alternatively, **Choose runtime folder...** accepts `bin/x64` or the extracted SDK root.
4. Wait for confirmation. The mod validates and copies the matching runtime into this Minecraft
   instance. You can remove the original download afterward.
5. Quit Minecraft completely and launch it again. Select **Vulkan** in Video Settings if needed
   (that backend change also requires a restart), then select **DLSS Super Resolution** in
   Anti Aliasing Settings. The runtime status reports readiness or the reason it cannot run.
   The setup button changes to **Change DLSS setup...** once a runtime folder is configured,
   allowing you to reconnect it later. It is hidden when another AA method is selected.

### Quality and model controls

Within the existing DLSS method, the quality slider starts with **DLAA (Native)** to the left
of **Quality**, followed by Balanced, Performance, Ultra Performance and Auto. DLAA renders
the scene at output resolution and uses DLSS for anti aliasing without upscaling.

Enable **Advanced controls** for **DLSS model request: Auto / K / M / L**. Auto leaves the model choice
to NVIDIA's runtime; the other options request that preset for every quality mode, including
DLAA. The supported runtime provides all three on RTX GPUs, including RTX 3060 Ti. M/L can
have a substantial performance cost on RTX 20/30 cards. NVIDIA App overrides can supersede
the requested preset. See [NVIDIA's model guidance](https://www.nvidia.com/en-us/geforce/news/dlss-4-5-super-resolution-available-now/).

These controls need no additional NVIDIA files. Model and quality changes apply in a world
without restarting; initial runtime setup still requires the restart described above.

### Motion, color and diagnostic controls

The DLSS path supplies camera reprojection plus matched model, item, moving-block,
block-model and custom quad/triangle geometry. Billboard particles contribute their actual
previous/current positions, rotation and scale. Atlas alpha and scene depth reject transparent
holes and occluded particles; overlapping translucent layers use the nearest dominant sample.
The **Particle motion opacity threshold** adjusts that dominance decision. One velocity per
pixel cannot represent several independently moving transparent layers exactly.

**Object motion geometry budget** offers 262K, 1M (default) and 2M captured vertices. Truncated
meshes are skipped as complete units. The coverage diagnostic reports new topology, capped
meshes and unidentified intercepted submissions. It measures the hooked renderer families,
not universal coverage of third-party renderers. Newly visible objects lack previous geometry;
custom text, procedural effects and renderer replacements can need dedicated adapters.

**Source color encoding** defaults to Auto: the standard 8-bit vanilla scene uses perceptual SDR;
a floating-point upstream target selects linear/HDR processing with RGBA16F scene/output
targets. An explicit override handles renderers whose encoding differs from that convention.
This control describes the incoming values; it does not convert gamma-encoded content into
linear light or enable HDR display output. NVIDIA auto exposure is used. Upstream renderers
that apply pre-exposure, their own tone mapping, or nonstandard attachments require a matching
integration and validation; arbitrary shader packs are not certified by the vanilla tests.

The legacy DLSS reactive-mask controls are removed because the supported K/M/L models do
not consume that mask. FSR keeps its own supported reactive controls. MIP bias remains
optional and terrain-specific: compare native DLAA before increasing negative bias, especially
with high-frequency resource packs. Entity/font textures without mip chains do not benefit
from blindly applying the terrain bias.

The memory display separates directly owned AA targets from NVIDIA's SDK feature estimate.
Neither value is total process or GPU memory. The model display reports a request, since an
NVIDIA App override can change the active choice.

Select the production `bin/x64` files, rather than development, debug or other architecture
variants. All five files must come from the supported, unmodified release:

```text
nvngx_dlss.dll
sl.interposer.dll
sl.common.dll
sl.dlss.dll
NvLowLatencyVk.dll
```

The mod checks Windows x64 DLL headers and pinned SHA-256 hashes before executing native code.
It rejects missing, modified, mixed or unsupported versions with a named error. Cancelling or a
failed import keeps the previous configuration. The Download button only opens NVIDIA's page.

The public jar contains the mod's own JNI bridge and excludes NVIDIA runtime DLLs. Imports are
stored in `<game directory>/salts_anti_aliasing/native/dlss/runtime/<content hash>/`. Available
NVIDIA license notices are copied alongside them and can be viewed under Credits and Licenses.

## Gradle development

With a plain launch, use the setup screen above:

```powershell
.\gradlew.bat runClient
```

This launch uses `run/26.3/config/salts_anti_aliasing.json`, separately from your normal launcher
instance. Each instance needs its own saved setup. The mod supplies and extracts its matching
bridge automatically; you do not need to find a separate bridge DLL.

For a temporary development override, point directly to the production runtime directory:

```powershell
.\gradlew.bat runClient -PdlssRuntimeDirectory="C:/SDKs/Streamline/bin/x64"
```

The override takes priority over the saved runtime for that process and does not copy NVIDIA DLLs
into the mod jar. Remove the flag to use the saved setup again. A plain `build` also produces a jar
without NVIDIA DLLs, even if the property is supplied. Custom bridge, application ID and log
overrides are documented in [the bridge README](../native/dlss_bridge/README.md).
