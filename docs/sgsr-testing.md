# SGSR hardware test handoff

Send this guide with the SGSR jar. Replace the existing Salt's Anti Aliasing jar; install only one copy. Requirements: **Minecraft Java 26.3, Fabric Loader 0.19.5 or newer, matching Fabric API, Java 25, Minecraft's Vulkan backend**. This is not a Bedrock/Android application. A Snapdragon device must already support that Java/Fabric/Vulkan configuration through its chosen runtime. SGSR itself needs no vendor DLL.

Both modes already render on RTX 3060 Ti. Snapdragon/Adreno driver, performance and visual feedback is still needed. Start without shader packs or other rendering mods, then repeat with your usual compatible setup and identify its versions. Sodium 0.9.2+mc26.3 was checked separately on desktop.

## Test sequence

1. Select Vulkan in Video Settings and restart. Select **SGSR 1 Upscale**, Quality, sharpening 0%. Renderer status should report SGSR and an internal resolution, with no fallback message.
2. Inspect foliage, fences, thin edges, distant terrain, sky and text. Pan slowly, turn quickly, and try Native/Balanced/Performance. Compare pre-AA Off/FXAA/SMAA. Try advanced directional edges, initially leaving threshold/detail at defaults.
3. Select **SGSR 2 Super Resolution** and repeat all qualities. Hold the camera still for convergence, walk sideways, expose hidden surfaces, and watch moving mobs, particles, water, glass and animated textures. Try improved transparency on/off.
4. Show the HUD and hold an item. Check sharp text and hand without trails or duplicated edges; compare hand AA Off and 4× MSAA.
5. Resize/fullscreen, reload resources, teleport, switch Off/back, leave/re-enter the world, and exit normally. Inspect transition frames for flashes, stretched output or stale history. If fallback occurs, record its message and try **Retry rendering** once.
6. For performance, use the same saved view, output size, render distance and settings. Disable VSync/frame caps, warm up, and compare Off / SGSR 1 Quality / SGSR 2 Quality over at least 30 seconds. On mobile repeat after several minutes to expose thermal throttling.

Low-resolution spatial aliasing is expected. Persistent trails, black frames, brightness shifts, border seams and transition corruption are useful defect reports. Motion problems benefit from video.

## Feedback

```text
Device / SoC / exact GPU:
OS, Vulkan driver, Java/game runtime:
Minecraft / Fabric Loader / Fabric API / rendering mod versions:
Jar SHA-256:
Output size, render distance, resource/shader packs:
SGSR version and quality; pre-AA/directional edges if SGSR 1:
Sharpening, MIP bias, hand AA, improved transparency:
Renderer status / internal resolution:
Issue or performance, including Off baseline:
Reproduction steps; whether Retry/restart helps:
Time until thermal throttling, if applicable:
```

Return `logs/latest.log`, `config/salts_anti_aliasing.json`, screenshots/video and metrics if enabled. Remove private paths/server addresses as needed. Compare the same camera and resolution. Report inability to launch Java/Fabric/Vulkan separately from an SGSR failure.

For timing comparisons, leave Record Metrics off: synchronous diagnostic snapshots can add periodic frame-time spikes. Enable it for a separate diagnostic run if needed. One extreme screenshot/reload automation test hit a native Java 25 compiler crash; ordinary reload checks passed. Report any reload crash with the Java version and `hs_err`/OS crash report; see [validation details](sgsr-validation.md).
