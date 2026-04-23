# Salt's Anti Aliasing

Salt's Anti Aliasing is planned as a Minecraft client mod that adds a modern anti-aliasing and image-scaling stack to the game.

The first target is the current OpenGL-based Minecraft renderer. The long-term goal is to keep the mod structured so that if Minecraft, a loader, or a compatible rendering layer eventually exposes a practical Vulkan path, we can port the backend without throwing away the effect logic.

## Main Goal

Build one mod with two time horizons:

- Near term: ship stable OpenGL support for the core modes listed below.
- Long term: keep the architecture backend-agnostic so the rendering backend can later move from OpenGL to Vulkan.
- Future expansion: once a Vulkan-capable path is realistic, add advanced temporal upscalers and vendor SDK integrations such as DLSS, DLAA, XeSS, and newer FSR generations.

## Planned Modes

### OpenGL Phase

- `Off`
- `FXAA`
- `MSAA`
- `SMAA`
- `TAA`
- `NIS Sharpen`
- `NIS Upscale`
- `FSR1 Upscale`
- `FSR1 + RCAS`
- `SSAA`

### Vulkan / Advanced Phase

- Vulkan backend port
- DLSS
- DLAA
- XeSS
- FSR2 or newer temporal FSR path

## Design Rules We Should Follow From Day One

- Do not hard-code OpenGL concepts into gameplay-facing config or effect selection.
- Keep "effect logic" separate from "graphics backend" code.
- Treat each AA or upscale mode as a pipeline module with clean inputs and outputs.
- Keep HUD and text rendering separate from the 3D scene so UI can stay native-resolution.
- Define shared frame data once: color, depth, velocity or reprojection data, jitter, exposure, render size, and output size.
- Avoid relying on implicit OpenGL state as much as possible.
- Use explicit render targets, explicit pass ordering, and explicit texture ownership.
- Keep shader math portable so it can later be adapted to Vulkan with minimal rewrites.
- Make proprietary SDK features optional add-ons, not hard dependencies of the entire mod.

## Recommended High-Level Architecture

```text
Config / Video Settings UI
        |
        v
Mode Selection + Quality Settings
        |
        v
Common Render Pipeline API
        |
        +-- OpenGL Backend
        |     |
        |     +-- Render target allocation
        |     +-- Pass execution
        |     +-- Shader binding
        |
        +-- Vulkan Backend (later)
              |
              +-- Equivalent resource and pass system

Shared Effect Modules
  - Passthrough / Off
  - Native-resolution post AA
  - Internal-resolution rendering
  - Spatial upscalers
  - Temporal AA / temporal upscalers
```

## Suggested Package Layout

```text
org.betterLostItems.salts_anti_aliasing
  client/
    config/
    ui/
    render/
      api/
      common/
      opengl/
      vulkan/
      effects/
        passthrough/
        fxaa/
        smaa/
        taa/
        nis/
        fsr1/
```

## Feature Order: Easiest To Hardest

This order is based on implementation difficulty, integration risk, and how much custom frame history or extra render data each mode needs.

1. `Off`
2. `NIS Sharpen`
3. `FXAA`
4. `MSAA`
5. `SSAA`
6. `NIS Upscale`
7. `FSR1 Upscale`
8. `FSR1 + RCAS`
9. `SMAA`
10. `TAA`

## Why This Order Makes Sense

- `Off` is the baseline passthrough mode and gives us the settings plumbing with almost no rendering risk.
- `NIS Sharpen` is a simple full-screen post-process and is the easiest real effect to verify.
- `FXAA` is still a single-pass post-process, but it adds edge detection and tuning.
- `MSAA` fits after FXAA because it is conceptually familiar but requires multisampled scene targets, resolve handling, and stricter render-target ownership.
- `SSAA` requires render-target scaling, output-size management, high-resolution scene resolves, and clean scene-vs-UI separation.
- `NIS Upscale` builds on the low-resolution render path and adds a real upscaler.
- `FSR1 Upscale` is similar in category to NIS Upscale but is more specialized and needs careful sampling behavior.
- `FSR1 + RCAS` adds an additional sharpening/tuning step on top of FSR1.
- `SMAA` is more complex than FXAA because it is multi-pass and needs lookup textures plus careful edge blending.
- `TAA` is the hardest because it needs jitter, history buffers, reprojection, stability tuning, and ghosting control.

## OpenGL Implementation Roadmap

### Phase 0: Foundation

Build the structure once before adding individual modes.

