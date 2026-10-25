# Salt's Anti Aliasing

Salt's Anti Aliasing is a client-side Fabric mod that adds anti-aliasing, sharpening, and spatial upscaling controls to Minecraft.

This branch is the **Fabric 26.3 branch**. It targets:

```text
Minecraft/Fabric target: 26.3
Fabric Loader >= project loader_version
Fabric API matching 26.3
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
fabric 26.3 jar -> 26.3 renderer descriptors and Fabric API surface
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
    platform/modern/     Fabric 26.3 bridge facade.
    render/
      api/               Backend-neutral render vocabulary.
      common/            Shared planning/runtime coordination.
      vulkan/            Vulkan renderer controllers and capability policy.
  mixin/client/          Thin Fabric 26.3 Minecraft hook points.
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

MSAA also exposes an optional alpha-to-coverage control, disabled by default. Enabling it smooths cutout texture edges but may make distant foliage fade or stipple.

SSAA percentages are per axis. The 200% option renders at twice the output width and twice the output height, so it evaluates four source pixels for every output pixel: conventional 4x SSAA. The selectable range now extends to 800% per axis (64x as many scene pixels). Values above 400% display an extreme-performance warning because they may heavily impact frame rate and GPU memory usage or exceed the GPU's maximum texture size at high output resolutions.

The optional **Cycle AA Mode** key binding is unbound by default so it cannot collide with shader-pack shortcuts (including Iris's `O` binding). It can be assigned under Minecraft's Controls screen; existing custom bindings remain intact. Existing installations that already saved the old `O` default should clear or reassign it once in Controls.

## Anti Aliasing Settings

Open **Options → Video Settings → Anti Aliasing Settings**. All mod controls now live in this
dedicated menu. Mod Menu and the Sodium integration open the same screen.

The selected method determines which controls appear. **Advanced controls** exposes additional
parameters, diagnostic views and optional native-runtime overrides. Sliders preview immediately;
leaving the screen saves them. Reset this method restores its image-quality defaults, while Reset
all restores the mod's controls without erasing explicit native-runtime paths.

| Method | Available image-quality controls |
| --- | --- |
| FXAA | Subpixel blend, edge threshold, minimum contrast |
| MSAA | Sample count and optional cutout alpha-to-coverage |
| SSAA | Per-axis resolution scale, area/tent resolve, linear-light/encoded averaging |
| SMAA | Low/Medium/High/Ultra search quality and edge threshold |
| FSR1 / NIS | Input-resolution preset, SMAA/FXAA/Off pre-AA and its controls, optional terrain MIP bias |
| TAA | History contribution, luminance/depth rejection, variance clipping, jitter length, history filter, geometry motion and confidence views |
| DLSS | SDK quality preset, geometry motion, color-rejection controls, terrain MIP bias and buffer diagnostics |
| FSR2 / FSR3 | SDK quality preset, geometry motion, reactive/composition-mask controls, terrain MIP bias and buffer diagnostics |
| FSR3 + FG | FSR3 controls, frame-generation toggle and rendered/display-present counters |

Every method also has independent sharpening. The generic filter is explicitly named as a custom
edge-adaptive filter, with optional mild unsharp filtering and a bounded overshoot control. FSR2/3
use native sharpening when their evaluation succeeds. Sharpening defaults to zero; terrain MIP
bias defaults to Off because aggressive detail sampling can increase shimmer. Configuration v3
migrates older settings and preserves explicit v2 sharpening values.

Diagnostics show the effective renderer, internal resolution, fallback reason and estimated payload
of directly owned scene targets. The estimate excludes SDK allocations, temporary post-chain targets
and driver overhead. **Retry rendering** clears recoverable controller failures on the render thread.

## Metrics And Diagnostics

When `recordMetrics` is enabled, the mod writes `salts_anti_aliasing_metrics_latest.json` to Minecraft's `logs` directory and archives a timestamped copy when the recording session ends. In addition to frame-time and FPS statistics, report version 2 includes a bounded structured diagnostics timeline with:

- complete JVM, operating-system, Fabric mod-list, GPU, driver, Vulkan-device, queue-family, and extension context;
- requested and effective configuration, pipeline passes, render targets, capability decisions, and automatic fallbacks;
- MSAA image-format/sample support and allocation attempts;
- render-target creation, resize, replacement, and destruction events;
- TAA, DLSS, FSR2, FSR3, and frame-generation initialization, temporal reset, evaluation, swapchain, present, and recovery state;
- warnings and failures with native result codes and full Java exception stack traces;
- lag-spike, severe-lag-spike, FPS-drop, heap, processor, and thread information.

The newest 4,096 diagnostic events are retained so a recurring failure cannot grow the report without limit. The report includes total counts by severity/category and a dropped-event count when that limit is exceeded. Recording remains a no-op while `recordMetrics` is disabled.

## DLSS Super Resolution

DLSS requires Windows x64, supported RTX hardware, Vulkan, and a user-downloaded NVIDIA runtime.
The public jar includes the mod's JNI bridge; NVIDIA's runtime DLLs are excluded from every build.
Open **Video Settings → Anti Aliasing Settings**, select **DLSS Super Resolution**, then use
**Set up DLSS** below Advanced controls. Download Streamline **2.14.1**,
extract it, then connect `bin/x64/nvngx_dlss.dll` using the system file picker. The mod checks the
five matching production DLLs and copies them into this Minecraft instance. Restart Minecraft
completely, then select DLSS Super Resolution. See [the setup guide](docs/dlss-setup.md).

This same flow works with `./gradlew runClient`. Its settings live in `run/26.3`, separately from
your launcher installation. Developers can instead use a local runtime for one launch:

```powershell
.\gradlew.bat runClient -PdlssRuntimeDirectory="C:/SDKs/Streamline/bin/x64"
```

The property only sets the development JVM's runtime path; it never packages NVIDIA DLLs.
DLSS Auto selects Quality through 1080p, Balanced through 1440p, and Performance above 1440p.
Advanced explicit overrides remain available:

```text
dlssBridgePath       optional custom bridge path (blank uses the included bridge)
dlssPluginPath       absolute path to the Streamline plugin/DLSS runtime folder
dlssApplicationId    optional NVIDIA-provided application ID (0 uses the mod's project ID)
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

The native bridge project lives in `native/dlss_bridge`. The normal Gradle build packages the prebuilt bridge from `native/redist/dlss/windows-x86_64`; rebuilding it requires a local NVIDIA Streamline SDK.

Fabric 26.3 requires bridge ABI 2; rebuild older bridge DLLs. Configure the runtime before starting Minecraft, because Streamline's required Vulkan extensions and features must be enabled when the device is created. Changing runtime paths during a session requires a restart.

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

If neither bridge nor runtime path is configured, the bundled native files are extracted to `.minecraft/salts_anti_aliasing/native/...` and loaded from there. FSR3 Frame Generation remains separate from FSR3 Super Resolution and only appears when the real FidelityFX Vulkan frame-generation swapchain path initializes successfully. The integration reserves distinct SDK queues during Vulkan device creation, keeps HUD-less scene capture immediately before GUI rendering, and verifies actual generated display presents through FidelityFX rather than estimating them from Minecraft's frame rate.

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

Build with Java 25 or newer. The distributable for this branch is
`build/libs/salts-anti-aliasing-mc26.3-1.1.jar`; the Minecraft version is included
in the filename to distinguish it from sibling releases.

The primary development target is set in `gradle.properties`:

```properties
minecraft_version=26.3
```

## Run Client

```powershell
.\gradlew.bat runClient
```

The dev run uses the configured Minecraft version and Fabric API in `gradle.properties`.
Its game directory is `run/26.3`, separate from older development worlds and mods.

## Minecraft 26.3 Port

- GPU adapters and mixins use RenderPearl's API, frontend, and Vulkan backend packages.
- Keyboard and mouse handling use Minecraft's SDL-compatible `InputConstants`.
- Render targets specify color and depth formats explicitly.
- MSAA selects compiled pipeline variants in the RenderPearl frontend. Sample count and
  alpha-to-coverage metadata follow each variant into asynchronous native compilation.
- FSR captures opaque scene color outside an active render pass, for both classic and
  improved transparency.
- All bundled fragment shaders use GLSL 450 and explicit input/output locations. The
  test suite compiles them to SPIR-V using RenderPearl's Vulkan 1.2 requirements.
- MSAA resolves opaque depth before the single-sample improved-transparency passes.
  Sky rendering follows the current scene target across sample-count changes and resizing.
- SSAA integrates the complete source-pixel footprint, including fractional scales,
  instead of discarding supersamples through a four-texel linear downsample.
- FSR1 uses AMD's reference FP32 EASU. NIS uses NVIDIA's reference SDR coefficient
  bank and directional filters adapted to a fragment pass. Their reconstruction
  passes leave sharpening to the independent user control.
- Temporal reprojection uses camera-relative matrices, Vulkan reverse-Z depth, and
  previous-minus-current pixel motion. Coordinates are subtracted in double precision
  before upload, and clear-depth sky preserves rotation without translation parallax.
- TAA, FSR and DLSS overlay depth-validated motion from matched entity-model, item and moving-block
  vertices. Stable block-entity submissions can also contribute animated model geometry. Unsupported
  custom geometry and particles still rely on camera motion plus color/depth rejection; DLSS uses
  a supported R16F color-change hint.
- FSR1/NIS receive configurable pre-AA. SSAA defaults to linear-light area integration, with a tent
  filter and legacy encoded-light choice. TAA resolves current/history samples onto an unjittered grid.
- Inactive scene targets are released, and native render features retire after GPU completion.
- Frame generation receives presentation-oriented color, depth and motion, including matching world
  overlays in the HUD-less image. This corrects an upside-down input that prevented useful interpolation.

## Fabric 26.3 Quality Verification

Validated on Windows x64 with an RTX 3060 Ti, NVIDIA driver 591.86, and Java 25.
The automated suite includes all shipped fragment shaders compiled to Vulkan 1.2 SPIR-V
and reprojection tests at ordinary coordinates and near the world border.

Runtime checks exercised every exposed mode, spatial and temporal quality changes,
window/fullscreen resizing, classic and improved transparency, glass, water, clouds,
cutout geometry, entities, underwater rendering, and the night sky. Real Streamline
DLSS evaluation and FidelityFX frame interpolation were verified through native logs.
GPU readback near x=29,999,900 measured stationary motion below 0.000015 pixels with
no non-finite values. GPU checkerboard tests of SSAA at 2.125x, 4x, and 8x matched
area-weighted reference output within one 8-bit color level.

These checks cover the tested configuration; other GPUs, drivers, render backends,
and third-party renderer combinations still need their own verification. Spatial
methods retain their normal limitations on temporal shimmer, and temporal methods
can still lose detail at low internal resolutions or around disocclusion.

The audit implementation and follow-up evidence are documented in
[the implementation and validation notes](docs/fabric-26.3-implementation-validation.md), including
enlarged game captures, stationary TAA measurements, actual generated-frame captures and remaining
coverage limits. Sparse, nonblocking GPU pass timestamps are included when metrics recording is enabled.

## Porting Guide For Other Jars

When syncing a sibling jar branch:

1. Keep `client.config` mode semantics compatible unless a feature truly cannot exist.
2. Keep `client.render.api` target/pass vocabulary as close as possible.
3. Replace the branch-specific platform facade.
4. Replace mixin descriptors in `mixin/client`.
5. Replace or adapt backend controller packages where Minecraft resource ownership changed.
6. Avoid adding runtime checks for unrelated branches.

## Current Renderer Notes

The anti-aliasing settings include a Credits and Licenses screen with developer attribution,
NVIDIA attribution, the Streamline interface license, and available license notices copied with a user-imported DLSS runtime.
The proprietary NVIDIA runtime remains subject to its accompanying license; the mod's own
license does not grant rights to it. NVIDIA's release notification and applicable written
approval of proposed trademark uses remain separate release requirements.

NVIDIA RTX™ — DLSS Super Resolution. DLSS Super Resolution uses NVIDIA's DLSS SDK and NVIDIA
Streamline. NVIDIA and NVIDIA RTX are trademarks and/or registered trademarks of NVIDIA
Corporation in the United States and other countries. This software contains source code
provided by NVIDIA Corporation. NVIDIA runtime components are governed by their accompanying
licenses. This proposed credit is included in the pending NVIDIA release review.

- Scene-only effects are applied after 3D world rendering and before HUD/menu rendering.
- Internal-resolution modes temporarily redirect Minecraft's main render target.
- Anti-aliasing is blocked unless Minecraft reports an active Vulkan device.
- MSAA uses native Vulkan multisampled scene textures and resolves into Minecraft's main target after world rendering. Cutout textures keep Minecraft's binary alpha behavior by default, with optional alpha-to-coverage for players who prefer smoother cutout edges.
- TAA uses jitter, a persistent history target, and dynamic uniforms.
- DLSS redirects world rendering to a Streamline-selected internal-resolution target. Players connect the Windows x64 production runtime through Set up DLSS; NVIDIA DLLs are excluded from public jars. A documented custom-engine Project ID is used when no NVIDIA-issued application ID exists.
- FSR2/FSR3 use the bundled FidelityFX Vulkan runtime on Windows x64 and can be overridden with explicit native paths. FSR3 Frame Generation uses the SDK replacement swapchain, dedicated queue roles, per-present interpolation state, and a HUD-less scene image.
- Dynamic uniforms are uploaded through writable GPU buffers when Minecraft's post-chain uniforms are immutable.

## Development Principles

- Prefer explicit render target ownership.
- Keep pass ids stable and readable.
- Keep shader constants documented in Java or JSON when they affect visible tuning.
- Keep UI labels in lang files and UI adapters, not core enums.
- Keep branch-specific hooks close to the branch-specific platform or renderer adapter.
- Treat crashes on unsupported Minecraft targets as metadata/versioning problems, not runtime feature toggles.

## Verification Checklist

Before calling a Fabric 26.3 branch change ready:

```text
.\gradlew.bat build
.\gradlew.bat runClient
Open Video Settings
Open Anti Aliasing Settings
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
