# SGSR validation — 29 October 2026

SGSR 1 and SGSR 2 are implemented in the Fabric 26.3 branch. Qualification used Windows x64, Java 25.0.2, RTX 3060 Ti and NVIDIA driver 591.86. Snapdragon/Adreno testing is pending the external hardware handoff. No Qualcomm native runtime is needed by these shader paths.

## Build and reference checks

- `gradlew build`: **164 tests, zero failures/errors/skips**. Tests cover independent configuration, odd/small sizing, reverse-Z reprojection, reference variance constants and Vulkan 1.2 compilation of all six new fragments.
- Qualcomm source is pinned to `d926f074bcb9d714e179f1ce0fcb9ee2eeb5074e`; the importer verifies upstream hashes. The jar contains both SGSR 1 reference filters, all SGSR fragments, provenance and BSD notices. It contains no disposable audit classes or NVIDIA runtime DLLs.
- SGSR 1 uses the standard and optional directional filters; SGSR 2 uses the official recommended two-pass fragment path. [Implementation details](sgsr-setup.md) describe the depth, motion, color and border adaptations.

## Actual game checks

All four presets rendered for both versions. At 720p, Quality uses 853×480; at 1080p, Quality uses 1280×720. Tests exercised SGSR 1 pre-AA Off/FXAA/SMAA and directional edges, SGSR 2 camera pan/translation, moving geometry and particles, water/glass with improved transparency on/off, underwater color, native HUD and 4× MSAA hand rendering. Inspected captures show correct brightness and no persistent corruption or obvious long trails. Lower input resolutions lose detail; spatial shimmer and temporal rejection remain algorithmic limitations, not a guarantee of artifact-free output.

Resize to 1920×1080 and odd 1279×719, resource reload, teleport, mode/preset changes, world unload and normal shutdown passed. Injecting a failure produced an explicit native-scene fallback with zero jitter. Retry restored SGSR 2. Owned AA target bytes returned to zero after Off and world unload.

Sodium 0.9.2+mc26.3 passed Native/Quality/Performance for both versions, OIT, automatic MIP bias, reload and return from other upscalers. Five actual terrain/OIT variants were observed. Quality bound `SALTS_MIP_BIAS=-0.5`, preserving the chosen policy without the extra FSR/DLSS offset. Shared motion-renderer regression checks passed DLSS M/DLAA, DLSS L/Quality, FSR3 with frame generation and XeSS Native AA, then SGSR again.

Both SGSR entries were selected through the actual dropdown from Off. Quality controls, SGSR 1 advanced controls, HUD/hand output and the Qualcomm license viewer were visually inspected.

`launch02.log` and `launch03.log` used Khronos validation plus synchronization validation and contain no Vulkan validation VUIDs. The deliberate recovery error appears in `launch02.log`. Initial development `launch01.log` exposed GLES constructor precision syntax incompatible with desktop Shaderc; it was corrected before qualification. Offline authentication and host performance-counter warnings are unrelated to SGSR rendering.

## Motion and jitter

GPU-read SGSR 2 motion contained zero non-finite pixels. During the same 0.1-degree-per-frame pan, mean NDC magnitudes were 0.00217306 Native, 0.00217358 Quality and 0.00217299 Performance. Resolution-independent NDC values check the reference velocity units; a translation probe also produced finite nonzero motion.

Four jitter sign combinations were compared against a same-scene 4×-per-axis SSAA reference over 16 captures. The implemented `(+X, -Y)` upload conversion had mean absolute RGB error **1.561** on the 0–255 scale and frame variation **0.444**; flipped alternatives gave error 1.934–2.278 and variation 0.810–1.274. The audit multipliers `(1,1)` mean the implemented conversion without further sign changes. This checks convention consistency, not a perceptual ranking of upscalers.

Early short motion probes sampled outside their moving capture window; they are excluded from the motion conclusion above. The `frame-pan-*` and `frame-translation` captures/probes are the corrected checks.

