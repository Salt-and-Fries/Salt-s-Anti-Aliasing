# Minecraft anti-aliasing comparisons

Open index.html for the visual gallery. All originals are direct Minecraft screenshots at 480 × 270. The full-scene pairs show the same view twice; split-view images join the left half of the AA Off capture to the right half of each treatment. Both comparison formats are enlarged using nearest-neighbor scaling, so no extra smoothing is added.

The camera, time and weather were fixed in a copied single-player world with server ticks frozen. Clouds were disabled and no particles were present; this avoids changing animated elements. Captures at the beginning and end with AA Off are pixel-identical. All captures report the same camera position and orientation. Temporal methods use a warm-up of 120 frames; 800% SSAA used 32. Sharpening is 0%.

MSAA 16x was requested, but the GPU fell back to 8x; the label reports both. FXAA has no low/high quality preset, so its 0% and 100% subpixel control endpoints are shown. TAA's 8/32 jitter phases are control endpoints, not a promise of lower or higher image quality. Upscalers' Ultra Performance and Quality/Native AA/DLAA represent their lowest and highest render-resolution quality choices. FSR3 frame generation is an FSR3 toggle, and the Minecraft screenshots capture the rendered source frame, not the generated display frame; still images cannot demonstrate its frame-rate effect.

Capture metadata and runtime logs are in the parent folder. The scene is a real Minecraft world capture, not a generated illustration.
