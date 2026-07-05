package org.betterLostItems.salts_anti_aliasing.client.compat.sodium;

import net.caffeinemc.mods.sodium.api.config.ConfigEntryPoint;
import net.caffeinemc.mods.sodium.api.config.ConfigState;
import net.caffeinemc.mods.sodium.api.config.structure.ConfigBuilder;
import net.caffeinemc.mods.sodium.api.config.structure.EnumOptionBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.betterLostItems.salts_anti_aliasing.SaltsAntiAliasing;
import org.betterLostItems.salts_anti_aliasing.client.SaltsAntiAliasingClient;
import org.betterLostItems.salts_anti_aliasing.client.config.AntiAliasingConfig;
import org.betterLostItems.salts_anti_aliasing.client.config.AntiAliasingMode;
import org.betterLostItems.salts_anti_aliasing.client.config.MsaaSampleLevel;
import org.betterLostItems.salts_anti_aliasing.client.config.NisUpscaleQualityPreset;
import org.betterLostItems.salts_anti_aliasing.client.config.SsaaScaleLevel;
import org.betterLostItems.salts_anti_aliasing.client.gui.ClientText;
import org.betterLostItems.salts_anti_aliasing.client.render.common.RenderRuntime;

import java.util.Set;

/**
 * Implements salts anti aliasing sodium config behavior for Salt's Anti Aliasing. Compatibility
 * glue code that cooperates with optional mods without making them hard dependencies.
 */
public final class SaltsAntiAliasingSodiumConfig implements ConfigEntryPoint {
    private static final Identifier MODE_ID = id("mode");
    private static final Identifier SHARPNESS_ID = id("sharpness");
    private static final Identifier MSAA_SAMPLES_ID = id("msaa_samples");
    private static final Identifier SSAA_SCALE_ID = id("ssaa_scale");
    private static final Identifier UPSCALE_QUALITY_ID = id("upscale_quality");

    private static final String PAGE_TITLE_KEY = "screen.salts_anti_aliasing.config";
    private static final String GROUP_TITLE_KEY = "options.salts_anti_aliasing.group.image_quality";
    private static final String SHARPNESS_TOOLTIP_KEY = "options.salts_anti_aliasing.sharpness.tooltip";
    private static final String MSAA_TOOLTIP_KEY = "options.salts_anti_aliasing.msaa_samples.tooltip";
    private static final String SSAA_TOOLTIP_KEY = "options.salts_anti_aliasing.ssaa_scale.tooltip";
    private static final String UPSCALE_TOOLTIP_KEY = "options.salts_anti_aliasing.upscale_quality.tooltip";

    /**
     * Coordinates register config late within the anti-aliasing render, configuration, or compatibility flow.
     * @param builder builder value supplied by the caller or Minecraft callback
     */
    @Override
    public void registerConfigLate(ConfigBuilder builder) {
        builder.registerOwnModOptions()
                .addPage(builder.createOptionPage()
                        .setName(Component.translatable(PAGE_TITLE_KEY))
                        .addOptionGroup(builder.createOptionGroup()
                                .setName(Component.translatable(GROUP_TITLE_KEY))
                                .addOption(createModeOption(builder))
                                .addOption(builder.createIntegerOption(SHARPNESS_ID)
                                        .setName(Component.translatable("options.salts_anti_aliasing.sharpness", Component.empty()))
                                        .setTooltip(Component.translatable(SHARPNESS_TOOLTIP_KEY))
                                        .setStorageHandler(SaltsAntiAliasingSodiumConfig::afterSave)
                                        .setBinding(
                                                SaltsAntiAliasingSodiumConfig::setSharpnessPercent,
                                                SaltsAntiAliasingSodiumConfig::sharpnessPercent
                                        )
                                        .setDefaultValue(defaultSharpnessPercent())
                                        .setRange(
                                                sharpnessPercent(AntiAliasingConfig.MIN_SHARPEN_STRENGTH),
                                                sharpnessPercent(AntiAliasingConfig.MAX_SHARPEN_STRENGTH),
                                                1
                                        )
                                        .setValueFormatter(value -> Component.literal(value + "%"))
                                        .setEnabledProvider(
                                                state -> antiAliasingAvailable()
                                                        && state.readEnumOption(MODE_ID, AntiAliasingMode.class).usesSharpenControl(),
                                                MODE_ID,
                                                ConfigState.UPDATE_ON_REBUILD
                                        ))
                                .addOption(createMsaaSamplesOption(builder))
                                .addOption(createSsaaScaleOption(builder))
                                .addOption(createUpscaleQualityOption(builder))
                        ));
    }

