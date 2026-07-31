# Salt's Anti Aliasing

Salt's Anti Aliasing is a client-side Fabric mod that adds anti-aliasing, sharpening, and spatial upscaling controls to Minecraft.

This branch is the **Fabric 26.2 branch**. It targets:

```text
Minecraft/Fabric target: 26.2
Fabric Loader >= project loader_version
Fabric API matching 26.2
Java 25
```

This branch is intentionally kept separate from the 1.21.8-1.21.11 modern jar and any legacy or mid 1.21 jars. The code layout should match those branches wherever the Minecraft APIs allow it, but the hook descriptors and renderer adapter remain branch-specific.

## Version Jar Strategy

The project is expected to ship separate jar families from related branches:

```text
legacy jar       -> early 1.21 renderer, old integer texture/FBO path
mid jar          -> transitional GPU texture renderer
modern jar       -> 1.21.8-1.21.11 modern GPU texture view/framegraph path
fabric 26.2 jar -> 26.2 renderer descriptors and Fabric API surface
```

The point of this split is to avoid one giant jar full of runtime version checks, reflection, and fragile optional mixins. Each jar owns the Minecraft hook layer for its renderer family while sharing the same mode concepts and pipeline planning model where practical.

## Architecture Goal

The code is organized around two ideas:

1. **Core logic should be portable.**
   Config values, mode semantics, quality presets, pipeline planning, pass ids, target descriptions, metrics shape, and debug concepts should not care which Minecraft minor version is running.

2. **Platform glue should be replaceable.**
   Mixins, Fabric APIs, Minecraft render target classes, keyboard descriptors, post-chain APIs, and Vulkan/GPU details belong in adapter layers that can differ between jars.

## Current Package Boundaries

```text
org.betterLostItems.salts_anti_aliasing
  client/
    config/              Shared config and mode data.
    gui/                 Fabric/Minecraft UI adapters.
    metrics/             Runtime metrics and report generation.
    debug/               Debug HUD and edge analysis helpers.
    platform/modern/     Fabric 26.2 bridge facade.
    render/
      api/               Backend-neutral render vocabulary.
      common/            Shared planning/runtime coordination.
      vulkan/            Vulkan renderer controllers and capability policy.
  mixin/client/          Thin Fabric 26.2 Minecraft hook points.
```

## Important Separation Rules

- Core config enums must not create Minecraft `Component` objects.
- Core pipeline planning must not know about mixin descriptors.
- Mixins should delegate immediately to `client.platform.modern`.
- `client.platform.modern` may know about Minecraft classes and renderer hook descriptors.
- `client.render.vulkan` may know about Minecraft's Vulkan/GPU resources and renderer internals.
- Version checks should not be added here to support unrelated jar families.
- If another Minecraft/Fabric target needs a genuinely different render path, create or update the matching jar branch.

## Rendering Modes

The branch is structured around these modes:

```text
Off
FXAA
MSAA
SSAA
SMAA
NIS Upscale
DLSS Super Resolution
FSR2 Super Resolution
FSR3 Super Resolution
FSR3 Super Resolution + Frame Generation
FSR1 Upscale
TAA
```

Sharpening is an independent 0–100% control available with every mode, including Off, and defaults to 0%. The shared planner expresses each mode as conceptual passes and targets. The runtime requires Minecraft's Vulkan backend for every anti-aliasing mode; non-Vulkan sessions keep saved settings but block rendering and mode cycling until Minecraft is restarted on Vulkan.

SSAA percentages are per axis. The existing 200% option renders at twice the output width and twice the output height, so it evaluates four source pixels for every output pixel: conventional 4x SSAA. A 400% per-axis target would instead be 16x SSAA.