## 1080p performance

Runs used 1,800 measured frames per setting after warm-up, no validation layer, metrics recording or Sodium, VSync disabled, uncapped frame rate, sharpening 0%, MIP bias Off, a frozen natural scene and no shader pack. SGSR 1 used SMAA pre-AA. These are complete frame intervals from the disposable harness, including scene work; they are not SGSR-only GPU timings.

| Mode / quality | Mean | 95th percentile | 99th percentile |
| --- | ---: | ---: | ---: |
| Off | 0.756 ms | 1.261 ms | 1.457 ms |
| SGSR 1 Native | 1.175 ms | 1.651 ms | 1.800 ms |
| SGSR 1 Quality | 0.871 ms | 1.396 ms | 1.594 ms |
| SGSR 1 Balanced | 0.787 ms | 1.327 ms | 1.411 ms |
| SGSR 1 Performance | 0.738 ms | 1.294 ms | 1.388 ms |
| SGSR 2 Native | 0.981 ms | 1.497 ms | 1.576 ms |
| SGSR 2 Quality | 0.788 ms | 1.326 ms | 1.416 ms |
| SGSR 2 Balanced | 0.732 ms | 1.288 ms | 1.395 ms |
| SGSR 2 Performance | 0.699 ms | 1.254 ms | 1.346 ms |
| SGSR 1 Quality, directional | 0.887 ms | 1.408 ms | 1.518 ms |

The final short runs had maximum frame intervals of 2.2–6.4 ms. Earlier diagnostic runs recorded periodic roughly 50 ms stalls while the synchronous metrics report writer was active; those results are retained separately and excluded from this table. These figures show low steady costs in this light scene, not a promise of stutter-free performance in every world. Driver, scene load, other mods and thermal limits affect results.

A normal-settings moving-camera run with the world ticking completed 60,000 SGSR 2 Native frames in 62.64 seconds (mean 1.044 ms, p99 1.922 ms, maximum 17.078 ms), followed by 30,000 SGSR 1 Quality frames in 26.03 seconds (mean 0.868 ms, p99 1.608 ms, maximum 6.214 ms). Neither run had a frame above 50 ms or a renderer failure. Metrics recording was disabled, as it is by default.

## Reload stress limitation

An additional zero-warm-up test combined continuous screenshots with immediate resize, preset changes and resource reload. Teleport/preset/resize transition captures were sound, but reload triggered a native Java 25 access violation on a shader compiler worker in `launch06.log`. Windows recorded `jvm.dll+0xb410`; the dump identifies a compiler thread with its stack pointer below the committed stack limit. This test inherited an old experimental LWJGL upcall-arena override from the development launcher. That override was removed from subsequent profiles. Ordinary reload checks in `launch02.log` and `launch03.log` passed.

Three further ordinary reloads under normal JVM settings passed across SGSR 2, SGSR 1 and SGSR 2 in `launch08.log`, with validation enabled and no VUIDs. The intensive automation anomaly remains a native JVM/compiler stability limitation; its precise cause is not established or claimed fixed. Avoid intensive screenshot automation during resource reload when testing this Java runtime, and include the Java version and crash log if reload fails. The jar does not set the experimental JVM override.

## Evidence and remaining qualification

Evidence is under `output/sgsr-validation-2026-10-29/`: static/detail/motion contact sheets, UI/license captures, JSON capture metadata, GPU motion summaries, performance samples, lifecycle results and launch logs. Raw sequences are retained in the disposable `run/sgsr-validation-20261029/screenshots/` profile. None of its harness classes or world edits ship in the public jar.

Adreno/ARM, other operating systems/drivers, HDR, arbitrary shader packs and every third-party renderer are not qualified. The [hardware tester guide](sgsr-testing.md) records the configuration, image and thermal/performance feedback needed next. The build is ready for that test; universal absence of artifacts cannot be established from one desktop GPU.
