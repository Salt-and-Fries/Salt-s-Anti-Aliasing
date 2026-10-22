param(
    [Parameter(Mandatory=$true)][string]$StreamlineSdk,
    [Parameter(Mandatory=$true)][string]$BridgeDll,
    [string]$OutputDirectory = 'build/dlss-release-runtime'
)
$ErrorActionPreference = 'Stop'
$sdkRoot = (Resolve-Path -LiteralPath $StreamlineSdk).Path
$bridgeFile = (Resolve-Path -LiteralPath $BridgeDll).Path
$outputPath = [IO.Path]::GetFullPath($OutputDirectory)
New-Item -ItemType Directory -Force -Path $outputPath | Out-Null
$runtimeFiles = @('sl.interposer.dll','sl.common.dll','sl.dlss.dll','nvngx_dlss.dll','NvLowLatencyVk.dll')
foreach ($file in $runtimeFiles) {
    $source = Join-Path $sdkRoot "bin/x64/$file"
    $signature = Get-AuthenticodeSignature -LiteralPath $source
    if ($signature.Status -ne 'Valid' -or $signature.SignerCertificate.Subject -notmatch 'NVIDIA') {
        throw "Release runtime must use original NVIDIA-signed production files: $file"
    }
    Copy-Item -LiteralPath $source -Destination (Join-Path $outputPath $file) -Force
}
Copy-Item -LiteralPath $bridgeFile -Destination (Join-Path $outputPath 'salts_dlss_bridge.dll') -Force
Copy-Item -LiteralPath (Join-Path $sdkRoot 'license.txt') -Destination (Join-Path $outputPath 'LICENSE-Streamline.txt') -Force
Copy-Item -LiteralPath (Join-Path $sdkRoot 'bin/x64/nvngx_dlss.license.txt') -Destination (Join-Path $outputPath 'LICENSE-NVIDIA-DLSS.txt') -Force
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'END-USER-TERMS.txt') -Destination (Join-Path $outputPath 'END-USER-TERMS.txt') -Force
$notice = @'
Salt's Anti Aliasing: optional DLSS Super Resolution runtime for Windows x64.
This software contains source code provided by NVIDIA Corporation.
Includes NVIDIA Streamline and DLSS production runtime components, distributed
under the accompanying NVIDIA licenses. The application is not sponsored or
endorsed by NVIDIA. SDK development tools and debug binaries are not included.
NVIDIA SDK distribution, release-notification, and attribution terms apply.
'@
[IO.File]::WriteAllText((Join-Path $outputPath 'NOTICE.txt'), $notice, [Text.UTF8Encoding]::new($false))
$manifestLines = foreach ($file in (Get-ChildItem -LiteralPath $outputPath -File | Where-Object {$_.Name -ne 'bundle.properties'} | Sort-Object Name)) {
    $hash = (Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
    "$($file.Name).sha256=$hash"
}
$manifestContent = $manifestLines -join "`n"
$bundleHash = [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData([Text.Encoding]::UTF8.GetBytes($manifestContent))).ToLowerInvariant()
[IO.File]::WriteAllText((Join-Path $outputPath 'bundle.properties'), "bundleId=$bundleHash`n$manifestContent`n", [Text.UTF8Encoding]::new($false))
Write-Output "Staged signed production DLSS runtime in $outputPath"
