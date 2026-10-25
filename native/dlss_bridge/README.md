# Salt's Anti Aliasing DLSS Bridge

Public jars include only this project's Windows x64 JNI bridge and the Streamline interface's
MIT license. NVIDIA runtime binaries are user supplied. Gradle's `verifyPublicJar` check rejects
NVIDIA runtime DLLs in the distributable. See [player setup](../../docs/dlss-setup.md).

When NVIDIA has not issued an application ID, initialization uses the mod's stable GUID and
custom engine version. An optional NVIDIA-issued application ID selects that initialization path.
Successful initialization does not indicate NVIDIA endorsement or distribution approval.

## Rebuilding the bridge

Use the production Streamline **2.14.1** SDK:

```powershell
cmake -S native/dlss_bridge -B build/dlss_bridge `
  -DSALTS_DLSS_WITH_STREAMLINE=ON `
  -DSALTS_STREAMLINE_SDK=C:/SDKs/Streamline `
  -DSALTS_VULKAN_HEADERS=C:/SDKs/Vulkan-Headers
cmake --build build/dlss_bridge --config Release
Copy-Item build/dlss_bridge/Release/salts_dlss_bridge.dll native/redist/dlss/windows-x86_64/
.\gradlew.bat build
```

The Java code requires bridge ABI 4 and rejects older DLLs. The bridge uses manual Vulkan
integration, queries device requirements before device creation, tags reverse-Z depth and pixel
motion vectors, and invokes Streamline's common presentation hooks each frame.

ABI 4 removes the unsupported modern-model bias-current-color mask, chains explicit Vulkan
subresource ranges (including depth aspect), initializes pinhole metadata, and passes the
source color encoding to the settings query. It exposes the SDK's estimated feature VRAM
separately from the mod's own textures. Java and the bundled bridge must be updated together.

The settings query includes a stable model-preset argument. Auto requests NVIDIA's
default; K/M/L populate every SR and DLAA preset field. DLAA uses `eDLAA` with native input
dimensions. These controls use the existing user-imported runtime, with no additional NVIDIA DLLs.

## Development runtime

```powershell
.\gradlew.bat runClient -PdlssRuntimeDirectory="C:/SDKs/Streamline/bin/x64"
```

This property sets `salts.dlss.pluginPath` for the development JVM. It does not add files to the
jar. The included matching bridge is extracted automatically when a runtime path is configured.
Alternatively, use the in-game setup screen, which saves the private runtime under `run/26.3`.

`Stage-ReleaseRuntime.ps1` remains an optional local staging utility. It checks production
signatures and copies runtime files and notices into an ignored build directory. Its output is
for local testing; Gradle does not bundle it. Do not commit NVIDIA binaries or application IDs.

Advanced overrides (Java property / environment variable):

```text
salts.dlss.bridgePath / SALTS_DLSS_BRIDGE_PATH
salts.dlss.pluginPath / SALTS_DLSS_PLUGIN_PATH
salts.dlss.logPath / SALTS_DLSS_LOG_PATH
salts.dlss.applicationId / SALTS_DLSS_APPLICATION_ID
```

NVIDIA runtime files are checked against the supported production set before native execution.
Changing runtime paths requires a full restart because Vulkan extensions and features must be
enabled before the device is created. NVIDIA's SDK license continues to govern its downloaded
software. Available notices are preserved beside the private runtime and exposed in Credits and
Licenses.

Official references:

- https://github.com/NVIDIA-RTX/Streamline/releases/tag/v2.14.1
- https://github.com/NVIDIA-RTX/Streamline/blob/main/docs/ProgrammingGuide.md
- https://github.com/NVIDIA/DLSS/blob/main/doc/DLSS_Programming_Guide_Release.pdf
