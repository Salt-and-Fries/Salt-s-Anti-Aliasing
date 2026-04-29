# Ideas

## Geometry-edge-only anti-aliasing

Explore an anti-aliasing mode that affects block/object edges while preserving texture detail inside block faces.

Current post-process AA works mostly from the final rendered frame, so block geometry edges and texture detail are already flattened together. A perfect split is not available from color alone, but a practical version should be possible:

- Use depth-aware edge detection to favor real geometry edges and ignore high-frequency texture changes on flat surfaces.
- Apply FXAA/SMAA-style blending only where depth discontinuities or strong silhouette signals are present.
- Keep color-only edge detection as a fallback, but weight it lower so pixel-art texture patterns stay sharp.
- Investigate whether a normal, material ID, object ID, or stencil-like buffer can be exposed or generated for more reliable geometry-edge masks.
- Longer-term, consider an earlier render-pipeline integration where block geometry is still distinguishable from texture detail.

Goal: reduce shimmer and jagged silhouettes without smearing Minecraft's block textures.