1. Add a real mod README and keep the design documented.
2. Clean up scaffold leftovers such as copied description text before release.
3. Add a config system for mode, sharpness, quality presets, and internal render scale.
4. Add a video-settings entry or mod config UI so players can change modes in game.
5. Create a backend-neutral render API with an OpenGL implementation now and a Vulkan placeholder interface for later.
6. Create a pass manager for full-screen effects and intermediate framebuffers.
7. Define shared per-frame data for scene color, scene depth, internal render size, output window size, jitter values, previous-frame history, and future exposure or luminance inputs.
8. Make the 3D scene render into a mod-controlled target before final presentation.
9. Keep HUD, menus, and text at native resolution and composite them after scene AA or upscaling.
10. Add debug toggles for showing raw scene, final image, edges, history, and upscale output.



## Vulkan Planning: What We Need To Protect Now

The best Vulkan plan is to avoid baking OpenGL-only assumptions into the first implementation.

### Keep These Interfaces Backend-Agnostic

- Texture allocation
- Framebuffer or render-target creation
- Full-screen pass execution
- Shader parameter binding
- History-buffer management
- Resize and swapchain-like recreation handling
- Capability detection
- Per-frame synchronization points

### OpenGL Code Should Not Own The Effect Definitions

- `FXAA`, `SMAA`, `TAA`, `NIS`, and `FSR1` should be described as effect modules.
- OpenGL should only provide the resources and commands needed to run those modules.
- Vulkan later should provide the same effect inputs through a different backend implementation.

### Shader Portability Rules

- Keep algorithm constants and sampling logic isolated from API-specific glue.
- Minimize direct reliance on OpenGL-specific built-ins outside thin wrapper code.
- Keep a clean separation between shared math and backend-specific shader entrypoints.
- Document required inputs per effect so shader rewrites are mechanical instead of guesswork.

### Resource Rules That Help A Future Vulkan Port

- Prefer explicit texture formats.
- Avoid reading from and writing to the same texture in ambiguous ways.
- Avoid hidden global state dependencies.
- Treat every pass as an explicit input-output contract.
- Keep resize and resource recreation code centralized.

## Vulkan Port Roadmap

When a practical Vulkan renderer path exists, the port should happen in this order:

1. Add a Vulkan backend implementation behind the existing common render API.
2. Recreate the framebuffer and texture management layer for Vulkan images and views.
3. Recreate the full-screen pass system for Vulkan pipelines and descriptor binding.
4. Port the easiest modes first: `Off`, `NIS Sharpen`, and `FXAA`.
5. Port `MSAA` next because the multisampled-target design should already be isolated from the effect-selection UI.
6. Port the internal-resolution path next.
7. Port NIS and FSR1 spatial upscalers.
8. Port SMAA.
9. Port TAA last because temporal resource flow and synchronization are more sensitive.
10. Only after the common Vulkan path is stable should advanced SDK-based upscalers be added.

## Future Advanced Modes After Vulkan

These are intentionally not part of the first OpenGL milestone.

### DLSS / DLAA

- Requires NVIDIA SDK integration and native-library handling.
- Needs stable motion vectors, depth, jitter, exposure handling, and careful lifecycle management.
- Should be implemented as an optional adapter module so the main mod still works without proprietary binaries.

### XeSS

- Similar integration class to DLSS in terms of motion data and native SDK concerns.
- Should be kept behind the same "advanced temporal upscaler" interface family.

### FSR2 or Newer

- Likely the most realistic advanced non-proprietary temporal upscale path after Vulkan.
- Needs the same core data preparation as DLSS and XeSS: depth, jitter, reactive handling, and reliable history.
- This is another reason TAA-style data plumbing should be designed early, even if the first OpenGL release only ships basic TAA.

## Data We Will Eventually Need For Advanced Temporal Upscalers

- Stable depth
- Camera jitter
- Previous-frame history
- Motion vectors if available
- Exposure or luminance control
- Reactive or transparency handling where relevant
- Reliable reset conditions on teleports, menu transitions, and dimension swaps

## Recommended Development Sequence

1. Finish the common render pipeline foundation.
2. Ship `Off`.
3. Ship `NIS Sharpen`.
4. Ship `FXAA`.
5. Ship `MSAA`.
6. Ship `SSAA`.
7. Ship `NIS Upscale`.
8. Ship `FSR1 Upscale`.
9. Ship `FSR1 + RCAS`.
10. Ship `SMAA`.
11. Ship `TAA`.
12. Refactor and harden the backend abstraction while OpenGL support is already working.
13. Wait until a practical Vulkan path exists.
14. Port the backend to Vulkan without rewriting the effect roadmap.
15. Add advanced temporal or vendor integrations only after Vulkan parity is stable.

## Definition Of Success

- The first OpenGL release supports all currently requested basic modes.
- The user can switch modes in game without restarting.
- HUD and menus remain crisp and readable.
- The render pipeline is organized so OpenGL is just one backend, not the whole design.
- Vulkan later becomes a backend-port task, not a full rewrite of the mod.
