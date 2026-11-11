param(
    [string]$BuildDirectory = "$PSScriptRoot/../../build/xess-bridge",
    [string]$VulkanHeaders = 'C:/SDKs/Vulkan-Headers'
)
$ErrorActionPreference = 'Stop'
$commit = 'de0fb9c1c510661c571164e1418ceca8101dab69'
$runtimeSha256 = '251659dd84a3e84de67c886a4186e01f3eca49b00641906fe38bb6b807e5d5b7'
$redist = [IO.Path]::GetFullPath("$PSScriptRoot/../redist/xess/windows-x86_64")
New-Item -ItemType Directory -Force -Path $redist | Out-Null
if (!(Test-Path -LiteralPath "$redist/libxess.dll")) {
    Invoke-WebRequest -Uri "https://raw.githubusercontent.com/intel/xess/$commit/bin/libxess.dll" -OutFile "$redist/libxess.dll"
}
if ((Get-FileHash -LiteralPath "$redist/libxess.dll" -Algorithm SHA256).Hash.ToLowerInvariant() -ne $runtimeSha256) {
    throw 'XeSS runtime does not match the pinned Intel release'
}
cmake -S $PSScriptRoot -B $BuildDirectory "-DSALTS_VULKAN_HEADERS=$VulkanHeaders"
if ($LASTEXITCODE -ne 0) { throw 'XeSS bridge configure failed' }
cmake --build $BuildDirectory --config Release
if ($LASTEXITCODE -ne 0) { throw 'XeSS bridge build failed' }
Copy-Item -LiteralPath "$BuildDirectory/Release/salts_xess_bridge.dll" -Destination "$redist/salts_xess_bridge.dll"
$manifest = @("sdk.commit=$commit", 'sdk.version=2.0.2')
foreach ($name in @('salts_xess_bridge.dll','libxess.dll','LICENSE-Intel-XeSS.txt','third-party-programs.txt')) {
    $manifest += $name + '.sha256=' + (Get-FileHash -LiteralPath "$redist/$name" -Algorithm SHA256).Hash.ToLowerInvariant()
}
Set-Content -LiteralPath "$redist/bundle.properties" -Value $manifest -Encoding ascii
