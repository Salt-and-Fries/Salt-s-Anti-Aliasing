# Changelog

## X.1 — Minecraft 26.2

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

- Clarified that SSAA percentages apply per axis: `200%` is conventional 4x SSAA, while the new `800%` maximum renders 64x as many scene pixels as native resolution.
- Added setup and build documentation for the optional DLSS bridge and bundled FidelityFX bridge.
- Updated renderer architecture and compatibility documentation for Minecraft 26.2.
- Added regression tests for configuration migration, mode planning, dropdown scrolling, NIS resources, FSR temporal masks, Vulkan MSAA compatibility, and alpha-to-coverage policy.
- Added regression coverage for FidelityFX queue allocation and proxy-swapchain image state.
- Added dedicated FidelityFX queue reservation, per-present stale-frame protection, generated-present telemetry, resize/mode-switch recovery, and ordered proxy shutdown for FSR3 Frame Generation.
