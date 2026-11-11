# Intel XeSS

Choose **Video Settings → Anti Aliasing Settings → Intel XeSS Super Resolution**. The Windows x64
runtime is included, so no separate SDK download or Intel graphics card is required. Minecraft must
use its **Vulkan** backend; changing graphics backends requires restarting Minecraft.

Choose **Native Anti-Aliasing** for full-resolution temporal AA. The other presets render fewer scene
pixels and reconstruct the output: Ultra Quality Plus, Ultra Quality, Quality, Balanced, Performance,
and Ultra Performance. HUD/text and optional hand AA run at native output resolution. Sharpening is
optional and starts at zero. An RTX 3060 Ti uses Intel's cross-vendor implementation.

Unsupported GPUs/drivers/platforms display a reason and keep normal scene rendering. Advanced
controls show the loaded Intel runtime version. A failed evaluation reports fallback status; **Retry
rendering** retries the scene setup. Quality changes apply in the world and are saved with the mod's
other settings. XeSS is independent of the user's DLSS runtime setup.

XeSS's quality names have their own ratios: Quality is approximately 1.7× per axis, Balanced 2×,
Performance 2.3× and Ultra Performance 3×. Exact sizes come from the runtime. Native AA is 1×. Lower
input resolutions naturally lose fine detail, particularly at 720p/1080p output. The mod does not
apply a second temporal filter or force sharpening to change Intel's reconstruction.

The optional automatic texture MIP bias uses Intel's `log2(inputWidth/outputWidth)` recommendation
for XeSS. It remains an opt-in control shared with the existing modes. Leave the responsive-mask
debug view to FSR; XeSS uses captured object/particle velocities and its own temporal reconstruction.

Intel's license and third-party notices are included in the jar and available under **Credits and
Licenses**. See [native integration details](../native/xess_bridge/README.md) and the
[validation report](xess-validation.md) for the tested environment and limits.
