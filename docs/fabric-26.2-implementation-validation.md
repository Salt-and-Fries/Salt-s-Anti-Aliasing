# Fabric 26.2 implementation and validation

Minecraft 26.2 now has its own Fabric target alongside 26.3 on `multi-version-test`.
It uses **Minecraft's built-in Vulkan backend** and does **not** depend on VulkanMod.
The port starts from the current 26.3 implementation, including XeSS, SGSR, native
runtime management, hand AA, frame generation, texture-detail controls, metrics,
and the dedicated settings screen.

## Build and launch

Use JDK 25 or newer:

```powershell
.\gradlew.bat build
.\gradlew.bat :26.2:fabric:runClient
.\gradlew.bat :26.2:fabric:runClient -PsodiumProfile=stable
.\gradlew.bat :26.2:fabric:runClient -PsodiumProfile=beta
```

The root build verifies and collects all 25 Fabric targets. Its built-in Vulkan jars include:

```text
build/libs/salts-anti-aliasing-mc26.2-1.1.jar
build/libs/salts-anti-aliasing-mc26.3-1.1.jar
```

A target-only build writes to `versions/26.2/fabric/build/libs/`. Normal 26.2
development launches use `run/26.2`; Sodium profiles use subdirectories beneath it.
The short root `runClient` command continues to launch 26.3.

Pinned 26.2 dependencies:

| Dependency | Version |
| --- | --- |
| Fabric Loader | 0.19.5 |
| Fabric API | 0.161.0+26.2 |
| Mod Menu, compile only | 20.0.3 |
| Sodium API, compile only | 0.9.2+mc26.2 |
| Optional stable Sodium | mc26.2-0.9.2-fabric |
| Optional beta Sodium | mc26.2-0.9.2-beta.1-fabric |
| Vanilla common development artifacts | NeoForm 26.2-2 |

NeoForm supplies vanilla development classes; there is no NeoForge loader target.
Sodium remains optional and external to the distributable.

## Version-specific implementation

- **GPU APIs:** 26.2 uses Blaze3D's pipeline descriptions and synchronous Vulkan
  compilation. Pipeline variants are selected before attachment validation, with
  version-correct constructor signatures, sampler bindings, target formats, and
  explicit multi-target render areas.
- **Shaders:** 26.2 resources use `#moj_import`, matching its shader manager. Shader
  logic, temporal history, motion vectors, vendor algorithms, and tuning remain
  equivalent to 26.3. CPU-only Shaderc parsing runs on the protected compiler stack
  on Windows/Java 25; Vulkan object creation remains on the render thread.
- **MSAA and hand AA:** scene attachment samples, alpha-to-coverage, and per-sample
  hand shading are retained. The hand's multiple feature passes share one AA
  target. Multisampled depth is resolved for 26.2's separate transparency targets.
- **Native upscalers:** the Vulkan instance/device hooks use 26.2 signatures while
  retaining DLSS, XeSS, and FidelityFX requirements and feature normalization.
  Additional frame-generation queues and swapchain routing are preserved. The
  existing native bridge binaries work with both targets.
- **Frame-generation overlays:** 26.2's prepared feature geometry is replayed into
  the HUD-less world image before release, retaining native-resolution hand/HUD
  separation and world overlays.
- **UI and runtime import:** the video-settings entry, configuration screen, Mod
  Menu/Sodium integrations, credits, and native runtime settings remain available.
  File/folder selection uses 26.2's bundled TinyFD library asynchronously, and
  download links use Minecraft's platform URL opener.

Portable configuration, presets, render vocabulary, and planning still come from
`versions/common`. Both built-in Vulkan targets also receive native synchronization fixes as the older-version matrix is qualified.

## Verification

Current public-jar qualification for both 26.2 and 26.3 also passes with default,
stable Sodium and beta Sodium profiles, using explicit core and synchronization
validation. The [artifact report](fabric-validation-report.json) records final
jar hashes and all six profile logs after the shared native fixes. Use
`:26.2:fabric:runPackagedClientValidation` to exercise the actual distributable;
`-PsodiumProfile=stable` or `beta` selects the optional renderer profile.

Validation performed on November 11, 2026, using Windows, Java 25, an RTX 3060 Ti,
and Minecraft's built-in Vulkan backend. No VulkanMod was installed.

All **330 unit/integration tests passed**: 166 for 26.2 and 164 for 26.3. The 26.2
suite also checks that core feature flags write the correct inline native offsets
and that promoted feature flags write their Vulkan 1.3 aggregate members. Both public jars
are checked for registered entrypoints/mixins, native bundles/checksums, shader
provenance, and exclusion of downloaded NVIDIA binaries, Sodium, and GPU test code.

The separate 26.2 client gametest creates a disposable flat world containing
textured terrain, cutout leaves, an entity, a held item, and flame particles. It
cycles all 15 implemented rendering paths, checks actual rendering health, takes
screenshots, and verifies the settings entry opens the dedicated configuration
screen. Upscaling paths also require an allocated multisampled hand target, with
automatic texture-detail bias enabled. FSR3 frame generation must report confirmed
generated output, rather than just an active swapchain.

The mode sweep covers Off, FXAA, MSAA, SSAA, SMAA, NIS Upscale, DLSS, XeSS, SGSR 1,
SGSR 2, FSR2, FSR3, FSR3 with frame generation, FSR1, and TAA. All three runs passed,
including settings-screen checks and clean world/client shutdown:

| Renderer | Modes verified | Game / display presents during FSR3 FG |
| --- | --- | --- |
| Default | 15 | 175 / 347, generated output confirmed |
| Stable Sodium | 15 | 177 / 352, generated output confirmed |
| Beta Sodium | 15 | 175 / 348, generated output confirmed |

Run the GPU validation with:

```powershell
.\gradlew.bat :26.2:fabric:runClientGameTest
.\gradlew.bat :26.2:fabric:runClientGameTest -PsodiumProfile=stable
.\gradlew.bat :26.2:fabric:runClientGameTest -PsodiumProfile=beta
```

These full-parity GPU tests require a supported GPU and the same native runtime
prerequisites as the corresponding modes. DLSS remains user supplied. Configure
it in the validation profile or pass the existing development override:

```powershell
.\gradlew.bat :26.2:fabric:runClientGameTest -PdlssRuntimeDirectory='C:/SDKs/Streamline/bin/x64'
```

Tests use `run/26.2/validation`, `validation-sodium-stable`, or
`validation-sodium-beta`, keeping normal development worlds separate. Screenshots
and metrics are retained there. The validation mod lives in `src/gametest` and is
excluded from both release jars. A test-only shutdown mixin queues player cleanup
instead of blocking the client on Fabric's server tick barrier during disconnect.
Vanilla server shutdown still removes players and saves the disposable world.

This verifies the implemented feature paths on the tested GPU. It is not a
performance benchmark, an exhaustive test of every tuning combination, or
qualification of other GPU vendors and operating systems.
