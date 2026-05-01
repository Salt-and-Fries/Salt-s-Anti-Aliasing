# Salt's Anti Aliasing

Salt's Anti Aliasing is a client-side Fabric mod that adds anti-aliasing, sharpening, and spatial upscaling controls to Minecraft.

This branch is the **modern 1.21.x branch**. It targets:

```text
Minecraft >= 1.21.8 and < 1.21.12
Fabric Loader >= project loader_version
Fabric API matching the selected Minecraft minor
```

Older 1.21 versions are intentionally not handled in this branch. They need different jars because Minecraft changed large parts of its rendering stack inside the 1.21 line.

## Version Jar Strategy

The project is expected to ship three jar families from related branches:

```text
legacy jar  -> early 1.21 renderer, old integer texture/FBO path
mid jar     -> transitional GPU texture renderer
modern jar  -> 1.21.8-1.21.11 modern GPU texture view/framegraph path
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
    gui/                 Modern Fabric/Minecraft UI adapters.
    metrics/             Runtime metrics and report generation.
    debug/               Debug HUD and edge analysis helpers.
    platform/modern/     Minecraft 1.21.8-1.21.11 bridge facade.
    render/
      api/               Backend-neutral render vocabulary.
      common/            Shared planning/runtime coordination.
      opengl/            Modern OpenGL/GPU implementation.
      vulkan/            Placeholder backend family.
  mixin/client/          Thin modern-version Minecraft hook points.
```

## Important Separation Rules

- Core config enums must not create Minecraft `Component` objects.
- Core pipeline planning must not know about mixin descriptors.
- Mixins should delegate immediately to `client.platform.modern`.
- `client.platform.modern` may know about Minecraft classes and modern renderer hooks.
- `client.render.opengl` may know about modern OpenGL/GPU resources.
- Version checks should not be added to this branch for legacy 1.21.1 behavior.
- If a Minecraft minor needs a genuinely different render path, create or update the matching jar branch.

## Rendering Modes

The modern branch is structured around these modes:

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

The shared planner expresses each mode as conceptual passes and targets. The modern OpenGL implementation translates those concepts into Minecraft 1.21.8-1.21.11 render targets, post chains, texture views, resource pools, and temporary FBOs.

## Modern Hook Layer

`ModernMinecraftHooks` is the main facade between mixins and the renderer.

Mixins should do only this:

1. Land on a Minecraft method.
2. Collect parameters.
3. Delegate to `ModernMinecraftHooks`.

This keeps version-porting work contained. A legacy jar can provide a legacy facade with the same intent but different descriptors and renderer calls.

## Why Some Modern Hooks Have Multiple Descriptors

Minecraft still changed small method descriptors between 1.21.8 and 1.21.11. For example:

- The debug-key handler differs between 1.21.8 and later modern builds.
- The `LevelRenderer.renderLevel(...)` projection-matrix call has a different descriptor in 1.21.8 than in 1.21.10-1.21.11.

Those are handled as alternate mixin targets inside this modern jar. That is not meant to support legacy 1.21.1; it is only to cover the declared 1.21.8-1.21.11 range.

## Build

```powershell
.\gradlew.bat build
```

The primary development target is currently set in `gradle.properties`:

```properties
minecraft_version=1.21.11
```

The jar metadata constrains this branch to:

```json
"minecraft": ">=1.21.8 <1.21.12"
```

## Run Client

```powershell
.\gradlew.bat runClient
```

The dev run uses the configured Minecraft version and Fabric API in `gradle.properties`. For a different modern minor, update the Gradle properties and rebuild on this branch, or use the matching release jar.

## Porting Guide For Other Jars

When creating the legacy or mid jar branch:

1. Keep `client.config` mode semantics compatible unless a feature truly cannot exist.
2. Keep `client.render.api` target/pass vocabulary as close as possible.
3. Replace `client.platform.modern` with a branch-specific platform facade.
4. Replace mixin descriptors in `mixin/client`.
5. Replace or adapt `client.render.opengl` where Minecraft resource ownership changed.
6. Avoid adding legacy runtime checks to the modern branch.

## Current Modern Renderer Notes

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
- Keep branch-specific hacks close to the branch-specific hook or renderer adapter.
- Treat crashes on unsupported Minecraft minors as metadata/versioning problems, not runtime feature toggles.

## Verification Checklist

Before calling a modern-branch change ready:

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

- Create the legacy branch for early 1.21.x integer texture/FBO rendering.
- Create the mid branch for transitional GPU texture versions if needed.
- Add automated smoke checks per jar family.
- Keep common config and pass-planning behavior synchronized across branches.
