# XeSS validation — 29 October 2026

Intel XeSS Super Resolution and Native Anti-Aliasing are implemented in the Fabric 26.3 branch.
Qualification used an RTX 3060 Ti, NVIDIA driver 591.86, Windows x64, Java 25.0.2 and Intel's unmodified
XeSS-SR 2.0.2 runtime. The native DLL, headers and notices are pinned to Intel SDK commit
`de0fb9c1c510661c571164e1418ceca8101dab69`.

## Rendering and lifecycle

- All seven Intel presets rendered at both 720p and 1080p. Dimensions are queried from the SDK;
  at 1080p, Native AA is 1920×1080 and Quality is 1130×636.
- Camera panning/translation, foliage and thin detail, water/glass with improved transparency
  enabled/disabled, particles, moving entities, underwater color, native HUD and 4× MSAA hand
  rendering were exercised. No XeSS output corruption or persistent trails were observed in the
  inspected sequences. Lower input resolutions are visibly softer, as expected.
- Resize to 1920×1080 and odd 1279×719 output, resource reload, teleport, preset/method changes,
  world unload and normal shutdown completed. Runtime initialization works without a configured
  NVIDIA DLSS runtime.
- An injected scene failure produced an explicitly reported native-scene fallback with zero
  temporal jitter. Retry restored XeSS. The injected error is intentional in `launch07.log`.
- Sodium 0.9.2 with OIT and automatic MIP bias passed the same native/quality/ultra-performance
  render paths, reload and return from FSR3. Five actual Sodium scene pipeline variants were observed.
- The actual dropdown was opened and scrolled, and XeSS was selected from Off using SDL input.
  The Native AA quality control and Intel license viewer were visually inspected.
- Shared Vulkan startup changes were checked with DLSS model M/DLAA, model L/Quality, FSR3 frame
  generation, then XeSS Native AA and Quality. Qualification logs contain no Vulkan validation VUIDs
  or XeSS evaluation failures. The tests include synchronization validation.

Initial development launches exposed the current runtime's Vulkan 1.3 requirement and Streamline's
core private-data feature requirement. Both were fixed before qualification. Final qualification is
recorded in `launch03.log`, `launch04.log`, `launch06.log` and `launch11.log` under the evidence directory.
Recoverable Minecraft swapchain-out-of-date warnings occur around resize/reload. Offline authentication
and host performance-counter warnings are unrelated to XeSS.

## Input and image-quality checks

Intel's Vulkan integration guide and sample were used for the resource layouts, color encoding,
reversed depth, pixel velocities and jitter convention. The SDK receives linear SDR input, then its
output is encoded for display once. No second temporal resolve or forced sharpening is applied.

Static captures were compared with a same-scene 4×-per-axis SSAA reference, with sharpening disabled.
Testing all four jitter sign combinations favored the implemented `(+X, −Y)` SDK conversion: mean
absolute RGB error was 1.930 on the 0–255 scale, versus 2.157–2.447 after flipping an axis; temporal
variation also increased for the alternatives. This is a convention check, not a perceptual ranking
of different upscalers. XeSS remains softer than DLAA in the tested scene.

GPU-read motion probes contained zero non-finite values. Mean motion during the same 0.1-degree
per-frame pan scaled with input resolution: 1.332 pixels in Native AA, 0.784 in Quality and 0.445 in
Ultra Performance. This checks the pixel-velocity units and avoids an output/input scale mismatch.

## Steady performance

Final 1080p runs used 1,800 measured frames per preset after warm-up, without the validation layer,
VSync disabled, no shader pack, and a frozen natural scene. Values are complete frame intervals from
the disposable render harness, including normal scene work; they are not XeSS-only GPU timings.

| Preset | Mean frame time | 95th percentile | 99th percentile |
| --- | ---: | ---: | ---: |
| Native Anti-Aliasing | 2.991 ms | 3.311 ms | 3.570 ms |
| Quality | 2.685 ms | 3.023 ms | 3.304 ms |
| Performance | 2.530 ms | 2.908 ms | 3.116 ms |

These results apply to the tested scene and machine. Complex worlds, resource packs, shader packs,
other GPUs/drivers, HDR and every third-party renderer have not been certified.

## Build and evidence

`gradlew.bat build` passed all **158 tests**, including four new contract tests covering native feature
offsets, promoted Vulkan feature merging, SDK-sized jitter coverage and config preservation. The public
jar check verifies the bundled XeSS files against their SHA-256 manifest and retains the existing
exclusion of NVIDIA runtime binaries. No QA harness classes are included in the public jar.

The final jar, exact checksums, qualification logs, request/results files, source hashes and selected
screenshots are in [`output/xess-validation-2026-10-29`](../output/xess-validation-2026-10-29/).
[`validation.json`](../output/xess-validation-2026-10-29/validation.json) is the machine-readable
manifest. The development harness and test worlds stay outside the public mod.

See [setup](xess-setup.md), [native build and integration contract](../native/xess_bridge/README.md),
[Intel's pinned guide](https://github.com/intel/xess/blob/de0fb9c1c510661c571164e1418ceca8101dab69/doc/xess_sr_developer_guide_english.md)
and [Intel's Vulkan sample](https://github.com/intel/xess/blob/de0fb9c1c510661c571164e1418ceca8101dab69/samples/basic_sample_super_resolution_vk/triangle.cpp).
