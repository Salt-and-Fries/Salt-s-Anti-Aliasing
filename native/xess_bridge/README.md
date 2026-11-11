# Intel XeSS Vulkan bridge

The mod bundles the **unmodified Intel XeSS-SR 2.0.2 runtime** and its notices from Intel's SDK
commit `de0fb9c1c510661c571164e1418ceca8101dab69`. The bridge's headers are from that same commit.
`Build-XessRuntime.ps1` pins and verifies the runtime, builds the JNI bridge with CMake/MSVC and
regenerates the bundle checksums. Set `JAVA_HOME` to a JDK with JNI headers before building.

```powershell
./native/xess_bridge/Build-XessRuntime.ps1
./gradlew.bat build
```

The Intel binary is redistributable under the included Intel Simplified Software License. Keep
`LICENSE-Intel-XeSS.txt` and `third-party-programs.txt` beside it. The bridge belongs to this project.
The bundled headers retain Intel's copyright and SPDX MIT notices.

Integration contract:

- Query instance version/extensions and device extensions/features from the actual SDK. This
  runtime requests Vulkan 1.3. Enable only supported features and merge promoted 1.2/1.3 feature
  structures with Minecraft/FSR's chain. Unsupported systems preserve the requested mode and render
  a normal scene with an explanatory status.
- Query optimal input dimensions from the SDK; use at least `ceil(8 * outputPixels / inputPixels)`
  Halton samples. Keep history valid across ordinary frames and reset it on scene/size/preset changes.
- Decode Minecraft's display-encoded scene to RGBA16F linear SDR. Initialize with LDR input and
  reversed-depth flags, fixed exposure 1, undilated/un-jittered current-to-previous RG16F velocities
  in input pixels, and D32F reversed-Z depth. Encode the linear output back to display space once.
- Transition all inputs to shader-read-only and the output to general for the SDK call, with
  explicit dependencies before and after; restore Minecraft's general layouts afterward.
- No extra TAA, FSR/DLSS resolve, or automatic sharpening is applied to XeSS. HUD/text stay native;
  the optional existing hand-AA pass runs afterward.
- The current `(+jitterX, -jitterY)` API conversion is checked against Intel's Vulkan sample and
  same-scene sign-flip experiments. These experiments are in the disposable validation harness,
  never in the public mod.
- APIs run on the render thread. Pipeline compilation starts at context creation. Reinitialization
  and context retirement wait for submitted GPU work; ordinary frames never wait for device idle.

References: [Intel SDK](https://github.com/intel/xess/tree/de0fb9c1c510661c571164e1418ceca8101dab69),
[developer guide](https://github.com/intel/xess/blob/de0fb9c1c510661c571164e1418ceca8101dab69/doc/xess_sr_developer_guide_english.md),
[Vulkan sample](https://github.com/intel/xess/blob/de0fb9c1c510661c571164e1418ceca8101dab69/samples/basic_sample_super_resolution_vk/triangle.cpp).
