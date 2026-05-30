# Salt's Anti Aliasing

Salt's Anti Aliasing is a client-side Fabric mod that adds anti-aliasing, sharpening, and spatial upscaling controls to Minecraft.

This branch targets Minecraft 1.21.1:

```text
Minecraft >= 1.21.1 and < 1.21.2
Fabric Loader >= project loader_version
Fabric API matching 1.21.1
```

Minecraft changed its renderer inside the 1.21 line. This jar uses the pre-1.21.5 integer framebuffer and classic `PostChain` APIs; newer 1.21.8+ builds need a different branch.

## Architecture

The code is split into portable mod logic and version-specific renderer glue:

```text
org.betterLostItems.salts_anti_aliasing
  client/
    config/              Shared config and mode data.
    gui/                 Fabric/Minecraft UI adapters.
    metrics/             Runtime metrics and report generation.
    debug/               Debug HUD and edge analysis helpers.
    platform/modern/     Minecraft 1.21.1 bridge facade.
    render/
      api/               Backend-neutral render vocabulary.
      common/            Shared planning/runtime coordination.
      opengl/            1.21.1 OpenGL framebuffer implementation.
      vulkan/            Placeholder backend family.
  mixin/client/          Thin Minecraft 1.21.1 hook points.
```

Core config, modes, pass planning, metrics, and debug state stay portable. Mixins and OpenGL code own the Minecraft-specific method descriptors, framebuffer IDs, post-chain resources, and render target redirection.

## Rendering Modes

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

The shared planner expresses each mode as conceptual passes and targets. The 1.21.1 OpenGL implementation translates that plan into classic Minecraft `RenderTarget`, `PostChain`, and raw OpenGL FBO operations.

## Compatibility

- Mod Menu support is exposed through the `modmenu` entrypoint and opens the same config screen used by the in-game video options button.
- Sodium is detected at runtime without a hard dependency. MSAA falls back to FXAA when Sodium is loaded because Sodium's chunk renderer and the vanilla MSAA framebuffer redirect are not compatible on this renderer path.
- Sodium's newer config-page API is not available on the 1.21.1 Sodium line, so this jar does not register the `sodium:config_api_user` entrypoint.

## Build

```powershell
.\gradlew.bat build
```

The primary development target is set in `gradle.properties`:

```properties
minecraft_version=1.21.1
```

The jar metadata constrains this branch to:

```json
"minecraft": ">=1.21.1 <1.21.2"
```

## Run Client

```powershell
.\gradlew.bat runClient
```

## Verification Checklist

```text
.\gradlew.bat build
.\gradlew.bat runClient
Open Video Settings
Open the Mod Menu config screen
Cycle modes from Off through TAA
Enter a world
Toggle edge debug with F3+K
Resize the window
Return to Video Settings after entering a world
Check latest.log for mixin or renderer errors
```