    /**
     * Coordinates create mode option within the anti-aliasing render, configuration, or compatibility flow.
     * @param builder builder value supplied by the caller or Minecraft callback
     * @return a newly created instance configured for the current mod/runtime context
     */
    private static EnumOptionBuilder<AntiAliasingMode> createModeOption(ConfigBuilder builder) {
        return builder.createEnumOption(MODE_ID, AntiAliasingMode.class)
                .setName(Component.translatable("options.salts_anti_aliasing.mode", Component.empty()))
                .setTooltip(mode -> Component.translatable(
                        "options.salts_anti_aliasing.mode.tooltip",
                        ClientText.label(mode),
                        ClientText.summary(mode)
                ))
                .setStorageHandler(SaltsAntiAliasingSodiumConfig::afterSave)
                .setBinding(SaltsAntiAliasingSodiumConfig::setMode, SaltsAntiAliasingSodiumConfig::mode)
                .setDefaultValue(AntiAliasingMode.OFF)
                .setAllowedValues(Set.copyOf(AntiAliasingMode.implementedModes()))
                .setElementNameProvider(ClientText::label)
                .setEnabledProvider(state -> antiAliasingAvailable(), ConfigState.UPDATE_ON_REBUILD);
    }

    /**
     * Coordinates create msaa samples option within the anti-aliasing render, configuration, or compatibility flow.
     * @param builder builder value supplied by the caller or Minecraft callback
     * @return a newly created instance configured for the current mod/runtime context
     */
    private static EnumOptionBuilder<MsaaSampleLevel> createMsaaSamplesOption(ConfigBuilder builder) {
        return builder.createEnumOption(MSAA_SAMPLES_ID, MsaaSampleLevel.class)
                .setName(Component.translatable("options.salts_anti_aliasing.msaa_samples", Component.empty()))
                .setTooltip(Component.translatable(MSAA_TOOLTIP_KEY))
                .setStorageHandler(SaltsAntiAliasingSodiumConfig::afterSave)
                .setBinding(SaltsAntiAliasingSodiumConfig::setMsaaSampleLevel, SaltsAntiAliasingSodiumConfig::msaaSampleLevel)
                .setDefaultValue(MsaaSampleLevel.defaultLevel())
                .setElementNameProvider(level -> Component.literal(level.label()))
                .setEnabledProvider(
                        state -> antiAliasingAvailable()
                                && state.readEnumOption(MODE_ID, AntiAliasingMode.class).usesMsaaSampleControl(),
                        MODE_ID,
                        ConfigState.UPDATE_ON_REBUILD
                );
    }

    /**
     * Coordinates create ssaa scale option within the anti-aliasing render, configuration, or compatibility flow.
     * @param builder builder value supplied by the caller or Minecraft callback
     * @return a newly created instance configured for the current mod/runtime context
     */
    private static EnumOptionBuilder<SsaaScaleLevel> createSsaaScaleOption(ConfigBuilder builder) {
        return builder.createEnumOption(SSAA_SCALE_ID, SsaaScaleLevel.class)
                .setName(Component.translatable("options.salts_anti_aliasing.ssaa_scale", Component.empty()))
                .setTooltip(Component.translatable(SSAA_TOOLTIP_KEY))
                .setStorageHandler(SaltsAntiAliasingSodiumConfig::afterSave)
                .setBinding(SaltsAntiAliasingSodiumConfig::setSsaaScaleLevel, SaltsAntiAliasingSodiumConfig::ssaaScaleLevel)
                .setDefaultValue(SsaaScaleLevel.defaultLevel())
                .setElementNameProvider(level -> Component.literal(level.label()))
                .setEnabledProvider(
                        state -> antiAliasingAvailable()
                                && state.readEnumOption(MODE_ID, AntiAliasingMode.class).usesSsaaScaleControl(),
                        MODE_ID,
                        ConfigState.UPDATE_ON_REBUILD
                );
    }

    /**
     * Coordinates create upscale quality option within the anti-aliasing render, configuration, or compatibility flow.
     * @param builder builder value supplied by the caller or Minecraft callback
     * @return a newly created instance configured for the current mod/runtime context
     */
    private static EnumOptionBuilder<NisUpscaleQualityPreset> createUpscaleQualityOption(ConfigBuilder builder) {
        return builder.createEnumOption(UPSCALE_QUALITY_ID, NisUpscaleQualityPreset.class)
                .setName(Component.translatable("options.salts_anti_aliasing.upscale_quality", Component.empty()))
                .setTooltip(Component.translatable(UPSCALE_TOOLTIP_KEY))
                .setStorageHandler(SaltsAntiAliasingSodiumConfig::afterSave)
                .setBinding(
                        SaltsAntiAliasingSodiumConfig::setUpscaleQualityPreset,
                        SaltsAntiAliasingSodiumConfig::upscaleQualityPreset
                )
                .setDefaultValue(NisUpscaleQualityPreset.defaultPreset())
                .setElementNameProvider(ClientText::label)
                .setEnabledProvider(
                        state -> antiAliasingAvailable()
                                && state.readEnumOption(MODE_ID, AntiAliasingMode.class).usesSpatialUpscaleQualityControl(),
                        MODE_ID,
                        ConfigState.UPDATE_ON_REBUILD
                );
    }

