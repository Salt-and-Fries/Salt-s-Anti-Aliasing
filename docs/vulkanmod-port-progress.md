# VulkanMod Fabric port validation

All **23 official VulkanMod Fabric game targets** are implemented across 15 typed native adapter families. Minecraft **26.2 and 26.3** retain the built-in Vulkan backend. The implementation is on `multi-version-test`.

The root build produces **25 main jars** in `build/libs/`. It selects the actual `remapJar` output for mapped older releases and `jar` for 26.x, verifies metadata and native bundles, and removes stale collection files. Sources, development and validation jars are excluded. Building uses JDK 25, a 6 GiB Gradle heap and two workers; game runtimes use Java 17, 21 or 25 according to the [installation matrix](fabric-compatibility.md).

## Qualification: November 11, 2026

| Targets | Full packaged results |
| --- | --- |
| 1.18.2; 1.19.2–1.19.4; 1.20–1.20.4 | Nine Java 17 targets: PASS |
| 1.20.5–1.20.6; 1.21–1.21.5; 1.21.9–1.21.11 | Eleven Java 21 targets: PASS |
| 26.1–26.1.2 | Three Java 25 VulkanMod targets: PASS |
| Built-in Vulkan 26.2, 26.3 | Both targets with default, stable Sodium and beta Sodium: all six profiles PASS |

All **29 packaged runtime profiles** completed with **zero core, synchronization or shutdown validation errors**. Tests used the actual public jars, official dependencies, an isolated world, and an explicit Khronos synchronization-validation layer. Final logs are checked after client exit, when late native messages have flushed.

The [machine-readable validation report](fabric-validation-report.json) records exact collected jar SHA-256, canonical payload SHA-256, native DLL hashes, client runtimes, mode/quality checks, confirmed generated-present counters, and paths to final layer logs and completion markers. Every collected jar matches the artifact that was qualified. The rebuilt 1.20.1 jar received a fresh full run after final collection.

Testing used **Windows 11, NVIDIA GeForce RTX 3060 Ti, driver 591.86**. Java clients were Temurin 17.0.19, JDK 21.0.11 and JDK 25.0.3. Representative older, newer and built-in-renderer captures were visually inspected. These results cover the tested scenarios; other GPUs, drivers, operating systems, resource packs and mod combinations require separate qualification. This is not a performance benchmark.

