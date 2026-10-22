# Salt's Anti Aliasing DLSS Bridge

Release jars can include the Windows x64 production runtime and extract it automatically. Players
install the jar normally and select DLSS; no external paths or personal application ID are needed.
Unbundled developer builds still accept explicit bridge and Streamline paths.

When NVIDIA has not issued an application ID, initialization uses this mod's stable GUID and custom
engine version, as documented in the DLSS Programming Guide sections 5.2 and 5.2.1. If NVIDIA issues
an application ID later, supplying it selects that initialization path instead. This identification
mechanism is separate from the SDK license's distribution, attribution, and release-notification terms.

Example Windows x64 build:

```powershell
cmake -S native/dlss_bridge -B build/dlss_bridge `
  -DSALTS_DLSS_WITH_STREAMLINE=ON `
  -DSALTS_STREAMLINE_SDK=C:/SDKs/Streamline `
  -DSALTS_VULKAN_HEADERS=C:/SDKs/Vulkan-Headers
cmake --build build/dlss_bridge --config Release

.\native\dlss_bridge\Stage-ReleaseRuntime.ps1 `
  -StreamlineSdk C:/SDKs/Streamline `
  -BridgeDll build/dlss_bridge/Release/salts_dlss_bridge.dll
.\gradlew.bat build -PdlssRuntimeDirectory=build/dlss-release-runtime
```

Runtime configuration can be supplied with either config JSON fields or these overrides:

```text
salts.dlss.bridgePath / SALTS_DLSS_BRIDGE_PATH
salts.dlss.pluginPath / SALTS_DLSS_PLUGIN_PATH
salts.dlss.logPath / SALTS_DLSS_LOG_PATH
salts.dlss.applicationId / SALTS_DLSS_APPLICATION_ID
```

Do not commit NVIDIA Streamline/DLSS binaries or application IDs into this repository.

The staging script uses the SDK's production `bin/x64` directory, verifies NVIDIA signatures, and
includes the Streamline and DLSS licenses and `END-USER-TERMS.txt`. It excludes development tools and debug plugins. The
loader checks SHA-256 hashes, uses a content-specific cache, and replaces corrupted extracted files.

The included NVIDIA license requires notification before commercial release (including a plug-in
to a commercial application) and has separate attribution/marketing terms. Review the packaged
`LICENSE-NVIDIA-DLSS.txt` and submit the applicable release information at
https://developer.nvidia.com/sw-notification. An unanswered application-ID request is not that notification.
The mod's Credits and Licenses screen makes the bundled licenses and runtime terms accessible
from Video Settings and Mod Menu. The proposed NVIDIA credit and runtime terms must be included
in the release review with NVIDIA; packaging them does not establish trademark approval or
confirm that NVIDIA accepts the proposed mod-specific placement or end-user terms.
Official initialization guidance:
https://github.com/NVIDIA/DLSS/blob/main/doc/DLSS_Programming_Guide_Release.pdf

The Fabric 26.3 Java code requires bridge ABI 2 and rejects earlier DLLs. This bridge uses manual
Vulkan integration, queries device requirements before creation, tags reverse-Z depth and pixel
motion vectors, and invokes Streamline's common presentation hooks every frame. The current SDK
used for validation is Streamline 2.14.1.
