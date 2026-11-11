# SGSR 1 and SGSR 2

This Fabric 26.3 / Java 25 branch provides **SGSR 1 Upscale** and **SGSR 2 Super Resolution** in Anti Aliasing Settings. Select Minecraft's Vulkan backend and restart before enabling AA. No Qualcomm DLL, Snapdragon GPU, neural accelerator or separate SGSR download is required. Both modes have been exercised on RTX 3060 Ti; Adreno hardware testing remains outstanding.

| Setting | Input size per axis | SGSR 1 | SGSR 2 |
| --- | ---: | --- | --- |
| Native | 100% | Full-resolution spatial filter | Full-resolution temporal AA |
| Quality | 1 / 1.5 | Spatial upscale | Temporal reconstruction |
| Balanced | 1 / 1.7 | Spatial upscale | Temporal reconstruction |
| Performance | 50% | Spatial upscale | Temporal reconstruction |

Each version remembers its own setting. These presets are the mod's scale choices, not a vendor certification program. At 1920×1080 output, Quality uses 1280×720 and Performance uses 960×540. Odd dimensions are rounded and clamped to at least one pixel.

Start with Quality, general sharpening 0%, and automatic or disabled texture MIP bias. SGSR 1 supports Off/FXAA/SMAA pre-AA. Advanced controls expose Qualcomm's default edge threshold 8/255, integrated edge detail 2, and optional directional edges. Its integrated detail remains when separate sharpening is zero. SGSR 2 performs its own temporal AA without another TAA pass afterward. HUD/text remain native; optional hand MSAA runs after reconstruction.

SGSR 1 can shimmer in motion because it has no temporal history. SGSR 2 can still show rejection, ghosting or detail loss around disocclusions, animated textures and transparency without reliable motion/depth. Low input resolution trades detail for performance. See [tester instructions](sgsr-testing.md).

## Reference implementation

Sources are pinned to Qualcomm's [SGSR repository](https://github.com/SnapdragonGameStudios/snapdragon-gsr/tree/d926f074bcb9d714e179f1ce0fcb9ee2eeb5074e), commit `d926f074bcb9d714e179f1ce0fcb9ee2eeb5074e`. SGSR 1 uses the standard 12-tap mobile filter or edge-direction variant. SGSR 2 uses the recommended **two-pass fragment** variant. See the official [SGSR 1](https://github.com/SnapdragonGameStudios/snapdragon-gsr/blob/d926f074bcb9d714e179f1ce0fcb9ee2eeb5074e/sgsr/v1/README.md) and [SGSR 2](https://github.com/SnapdragonGameStudios/snapdragon-gsr/blob/d926f074bcb9d714e179f1ce0fcb9ee2eeb5074e/sgsr/v2/README.md) integration notes.

The adapter preserves the filter equations and supplies:

- Linear SDR RGB, matching the reference sample's sRGB texture sampling: decode Minecraft's RGBA8 scene to linear RGBA16F, filter, then encode once for display. SGSR 2 history remains linear RGBA16F.
- Conventional depth by flipping Minecraft reverse-Z, with clip-to-previous-clip conjugated by the same Z flip.
- Entity/particle current-minus-previous NDC motion, encoded as `v * 0.2495 + 32767/65535`. Zero marks static surfaces; the reference shader reconstructs their camera motion from depth. Alpha/depth checks preserve visible geometry coverage. Diagnostics convert NDC to pixel units.
- Fixed 32-phase Halton jitter, `(+X, -Y)` texels, reference variance/FOV/still-camera constants, and output-resolution ping-pong history. Camera cuts, teleport, resize, mode/preset changes and reload reset history.
- Linear history sampling and explicit clamping of integer-fetch border taps, which sampler clamp alone does not cover. Output alpha is one for compositing.

RenderPearl owns descriptor bindings and transitions. GLES declarations/constructor precision syntax are adapted to GLSL 450. Color/motion adapter passes add cost beyond Qualcomm's two reconstruction passes; isolated SDK shader timings are not whole-game timings.

`python tools/import_sgsr.py` regenerates adapted resources from cached or downloaded pinned inputs, checking source hashes before writing. Source provenance and BSD notices ship under `assets/salts_anti_aliasing/licenses/sgsr/`; the license also opens in Credits and Licenses. `gradlew build` compiles all six fragments for Vulkan 1.2 and checks packaged resources.

The adapter currently targets standard SDR Minecraft. HDR and arbitrary shader packs are unqualified. SGSR adds no native architecture dependency; existing optional vendor runtimes retain their own platform requirements.
