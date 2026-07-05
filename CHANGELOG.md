# Changelog

## Current Features

### Anti-Aliasing And Image Modes

- Added `Off` as a clean vanilla baseline mode.
- Added `FXAA` as a lightweight post-process anti-aliasing option.
- Added `MSAA` with `2x`, `4x`, `8x`, and `16x` sample options.
- Added `SSAA` with `125%`, `150%`, `175%`, and `200%` render-scale options.
- Added `SMAA` as a higher-quality spatial anti-aliasing option.
- Added `TAA` as a temporal anti-aliasing option with history-based smoothing.
- Added `NIS Sharpen` with adjustable sharpness strength.
- Added `NIS Upscale` with `Quality`, `Balanced`, `Performance`, and `Ultra Performance` presets.
- Added `FSR1 Upscale` with `Quality`, `Balanced`, `Performance`, and `Ultra Performance` presets.
- Added `FSR1 + RCAS` with the same preset options plus sharpening control.

### Video Settings Integration

- Added an in-game anti-aliasing mode selector to the Video Settings screen.
- Added always-visible AA control bars that gray out when the selected mode does not use them.
- Added a `Sharpness` slider for sharpen-capable modes.
- Added an `MSAA Samples` slider for `MSAA`.
- Added an `SSAA Scale` slider for `SSAA`.
- Added an `Upscale Quality` slider for NIS and FSR1 upscale modes.
- Added live tooltips that explain what each option does and what each AA mode is best used for.
- Added automatic disabling of the AA selector while `Improved Transparency` is enabled.
- Updated the AA settings layout to fit the native two-column Video Settings style.

### Rendering Rules And Compatibility

- Kept all AA, sharpening, supersampling, and upscaling effects limited to the 3D scene.
- Kept HUD, menus, and other UI elements out of the AA and upscale passes so they stay crisp.
- Added scene-only render-target control for post-process and internal-resolution paths.
- Added GPU-side handling for multisample resolve, supersample resolve, spatial upscale resolve, and temporal history flow.
- Added backend-neutral render structure so the mod is organized for renderer-specific ports.

### Debug Tools

- Added an edge-debug display that can be toggled with `F3 + K`.
- Added a black-and-white edge preview for checking edge detection coverage.
- Added on-screen edge-debug statistics for quick quality inspection.

### Config And Persistence

- Added a JSON config file for the mod's settings.
- Added persistence for AA mode, sharpness, sample level, upscale quality, SSAA scale, and debug toggles.
- Added `recordMetrics` to the config, disabled by default.

### Performance Metrics

- Added optional performance recording to JSON reports when `recordMetrics` is enabled.
- Added session-wide metrics such as average FPS, median FPS, 1% low FPS, 5% low FPS, frame-time stats, lag spikes, severe spikes, FPS drops, and low-FPS time.
- Added per-mode metrics so each AA mode records its own totals and per-minute rates.
- Added live metrics output to `logs/salts_anti_aliasing_metrics_latest.json`.
- Added archived per-session metrics files in the game `logs` folder on shutdown.

### General Polish

- Added scroll-position preservation when changing AA modes in Video Settings.
- Added mode-aware settings behavior so controls update live without restarting the game.
- Added multiple rounds of visual and performance tuning for FXAA, TAA, MSAA, SSAA, NIS, and FSR1 paths.
