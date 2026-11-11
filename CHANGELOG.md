# Changelog## Unreleased — SGSR 1 and SGSR 2- Add Qualcomm SGSR 1 spatial upscaling and the SGSR 2 two-pass fragment temporal path, with separate Native, Quality, Balanced and Performance settings.- Preserve reference filter math, linear color, encoded object motion, 32-phase jitter and history resets; adapt Minecraft reverse-Z depth and define border sampling.- Add SGSR 1 pre-AA, directional edges and edge controls, native HUD/hand handling, motion diagnostics, allocation accounting and failure/retry behavior.- Bundle BSD notices and pinned source provenance; provide a reproducible shader importer and Vulkan compilation tests. Validate on RTX 3060 Ti and provide an Adreno test handoff.

## Unreleased — Intel XeSS

- Add Intel XeSS Super Resolution, Native Anti-Aliasing and all official quality presets with a bundled, checksum-verified Windows x64 runtime and license notices.
- Integrate runtime-queried Vulkan requirements and input sizing, linear SDR color, reversed depth, camera/object/particle motion, temporal history resets, explicit resource barriers and native-resolution HUD/hand handling.
- Merge promoted Vulkan 1.3 features safely with Minecraft, DLSS and FSR; show unsupported/fallback status and preserve saved settings.
- Add integration contract tests and validate presets, motion, transparency, resize, reload and runtime transitions on RTX 3060 Ti.

## Unreleased — Minecraft 26.3 / Sodium and native runtime compatibility

- Repair DLSS M/L private-image clear synchronization with a process-local Vulkan layer in the mod's own bridge. Correct initial transitions' destination transfer scope inside DLSS evaluation, preserve model choices/inputs and avoid new GPU commands or changes to ordinary game/FSR barriers.
- Accept complete user-downloaded Streamline 2.14.16 / DLSS 310.9.16 runtime sets alongside existing 2.14.1 / 310.9.1 sets. Reject mixed releases, preserve old cache identities and show the detected runtime version in advanced settings. Continue excluding NVIDIA DLLs from public builds.
- Isolate asynchronous Windows/Java 25 pipeline compilation in a bounded pool with larger stacks after native dumps traced the startup/reload fault through Shaderc's Java include callback. Preserve inputs/futures and deferred GPU-finalization scheduling; expire idle threads. The shared-arena experiment did not prevent the fault and is not enabled globally or in development profiles.
- Correct promoted descriptor-indexing feature declarations for FSR-only Vulkan 1.2 sessions. Keep requested individual bits and omit the redundant extension declaration that required an additional umbrella bit.
- Add native clear-layer dispatch/scope/lifetime tests and regression coverage for complete runtime-set selection, mixed DLL rejection, loader manifest types, promoted Vulkan features and nested/erroring shader compilation futures.

- Apply optional terrain MIP bias to Sodium's terrain fragment shader, including OIT pipelines whose pipeline locations use the Minecraft namespace. Preserve the original layouts, compressed vertex bindings, push constants, render state and float attachment formats.
- Preserve Sodium's bulk vertex-writing API during entity motion capture. Read actual float-position offsets/strides before the delegate can overwrite its input, bound capture work by the motion budget, and reject partial/unknown geometry histories safely.
- Keep the Sodium adapter optional at class-loading time; retain the scalar capture path when Sodium is absent.
- Query and enable FidelityFX's required unformatted storage-image read/write features before device creation, and keep unsupported frame generation out of the capability list while retaining upscaling.
- Rebuild the bundled MIT-licensed FidelityFX Vulkan runtime with corrections for static line width, per-image presentation semaphores, failed-acquire signaling, duplicate semaphore allocation and UI/debug cache cleanup after presentation drains.
- Publish the native FG proxy handle only after initialization succeeds, and preserve RenderPearl's ownership of an old swapchain during failure/fallback.
- Add checksum-verified stable/beta Sodium development profiles with isolated settings and worlds. Keep Sodium and NVIDIA runtime DLLs out of public jars.
- Add native-buffer, shader-selection/idempotence and optional-class-loading regression tests. Validate actual scene pipelines, motion inputs, checker-texture LOD changes, generated frames, settings navigation, reload/resize/mode changes and direct exit.
- Preserve the original Sodium audit evidence and document the follow-up native-runtime repairs and remaining JVM uncertainty separately; see README qualification notes and `output/runtime-stability-fixes-2026-10-25/`.

## 1.1 — Minecraft 26.2

These are the changes introduced on the `fabric-26.2` branch since `fabric-26.1.2`.

### Platform And Compatibility

- Updated the mod from Minecraft 26.1.2 to Minecraft 26.2, including the matching Fabric Loader, Fabric API, Mod Menu, and Sodium API versions.
- Replaced the OpenGL rendering paths with native Vulkan implementations for anti-aliasing, supersampling, upscaling, temporal history, and multisample resolve.
- Anti-aliasing modes now require Minecraft's Vulkan renderer. Saved settings remain intact when Vulkan is unavailable.
- Restored MSAA availability when Sodium is installed.
- Improved Vulkan capability checks, supported-sample clamping, failure recovery, image-layout synchronization, and color-blit completion.

