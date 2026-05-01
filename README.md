# Salt's Anti Aliasing

Salt's Anti Aliasing is a client-side Fabric mod that adds anti-aliasing, sharpening, and spatial upscaling controls to Minecraft.

This branch is the **Fabric 26.1.2 branch**. It targets:

```text
Minecraft/Fabric target: 26.1.2
Fabric Loader >= project loader_version
Fabric API matching 26.1.2
Java 25
```

This branch is intentionally kept separate from the 1.21.8-1.21.11 modern jar and any legacy or mid 1.21 jars. The code layout should match those branches wherever the Minecraft APIs allow it, but the hook descriptors and renderer adapter remain branch-specific.

## Version Jar Strategy

The project is expected to ship separate jar families from related branches:

```text
legacy jar       -> early 1.21 renderer, old integer texture/FBO path
mid jar          -> transitional GPU texture renderer
modern jar       -> 1.21.8-1.21.11 modern GPU texture view/framegraph path
fabric 26.1.2 jar -> 26.1.2 renderer descriptors and Fabric API surface
```

The point of this split is to avoid one giant jar full of runtime version checks, reflection, and fragile optional mixins. Each jar owns the Minecraft hook layer for its renderer family while sharing the same mode concepts and pipeline planning model where practical.

## Architecture Goal

The code is organized around two ideas:

1. **Core logic should be portable.**
   Config values, mode semantics, quality presets, pipeline planning, pass ids, target descriptions, metrics shape, and debug concepts should not care which Minecraft minor version is running.

2. **Platform glue should be replaceable.**
   Mixins, Fabric APIs, Minecraft render target classes, keyboard descriptors, post-chain APIs, and OpenGL/GPU details belong in adapter layers that can differ between jars.

## Current Package Boundaries

```text
org.betterLostItems.salts_anti_aliasing
  client/
    config/              Shared config and mode data.
    gui/                 Fabric/Minecraft UI adapters.
    metrics/             Runtime metrics and report generation.
    debug/               Debug HUD and edge analysis helpers.
    platform/modern/     Fabric 26.1.2 bridge facade.
    render/
      api/               Backend-neutral render vocabulary.
      common/            Shared planning/runtime coordination.
      opengl/            OpenGL/GPU implementation for this branch.
      vulkan/            Placeholder backend family.
  mixin/client/          Thin Fabric 26.1.2 Minecraft hook points.
```

## Important Separation Rules

- Core config enums must not create Minecraft `Component` objects.
- Core pipeline planning must not know about mixin descriptors.
- Mixins should delegate immediately to `client.platform.modern`.
- `client.platform.modern` may know about Minecraft classes and renderer hook descriptors.
- `client.render.opengl` may know about OpenGL/GPU resources.
- Version checks should not be added here to support unrelated jar families.
- If another Minecraft/Fabric target needs a genuinely different render path, create or update the matching jar branch.

## Rendering Modes

The branch is structured around these modes:

```text
Off
NIS Sharpen
FXAA
MSAA
SSAA
SMAA
NIS Upscale
FSR1 Upscale
FSR1 + RCAS
TAA
```

The shared planner expresses each mode as conceptual passes and targets. The OpenGL implementation translates those concepts into Minecraft render targets, post chains, texture views, resource pools, and temporary FBOs for this branch.

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
minecraft_version=26.1.2
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
5. Replace or adapt `client.render.opengl` where Minecraft resource ownership changed.
6. Avoid adding runtime checks for unrelated branches.

## Current Renderer Notes

- Scene-only effects are applied after 3D world rendering and before HUD/menu rendering.
- Internal-resolution modes temporarily redirect Minecraft's main render target.
- MSAA uses a multisampled OpenGL FBO and resolves back to Minecraft's main target.
- TAA uses jitter, a persistent history target, and dynamic uniforms.
- Dynamic uniforms are uploaded through writable GPU buffers when Minecraft's post-chain uniforms are immutable.

## Development Principles

- Prefer explicit render target ownership.
- Keep pass ids stable and readable.
- Keep shader constants documented in Java or JSON when they affect visible tuning.
- Keep UI labels in lang files and UI adapters, not core enums.
- Keep branch-specific hooks close to the branch-specific platform or renderer adapter.
- Treat crashes on unsupported Minecraft targets as metadata/versioning problems, not runtime feature toggles.

## Verification Checklist

Before calling a Fabric 26.1.2 branch change ready:

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
