# Fabric compatibility and installation

All 25 main jars passed packaged GPU qualification on Windows 11 / RTX 3060 Ti, including 29 runtime profiles with zero Vulkan validation errors. See [validation coverage and limits](vulkanmod-port-progress.md) and the [exact artifact report](fabric-validation-report.json).

Choose the Salt's Anti Aliasing jar matching your exact Minecraft release. Install Fabric Loader 0.19.5 or newer, the matching Fabric API, and the linked VulkanMod build for the older targets below. Mod Menu is optional. Java requirements apply to the game launcher; building the project uses JDK 25.

| Minecraft | Tested VulkanMod build | Fabric API pin | Java |
| --- | --- | --- | --- |
| 1.18.2 | [0.2.5](https://modrinth.com/mod/vulkanmod/version/JOtvsq3o) | 0.76.0+1.18.2 | 17 |
| 1.19.2 | [0.3.1 (+1 build)](https://modrinth.com/mod/vulkanmod/version/W6KPVWT3) | 0.77.0+1.19.2 | 17 |
| 1.19.3 | [0.2.3](https://modrinth.com/mod/vulkanmod/version/mslg9G1S) | 0.75.1+1.19.3 | 17 |
| 1.19.4 | [0.3.2](https://modrinth.com/mod/vulkanmod/version/rGsZuGn8) | 0.87.2+1.19.4 | 17 |
| 1.20 | [0.5.2](https://modrinth.com/mod/vulkanmod/version/ZhvilXaM) | 0.83.0+1.20 | 17 |
| 1.20.1 | [0.5.2](https://modrinth.com/mod/vulkanmod/version/ZhvilXaM) | 0.92.12+1.20.1 | 17 |
| 1.20.2 | [0.3.9](https://modrinth.com/mod/vulkanmod/version/nC4iQVVW) | 0.91.6+1.20.2 | 17 |
| 1.20.3 | [0.4.7](https://modrinth.com/mod/vulkanmod/version/hZjQjfpD) | 0.91.1+1.20.3 | 17 |
| 1.20.4 | [0.4.7](https://modrinth.com/mod/vulkanmod/version/hZjQjfpD) | 0.97.3+1.20.4 | 17 |
| 1.20.5 | [0.4.2](https://modrinth.com/mod/vulkanmod/version/bPsylH8I) | 0.97.8+1.20.5 | 21 |
| 1.20.6 | [0.4.2](https://modrinth.com/mod/vulkanmod/version/bPsylH8I) | 0.100.8+1.20.6 | 21 |
| 1.21 | [0.6.7+1.21.1](https://modrinth.com/mod/vulkanmod/version/2eqroDKD) | 0.102.0+1.21 | 21 |
| 1.21.1 | [0.6.7+1.21.1](https://modrinth.com/mod/vulkanmod/version/2eqroDKD) | 0.116.18+1.21.1 | 21 |
| 1.21.2 | [0.5.3](https://modrinth.com/mod/vulkanmod/version/mr9uxFGT) | 0.106.1+1.21.2 | 21 |
| 1.21.3 | [0.5.3](https://modrinth.com/mod/vulkanmod/version/mr9uxFGT) | 0.114.1+1.21.3 | 21 |
| 1.21.4 | [0.5.6](https://modrinth.com/mod/vulkanmod/version/RAtVo03C) | 0.119.4+1.21.4 | 21 |
| 1.21.5 | [0.5.6](https://modrinth.com/mod/vulkanmod/version/7pYvUnJk) | 0.128.2+1.21.5 | 21 |
| 1.21.9 | [0.6.6](https://modrinth.com/mod/vulkanmod/version/jkDMvvWU) | 0.134.1+1.21.9 | 21 |
| 1.21.10 | [0.6.6](https://modrinth.com/mod/vulkanmod/version/jkDMvvWU) | 0.138.4+1.21.10 | 21 |
| 1.21.11 | [0.6.8+1.21.11](https://modrinth.com/mod/vulkanmod/version/hWKRTXbQ) | 0.141.6+1.21.11 | 21 |
| 26.1 | [0.6.8+26.1.2](https://modrinth.com/mod/vulkanmod/version/CEmnv55N) | 0.155.3+26.1.2 | 25 |
| 26.1.1 | [0.6.8+26.1.2](https://modrinth.com/mod/vulkanmod/version/CEmnv55N) | 0.155.3+26.1.2 | 25 |
| 26.1.2 | [0.6.8+26.1.2](https://modrinth.com/mod/vulkanmod/version/CEmnv55N) | 0.155.3+26.1.2 | 25 |

Fabric API 0.155.3+26.1.2 advertises `~26.1-` compatibility and is pinned consistently for all three 26.1 releases.

Minecraft 26.2 and 26.3 use the built-in Vulkan renderer with their matching Fabric API and Java 25. Their jars preserve the existing optional Sodium integration. Use the Vulkan graphics backend when launching those releases.

Fabric API is constrained to 0.76.x for 1.18.2 and 0.75.x for 1.19.3 because later shader-registration hooks conflict with those native releases. VulkanMod does not advertise 1.21.6–1.21.8, so this branch does not provide those targets. The exact tested dependency hashes and source URLs are recorded in [the machine-readable matrix](../versions/vulkanmod/compatibility/build-targets.json) and inside each older jar.

All rendering methods and controls are available through **Options → Video Settings → Anti Aliasing Settings**, the native VulkanMod settings entry, or Mod Menu. DLSS uses a supported GPU and user-supplied NVIDIA runtime. FSR and XeSS retain the same native feature requirements and capability fallbacks as the existing 26.3 implementation.

Build all targets with `.\gradlew.bat build`. Per-target jars are under `versions/<minecraft>/fabric/build/libs/`; the root collection contains the 25 verified main jars in `build/libs/`. Validation mods, development jars, sources jars, and NVIDIA runtime DLLs are excluded from that release set.