### New Upscaling Modes

- Added optional NVIDIA DLSS Super Resolution support with dedicated quality presets and a native bridge.
- Added AMD FSR2 Super Resolution.
- Added AMD FSR3 Super Resolution.
- Added a separate FSR3 Super Resolution + Frame Generation mode.
- Bundled the AMD FidelityFX Vulkan runtime and bridge for Windows x64, while retaining advanced path overrides.
- FSR3 Frame Generation can be selected only when its Vulkan swapchain path initializes successfully.
- Completed the end-to-end FSR3 Frame Generation implementation: FidelityFX now creates and presents real interpolated frames through its replacement swapchain, preserves a HUD-less world image for UI composition, survives resizing and mode switches, and shuts down cleanly instead of silently falling back to regular FSR3.

### Image Quality

- Reworked TAA history reprojection to remove heavy blur and reduce ghosting while retaining temporal edge stability.
- Corrected FSR2 and FSR3 render dimensions, jitter, depth data, motion vectors, exposure, and temporal inputs.
- Added proper linear-color processing around FidelityFX evaluation.
- Removed false sky and cloud parallax from FSR motion reconstruction.
- Added temporal masks to stabilize translucent boundaries such as water, foliage, and block edges.
- Corrected FSR3 Frame Generation reset behavior and HUDless-frame state handling.
- Moved the HUD-less frame capture after scene post effects so generated frames retain the complete world image without duplicating the GUI.
- Replaced the old wide, depth-weighted FXAA blur with the NVIDIA FXAA 3.11 Quality endpoint-search algorithm and high-quality preset-28 schedule, reducing softness and halos while preserving subpixel cleanup.
- Replaced the custom SMAA-like heuristic with the official SMAA 1x Ultra pipeline, including color-edge detection, horizontal/vertical/diagonal searches, crossing-edge classification, Area/Search lookup textures, corner handling, and the reference coverage resolve.
- Propagated internal render resolution correctly to shaders used by scaled rendering modes.
- Kept MSAA cutout textures binary by default, preventing distant transparent blocks and foliage from fading or stippling.
- Added an optional MSAA-only `Alpha to Coverage` toggle for players who prefer smoother cutout edges. It defaults to `Off` and applies live without requiring a restart.

### Settings And User Interface

- Rebuilt the Video Settings integration for Minecraft 26.2.
- Updated the Mod Menu configuration screen to use the same modal mode picker and dynamic mode-specific controls as Video Settings, including universal sharpness, MSAA alpha-to-coverage, SSAA through 800% with warnings, and DLSS/FSR quality controls.
- Fixed the crash that prevented the Video Settings screen from opening.
- Added a scrollable anti-aliasing mode popup that always renders above the settings buttons.
- Added wrapped, multi-line mode tooltips so descriptions remain readable near screen edges.
- Made the popup modal so controls behind it no longer highlight, change the cursor, scroll, or receive clicks.
- Added click-and-drag support for the popup scrollbar.
- Added mode-specific DLSS and FSR quality controls.
- Expanded the SSAA scale choices from 200% up to 800% per axis, with a localized performance and GPU-memory warning for values above 400% in the built-in, Mod Menu, and Sodium settings interfaces.
- SSAA targets beyond the GPU's texture or allocation limits now fall back to native resolution and can recover after lowering the scale or resizing the window instead of disabling scaled rendering for the rest of the session.
- Replaced combined sharpening modes with one independent `Sharpness` slider available for every mode, including `Off`.
- Sharpness now defaults to `0%`; existing `NIS Sharpen` and `FSR1 + RCAS` configurations migrate to their corresponding base modes.
- Added the MSAA `Alpha to Coverage` option to the integrated Video Settings section, Mod Menu screen, and Sodium settings API.
- Removed the default `O` binding from `Cycle AA Mode` to avoid conflicts with shader-pack controls. Existing custom bindings remain unchanged.

### Documentation And Testing

- Expanded optional metrics reports to version 2 with bounded structured diagnostics covering environment, configuration, Vulkan capabilities, render-resource lifecycles, fallback reasons, native DLSS/FSR state, temporal resets, performance anomalies, and complete exception stack traces.
- Clarified that SSAA percentages apply per axis: `200%` is conventional 4x SSAA, while the new `800%` maximum renders 64x as many scene pixels as native resolution.
- Added setup and build documentation for the optional DLSS bridge and bundled FidelityFX bridge.
- Updated renderer architecture and compatibility documentation for Minecraft 26.2.
- Added regression tests for configuration migration, mode planning, dropdown scrolling, NIS resources, FSR temporal masks, Vulkan MSAA compatibility, and alpha-to-coverage policy.
- Added regression coverage for FidelityFX queue allocation and proxy-swapchain image state.
- Added dedicated FidelityFX queue reservation, per-present stale-frame protection, generated-present telemetry, resize/mode-switch recovery, and ordered proxy shutdown for FSR3 Frame Generation.
