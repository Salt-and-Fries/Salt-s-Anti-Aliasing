# Salt's Anti Aliasing DLSS Bridge

This native project is optional. The Java mod builds and runs without it; DLSS is disabled until a
local bridge DLL, Streamline plugin path, and NVIDIA application ID are configured.

Example Windows x64 build:

```powershell
cmake -S native/dlss_bridge -B build/dlss_bridge `
  -DSALTS_DLSS_WITH_STREAMLINE=ON `
  -DSALTS_STREAMLINE_SDK=C:/SDKs/Streamline
cmake --build build/dlss_bridge --config Release
```

Runtime configuration can be supplied with either config JSON fields or these overrides:

```text
salts.dlss.bridgePath / SALTS_DLSS_BRIDGE_PATH
salts.dlss.pluginPath / SALTS_DLSS_PLUGIN_PATH
salts.dlss.logPath / SALTS_DLSS_LOG_PATH
salts.dlss.applicationId / SALTS_DLSS_APPLICATION_ID
```

Do not commit NVIDIA Streamline/DLSS binaries or application IDs into this repository.