The optional **Cycle AA Mode** key binding is unbound by default so it cannot collide with shader-pack shortcuts (including Iris's `O` binding). It can be assigned under Minecraft's Controls screen; existing custom bindings remain intact. Existing installations that already saved the old `O` default should clear or reassign it once in Controls.

## DLSS Super Resolution

DLSS is optional and disabled by default. The repo does not include NVIDIA Streamline/DLSS binaries or a NVIDIA application ID.

To test DLSS locally, provide:

```text
dlssBridgePath       absolute path to salts_dlss_bridge.dll
dlssPluginPath       absolute path to the Streamline plugin/DLSS runtime folder
dlssApplicationId    NVIDIA-provided application ID
dlssLogPath          optional absolute log output folder
```

The same values can be overridden before startup with:

```text
SALTS_DLSS_BRIDGE_PATH
SALTS_DLSS_PLUGIN_PATH
SALTS_DLSS_APPLICATION_ID
SALTS_DLSS_LOG_PATH
```

or Java properties:

```text
salts.dlss.bridgePath
salts.dlss.pluginPath
salts.dlss.applicationId
salts.dlss.logPath
```

The native bridge project lives in `native/dlss_bridge`. The normal Gradle build does not compile it; build it separately against a local NVIDIA Streamline SDK.

## AMD FSR2/FSR3

The Windows x64 release jar bundles the mod's FSR JNI bridge and AMD FidelityFX Vulkan runtime. Users should only need to install the jar, run Minecraft on Vulkan, and select an FSR mode.

Advanced overrides are still available:

```text
SALTS_FSR_BRIDGE_PATH
SALTS_FSR_RUNTIME_PATH
SALTS_FSR_LOG_PATH
```

or Java properties:

```text
salts.fsr.bridgePath
salts.fsr.runtimePath
salts.fsr.logPath
```

If neither bridge nor runtime path is configured, the bundled native files are extracted to `.minecraft/salts_anti_aliasing/native/...` and loaded from there. FSR3 Frame Generation remains separate from FSR3 Super Resolution and only appears when the real FidelityFX Vulkan frame-generation swapchain path initializes successfully.

## Platform Hook Layer

`ModernMinecraftHooks` is the main facade between mixins and the renderer.

Mixins should do only this:

1. Land on a Minecraft method.
2. Collect parameters.
3. Delegate to `ModernMinecraftHooks`.

This keeps version-porting work contained. Other jar branches can provide a facade with the same intent but different descriptors and renderer calls.

## Build

```powershell
.\gradlew.bat build
```

The primary development target is set in `gradle.properties`:

```properties
minecraft_version=26.2
```

## Run Client

```powershell
.\gradlew.bat runClient
```

The dev run uses the configured Minecraft version and Fabric API in `gradle.properties`.

## Porting Guide For Other Jars

When syncing a sibling jar branch:

1. Keep `client.config` mode semantics compatible unless a feature truly cannot exist.
2. Keep `client.render.api` target/pass vocabulary as close as possible.
3. Replace the branch-specific platform facade.
4. Replace mixin descriptors in `mixin/client`.
5. Replace or adapt backend controller packages where Minecraft resource ownership changed.
6. Avoid adding runtime checks for unrelated branches.

## Current Renderer Notes

- Scene-only effects are applied after 3D world rendering and before HUD/menu rendering.
- Internal-resolution modes temporarily redirect Minecraft's main render target.
- Anti-aliasing is blocked unless Minecraft reports an active Vulkan device.
- MSAA uses native Vulkan multisampled scene textures and resolves into Minecraft's main target after world rendering. Cutout textures keep Minecraft's binary alpha behavior instead of fading through partial sample coverage.
- TAA uses jitter, a persistent history target, and dynamic uniforms.
- DLSS redirects world rendering to a Streamline-selected internal-resolution target and evaluates through the optional JNI bridge when all external requirements are met.
- FSR2/FSR3 use the bundled FidelityFX Vulkan runtime on Windows x64 and can be overridden with explicit native paths.
- Dynamic uniforms are uploaded through writable GPU buffers when Minecraft's post-chain uniforms are immutable.

## Development Principles

- Prefer explicit render target ownership.
- Keep pass ids stable and readable.
- Keep shader constants documented in Java or JSON when they affect visible tuning.
- Keep UI labels in lang files and UI adapters, not core enums.
- Keep branch-specific hooks close to the branch-specific platform or renderer adapter.
- Treat crashes on unsupported Minecraft targets as metadata/versioning problems, not runtime feature toggles.

## Verification Checklist

Before calling a Fabric 26.2 branch change ready:

```text
.\gradlew.bat build
.\gradlew.bat runClient
Open Video Settings
Cycle modes from Off through TAA
Enter a world
Toggle edge debug with F3+K
Resize the window
Return to Video Settings after entering a world
Check latest.log for mixin or renderer errors
```

## Future Work

- Keep this branch structurally synced with sibling jar branches.
- Add automated smoke checks per jar family.
- Keep common config and pass-planning behavior synchronized across branches.
