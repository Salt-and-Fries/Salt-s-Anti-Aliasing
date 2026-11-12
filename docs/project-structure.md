# Project Structure

`multi-version-test` follows the Prism layout used by the template at
`C:\Users\texca\IdeaProjects\template`. Minecraft **26.2 Fabric** and **26.3 Fabric**
use Minecraft's built-in Vulkan renderer. Another 23 Fabric targets use pinned
official VulkanMod releases. Their 15 typed adapter families share a Java 17 GPU
backend. See [current packaged validation](vulkanmod-port-progress.md) before
treating a new target as qualified.

```text
build.gradle.kts                  Prism metadata, repositories, target dependencies
settings.gradle.kts               Shared common + per-version common/Fabric project matrix
gradle.properties                 Existing pinned dependencies and mod version
gradle/fabric.gradle              Fabric runs, tests, publication, native packaging checks
versions/
  common/src/main/
    java/                        Loader-free config, render API, pipeline planning
    resources/                   Language, icon, and third-party shader notices
  vulkanmod/
    src/main/                    Java 17 GPU backend and native ownership
    src/test/                    Backend, GLSL/SPIR-V, and real GPU regressions
    fabric/src/main/             Shared Fabric settings, metrics, SDK integration
    adapters/mc*/                Typed renderer, GUI, and disposable-world tests
    adapters/shared_object_backend/ Shared ownership hooks for newer native backends
    compatibility/               Official target list and exact dependency hashes
  <older version>/fabric/src/main/ Per-release mod metadata and mixin registration
  26.2/ and 26.3/
    common/src/main/
      java/                      Version-specific renderer policies and shader patch helper
      resources/                 Version-specific GLSL shaders and pack metadata
    fabric/src/
      main/                      Fabric initializer and fabric.mod.json
      client/                    Client initializer, renderer, GUI, metrics, mixins
      test/                      All existing unit and integration checks
      gametest/                  Separate GPU validation mod, excluded from releases
native/                          Existing bridge sources and bundled native runtimes
```

Java packages and runtime resource paths are preserved. Prism compiles the shared
and version-common sources into the Fabric jar. The client runtime stays in Loom's
client source set, including code that calls Fabric-backed configuration, metrics,
or optional-mod detection. The 26.2 adapters use Blaze3D and GLFW; 26.3 uses
RenderPearl and SDL. Older targets use VulkanMod-specific adapters; the 26.2 and
26.3 jars do not depend on VulkanMod. Fabric is the only implemented loader.

The Gradle projects are `:common`, `:vulkanmod`, and per-release
`:<minecraft>:common` / `:<minecraft>:fabric` projects. Each version-common
project uses NeoForm's vanilla Minecraft development artifacts; this does not add
a NeoForge mod target or a NeoForge dependency to the Fabric jar.

## Commands

Run Gradle on **JDK 25 or newer**. The wrapper pins Gradle 9.7.1 and the settings
pin Prism 0.6.8. The full 25-target build reserves up to 6 GiB for Gradle and limits workers to two for source remapping. For example, in PowerShell with a local JDK 25 installation:

```powershell
$env:JAVA_HOME = 'C:/Program Files/Java/jdk-25.0.3'
.\gradlew.bat projects
.\gradlew.bat build
.\gradlew.bat :26.2:fabric:build
.\gradlew.bat :26.2:fabric:runClient
.\gradlew.bat :26.3:fabric:build
.\gradlew.bat :26.3:fabric:test
.\gradlew.bat :26.3:fabric:verifyPublicJar
.\gradlew.bat :26.3:fabric:runClient
.\gradlew.bat :26.3:fabric:runDatagen
.\gradlew.bat :1.20.1:fabric:verifyPublicJar
.\gradlew.bat :1.20.1:fabric:runPackagedClientValidation
.\gradlew.bat :26.3:fabric:runPackagedClientValidation
```

The short `runClient` and `runDatagen` commands select 26.3. Use a fully qualified
task path for other versions. Root `check` runs shared/modern tests and verifies
all 25 public jars. The root `build` collects exactly those main jars into
`build/libs/`, named `salts-anti-aliasing-mc<version>-1.1.jar`. It selects `remapJar`
for older mapped versions and `jar` for 26.x. Sources, development and validation
jars stay outside the release collection; stale files in that output are removed.
Maven artifact IDs also include the Minecraft version to keep publications distinct.

Development launches use the repository's `run/<version>` directory. Existing
Sodium profiles and a user-provided DLSS runtime work with the same arguments:

```powershell
.\gradlew.bat runClient -PsodiumProfile=stable
.\gradlew.bat runClient -PsodiumProfile=beta
.\gradlew.bat runClient -PdlssRuntimeDirectory='C:/SDKs/Streamline/bin/x64'
```

Sodium profile checksums, optional dependencies, and the public-jar verification
remain enabled. `native/redist` is resolved from the repository root, and NVIDIA's
user-supplied runtime stays excluded from distributable jars.

`python tools/import_sgsr.py` writes shaders to the 26.3 common resources by default.
Use `--minecraft-version 26.2` to regenerate 26.2 shaders with Blaze3D's
`#moj_import` directives. Provenance/licenses remain in shared common resources,
and the SDK cache stays under `build/sgsr-sdk`.

All targets provide `runClientGameTest` and `runPackagedClientValidation` for
in-world GPU validation. Both use a separate validation profile and mod, excluded
from release jars. Packaged validation uses the actual public jar and official
dependencies. Supply `-PclientJavaHome=...` to choose the client runtime, and
`-PvulkanValidationLogDirectory=...` with the Khronos layer environment to require
clean Vulkan teardown as well as rendering. Use `-PdlssRuntimeDirectory=...` for
the user-supplied NVIDIA runtime. Native prerequisites and validation limits are
recorded in the [port status](vulkanmod-port-progress.md).

## Scope of future ports

Keep shared common Java independent of Minecraft and loaders. Put version-specific
shader and renderer changes in that version's directory, and loader entrypoints,
metadata, and integration hooks in its loader directory. Register and implement
additional targets only when explicitly requested.

## Initial 26.3 layout migration validation (2026-11-11)

Compared against the clean baseline commit `a1df150`:

- All 287 original source/resource/test files are present in the new layout.
  All 226 Java files, including tests, retain their original bytes.
- All 254 runtime classes in the distributable are byte-identical to the baseline.
  All 69 packaged asset files, including shaders and native binaries, are also
  byte-identical. No original jar entry is missing.
- The expanded `fabric.mod.json` is identical. The empty common mixin config has
  only newline normalization. Loom's manifest reflects the moved portable classes,
  and `pack.mcmeta` is the sole new jar entry.
- `build` passed all 164 tests with zero failures, errors, or skipped tests.
  The public-jar guard also verifies that every registered entrypoint and mixin
  class is packaged, in addition to the existing native/shader checks.
- Both `stable` and `beta` Sodium runtime SHA-256 checks passed. Neither renderer
  is bundled in the public jar or added to the default development classpath.
- `runDatagen` completed successfully, including client initializer and mixin
  loading. The normal `runClient` loaded the existing development configuration,
  initialized Vulkan, reported DLSS and FSR ready, activated the FSR3
  frame-generation swapchain, loaded resources, and exited normally when closed.

Prism's older default Mixin/MixinExtras support dependencies are removed from
the Fabric compile/annotation-processor configurations so Fabric Loader supplies
the same API signatures as the original build. Without that adjustment, injector
annotations differed even though the source files had not changed.

This was a build, packaging, datagen, and client-startup verification. A full
in-world visual comparison of every anti-aliasing mode was not repeated.
