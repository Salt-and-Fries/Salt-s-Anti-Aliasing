param(
    [Parameter(Mandatory)][string]$SdkPath,
    [Parameter(Mandatory)][string]$BuildDirectory,
    [string]$VulkanIncludeDirectory,
    [string]$VulkanImportLibrary
)
$ErrorActionPreference = 'Stop'
$sdkRoot = (Resolve-Path -LiteralPath $SdkPath).Path
$expected = 'c6efa6bf7f2027b3ec94f28578bb5965eabb9e55'
$revision = & git -C $sdkRoot rev-parse HEAD
if ($LASTEXITCODE -ne 0 -or $revision -ne $expected) { throw "Use FidelityFX SDK v1.1.4 at $expected" }
$patch = Join-Path $PSScriptRoot 'patches/fidelityfx-1.1.4-vulkan-correctness.patch'
& git -C $sdkRoot apply --reverse --check $patch 2>$null
if ($LASTEXITCODE -ne 0) {
    & git -C $sdkRoot apply --check $patch
    if ($LASTEXITCODE -ne 0) { throw 'The runtime patch does not apply to this SDK checkout' }
    & git -C $sdkRoot apply $patch
    if ($LASTEXITCODE -ne 0) { throw 'Applying the runtime patch failed' }
}
$cmakeArgs = @('-S', (Join-Path $sdkRoot 'ffx-api'), '-B', $BuildDirectory, '-A', 'x64', '-DFFX_API_BACKEND=VK_X64')
if ($VulkanIncludeDirectory) { $cmakeArgs += "-DVulkan_INCLUDE_DIR=$VulkanIncludeDirectory" }
if ($VulkanImportLibrary) { $cmakeArgs += "-DVulkan_LIBRARY=$VulkanImportLibrary" }
& cmake @cmakeArgs
if ($LASTEXITCODE -ne 0) { throw 'FidelityFX configuration failed' }
& cmake --build $BuildDirectory --config Release --parallel 8
if ($LASTEXITCODE -ne 0) { throw 'FidelityFX build failed' }
$runtime = Join-Path $sdkRoot 'ffx-api/bin/amd_fidelityfx_vk.dll'
Get-FileHash -LiteralPath $runtime -Algorithm SHA256
Write-Output "Built $runtime. Stage it in native/redist/windows-x86_64 with the upstream MIT license."