    /**
     * Handles id as part of the anti-aliasing render, configuration, or compatibility flow.
     * @param path path value supplied by the caller or Minecraft callback
     * @return id produced by this helper
     */
    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(SaltsAntiAliasing.MOD_ID, path);
    }

    /**
     * Handles after save as part of the anti-aliasing render, configuration, or compatibility flow.
     */
    private static void afterSave() {
    }

    /**
     * Coordinates anti aliasing available within the anti-aliasing render, configuration, or compatibility flow.
     * @return whether the operation or state is enabled
     */
    private static boolean antiAliasingAvailable() {
        return !Minecraft.getInstance().useShaderTransparency();
    }

    /**
     * Handles mode as part of the anti-aliasing render, configuration, or compatibility flow.
     * @return active anti-aliasing mode
     */
    private static AntiAliasingMode mode() {
        RenderRuntime runtime = SaltsAntiAliasingClient.runtimeOrNull();
        return runtime == null ? AntiAliasingMode.OFF : runtime.activeMode();
    }

    /**
     * Applies a requested mode after clamping unknown choices to a safe fallback.
     * @param mode anti-aliasing mode requested by UI, hotkey, or loaded config
     */
    private static void setMode(AntiAliasingMode mode) {
        SaltsAntiAliasingClient.runtime().setMode(mode);
    }

    /**
     * Coordinates sharpness percent within the anti-aliasing render, configuration, or compatibility flow.
     * @return sharpness percent produced by this helper
     */
    private static int sharpnessPercent() {
        RenderRuntime runtime = SaltsAntiAliasingClient.runtimeOrNull();
        return runtime == null ? defaultSharpnessPercent() : sharpnessPercent(runtime.sharpenStrength());
    }

    /**
     * Updates sharpness percent and keeps dependent render state in sync when necessary.
     * @param percent percent value supplied by the caller or Minecraft callback
     */
    private static void setSharpnessPercent(int percent) {
        SaltsAntiAliasingClient.runtime().setSharpenStrength(percent / 100.0f);
    }

    /**
     * Coordinates msaa sample level within the anti-aliasing render, configuration, or compatibility flow.
     * @return msaa sample level produced by this helper
     */
    private static MsaaSampleLevel msaaSampleLevel() {
        RenderRuntime runtime = SaltsAntiAliasingClient.runtimeOrNull();
        return runtime == null ? MsaaSampleLevel.defaultLevel() : runtime.msaaSampleLevel();
    }

    /**
     * Updates msaa sample level and keeps dependent render state in sync when necessary.
     * @param level level value supplied by the caller or Minecraft callback
     */
    private static void setMsaaSampleLevel(MsaaSampleLevel level) {
        SaltsAntiAliasingClient.runtime().setMsaaSampleLevel(level);
    }

    /**
     * Handles ssaa scale level as part of the anti-aliasing render, configuration, or compatibility
     * flow.
     * @return ssaa scale level produced by this helper
     */
    private static SsaaScaleLevel ssaaScaleLevel() {
        RenderRuntime runtime = SaltsAntiAliasingClient.runtimeOrNull();
        return runtime == null ? SsaaScaleLevel.defaultLevel() : runtime.ssaaScaleLevel();
    }

    /**
     * Updates ssaa scale level and keeps dependent render state in sync when necessary.
     * @param level level value supplied by the caller or Minecraft callback
     */
    private static void setSsaaScaleLevel(SsaaScaleLevel level) {
        SaltsAntiAliasingClient.runtime().setSsaaScaleLevel(level);
    }

    /**
     * Coordinates upscale quality preset within the anti-aliasing render, configuration, or compatibility flow.
     * @return upscale quality preset produced by this helper
     */
    private static NisUpscaleQualityPreset upscaleQualityPreset() {
        RenderRuntime runtime = SaltsAntiAliasingClient.runtimeOrNull();
        return runtime == null ? NisUpscaleQualityPreset.defaultPreset() : runtime.upscaleQualityPreset();
    }

    /**
     * Updates upscale quality preset and keeps dependent render state in sync when necessary.
     * @param preset quality preset selected by the user or loaded from config
     */
    private static void setUpscaleQualityPreset(NisUpscaleQualityPreset preset) {
        SaltsAntiAliasingClient.runtime().setUpscaleQualityPreset(preset);
    }

    /**
     * Coordinates default sharpness percent within the anti-aliasing render, configuration, or compatibility flow.
     * @return default sharpness percent produced by this helper
     */
    private static int defaultSharpnessPercent() {
        return sharpnessPercent(AntiAliasingConfig.DEFAULT_SHARPEN_STRENGTH);
    }

    /**
     * Coordinates sharpness percent within the anti-aliasing render, configuration, or compatibility flow.
     * @param sharpenStrength normalized sharpening amount requested by the user interface
     * @return sharpness percent produced by this helper
     */
    private static int sharpnessPercent(float sharpenStrength) {
        return Math.round(sharpenStrength * 100.0f);
    }
}