The official [VulkanMod releases](https://modrinth.com/mod/vulkanmod/versions) were rechecked against the [Modrinth API](https://api.modrinth.com/v2/project/vulkanmod/version): all 23 advertised Fabric targets are covered, with no extras or omissions. VulkanMod does not advertise 1.21.6–1.21.8. Native jar SHA-512 pins are in `versions/vulkanmod/compatibility/official-targets.json`; Fabric API, Mod Menu and mapping pins are in `build-targets.json` beside it.

## Coverage

Each older target exercises all 15 rendering modes including the Off baseline, all 55 quality changes, MSAA samples and alpha-to-coverage, hand coverage, actual native SDK evaluations, confirmed generated frames, moving entities/items/falling blocks, glass, particles, terrain projection jitter and texture detail. It also checks motion/edge views, resize during frame generation, resource reload, retry, settings entry points, advanced controls, credits, DLSS setup and metrics. A completion marker is written after the disposable world closes.

Both built-in Vulkan targets exercise all 15 modes, native SDK evaluation and generated-frame confirmation, a multisampled hand target, texture detail, particles, settings entry and clean world/client shutdown in each of their three renderer profiles. Sodium remains optional and external to the distributables.

The backend passed **52 tests**, including real GPU readback, shader compilation/reflection, projection math, mip barriers, pipeline alias retirement and native ownership ordering. Packaged game runs cover LWJGL 3.3.1, 3.3.3 and 3.4.1. The modern suites passed **166 tests for 26.2 and 164 for 26.3**. The final root build passed all checks; no tests failed or were skipped.

## Rendering and lifecycle fixes

- The oldest Drawer backends use real dynamic attachments, correct color/depth resolves, framebuffer/sample-aware pipeline keys, acquired-image indexing, synchronized readback and the recorded frame slot's submission identity.
- Swapchain recreation invalidates attachment state even when FidelityFX reuses a proxy handle. Newer adapters size the scene against actual native attachments when Minecraft's reported window size has already changed.
- Both render-pass and KHR dynamic-rendering attachment LOAD operations receive color/depth read dependencies. Older presentation transitions preserve prior writes and proxy shader reads.
- Chunk arena uploads use the consuming graphics queue, with dependencies before and after each batch. Typed hooks cover singleton queues, the 0.3.9 manager, inline 0.4.2 copies and newer helper methods. Explicit overload descriptors survive production remapping; arena growth makes earlier writes visible to the copy's source reads.
- Overlapping atlas copies synchronize the actual image, mip and array range even when the layout stays TRANSFER_DST. Native SDK evaluations also order reused private buffers, including DLSS transfer-cleared counters.
- Native object/VMA tracking retires resources in dependency order while preserving queue-owned objects until native queue cleanup. Cached samplers transfer back to SamplerManager in releases that own their cleanup. Retained VBOs and emulated main-target attachments close explicitly; image-map cleanup uses snapshots.
- Every direct native device-idle call drains FidelityFX presentation first. The SDK patch waits for presenter CPU completion as well as GPU timelines.
- Both DynamicTexture constructor overloads preserve lightmap/overlay allocation. Old descriptor caches prepare sampled textures in both binding overloads. Terrain UBO additions preserve existing offsets; geometry capture follows each native API.
- The 1.20 rendering bridge preserves block state, random reseeding, transforms, culling and AO. Fabric API pins avoid incompatible later shader hooks on 1.18.2 and 1.19.3. Legacy layout methods survive remapping.

## Native bundle and reproduction

FidelityFX SDK 1.1.4 commit `c6efa6bf7f2027b3ec94f28578bb5965eabb9e55` uses the checked-in correctness patch for storage formats, descriptor pools, line width, per-image presentation semaphores, composition-cache cleanup and presenter completion. JNI mutex initialization supports older Windows JREs. NVIDIA runtime DLLs remain user supplied; their test hashes are recorded without bundling them.

- AMD runtime SHA-256: `2C2377D25819656704A4158E76A9AF48DB5C2BF20688D99FB6CEC7C13E09347F`.
- FSR JNI bridge SHA-256: `908658015AA362FEB480C8587F8D4E3743CEC14E3838EFE2000CF89F61155D34`.

Use a current Khronos validation layer and enable synchronization validation explicitly, following its [configuration guide](https://github.com/KhronosGroup/Vulkan-ValidationLayers/blob/main/docs/updating_from_VK_EXT_validation_features.md):

```powershell
$env:VK_LAYER_PATH = 'C:/VulkanSDK/<version>/Bin'
$env:VK_INSTANCE_LAYERS = 'VK_LAYER_KHRONOS_validation'
$env:VK_LAYER_VALIDATE_SYNC = '1'
$env:VK_KHRONOS_VALIDATION_DEBUG_ACTION = 'VK_DBG_LAYER_ACTION_LOG_MSG'
.\gradlew.bat :1.20.1:fabric:runPackagedClientValidation `
  '-PclientJavaHome=C:/Java/jdk-17' `
  '-PdlssRuntimeDirectory=C:/SDKs/Streamline/bin/x64' `
  '-PvulkanValidationLogDirectory=build/validation/1.20.1'
```

Use the target's Java version and substitute its fully qualified task path. For 26.2/26.3 add `-PsodiumProfile=stable` or `beta` for optional Sodium profiles, with a fresh log directory for each. Omit `SALTS_VULKANMOD_TEST_MODES` and per-version `vulkanValidationModes` properties for a full older-target suite. GPU clients run sequentially.

The requested validation task fails on any validation error or missing log. Raw local evidence remains under `tmp/vulkanmod-port` and the isolated `run/<version>/packaged-validation*` directories; the report preserves its paths and checksums. Validation code stays outside every public jar.
