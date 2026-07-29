# Salt's Anti Aliasing FSR Bridge

The release jar bundles the Windows x64 bridge DLL and AMD FidelityFX SDK v1.x Vulkan runtime under
`assets/salts_anti_aliasing/native/windows-x86_64/`. At runtime the mod extracts those files to
`.minecraft/salts_anti_aliasing/native/<version>/...` and loads the bridge from there.

This native project is still useful for maintainers and for users who want to override the bundled
runtime with locally built or newer binaries.

Example Windows x64 build:

```powershell
cmake -S native/fsr_bridge -B build/fsr_bridge `
  -DSALTS_FSR_WITH_FFX_SDK=ON `
  -DSALTS_FFX_SDK=C:/SDKs/FidelityFX-SDK-1.1.4
cmake --build build/fsr_bridge --config Release
```

Runtime configuration can be supplied with either config JSON fields or these overrides. Explicit
bridge/runtime overrides take precedence over the bundled DLLs:

```text
salts.fsr.bridgePath / SALTS_FSR_BRIDGE_PATH
salts.fsr.runtimePath / SALTS_FSR_RUNTIME_PATH
salts.fsr.logPath / SALTS_FSR_LOG_PATH
```

`runtimePath` may point directly to `amd_fidelityfx_vk.dll` or to the SDK/runtime directory that
contains it. If neither bridge nor runtime path is configured, the bundled Windows x64 runtime is
used automatically when available.

FSR3 frame generation is intentionally separate from FSR3 super resolution. The bridge only reports
frame generation as ready after the FidelityFX Vulkan frame-generation swapchain wraps Minecraft's
swapchain and the frame-generation context is created. If the SDK runtime, GPU, driver, surface, or
queue layout cannot support that, Minecraft falls back to its normal swapchain and the FSR3+FG option
stays unavailable rather than silently behaving like plain FSR3 upscaling.

The Vulkan frame-generation swapchain needs Minecraft's graphics queue plus two distinct auxiliary
queues, with one auxiliary queue supporting presentation for the window surface. This follows the AMD
FSR3 Vulkan swapchain model; devices/drivers where Minecraft only exposes one usable queue will not
enable frame generation.

Keep `native/redist/windows-x86_64/LICENSE-AMD-FidelityFX.txt` in sync with the bundled AMD runtime.
