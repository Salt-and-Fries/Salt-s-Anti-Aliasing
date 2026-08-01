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
  -DSALTS_FFX_SDK=C:/SDKs/FidelityFX-SDK-1.1.4 `
  -DSALTS_VULKAN_HEADERS=C:/SDKs/Vulkan-Headers
cmake --build build/fsr_bridge --config Release
```

Release builds use the FidelityFX SDK 1.1.4 headers (tag `v1.1.4`, commit
`c6efa6bf7f2027b3ec94f28578bb5965eabb9e55`). SDK support defaults to `ON` so an
accidental stub bridge cannot be mistaken for a working release binary.

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

The Vulkan frame-generation swapchain receives four distinct queue handles: Minecraft's graphics
queue plus private async-compute, presentation, and image-acquire queues reserved before device
creation. The presentation queue is revalidated against the real window surface. Devices or drivers
that cannot expose those queues keep FSR2/FSR3 upscaling available but do not advertise frame
generation.

Frame interpolation currently uses FidelityFX's synchronous workload mode. This is the SDK's
lower-memory path and avoids making HUD-less resource lifetimes depend on an overlapping async frame;
the dedicated async-compute role is still reserved and validated for the replacement swapchain.

Keep `native/redist/windows-x86_64/LICENSE-AMD-FidelityFX.txt` in sync with the bundled AMD runtime.
