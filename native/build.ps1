# Сборка нативных бинарников для магнитолы (arm64-v8a, Android 9 / API 28).
#
#   .\native\build.ps1            # собрать всё из native/src
#   .\native\build.ps1 probe      # только probe
#
# NDK берётся из ANDROID_NDK_HOME, иначе из SDK (local.properties -> sdk.dir).

param([string]$Only = "")

$ErrorActionPreference = "Stop"

$ndk = $env:ANDROID_NDK_HOME
if (-not $ndk) {
    $sdk = "D:\LLM\_tools\android-sdk"
    $lp = Join-Path (Split-Path $PSScriptRoot -Parent) "local.properties"
    if (Test-Path $lp) {
        $line = (Get-Content $lp | Where-Object { $_ -match '^sdk\.dir=' })
        if ($line) { $sdk = ($line -replace '^sdk\.dir=', '') -replace '\\:', ':' -replace '\\\\', '\' }
    }
    $ndk = Join-Path $sdk "ndk\28.0.13004108"
}
if (-not (Test-Path $ndk)) { throw "NDK не найден: $ndk" }

$clang = Join-Path $ndk "toolchains\llvm\prebuilt\windows-x86_64\bin\clang++.exe"
$out = Join-Path $PSScriptRoot "build"
New-Item -ItemType Directory -Force $out | Out-Null

$targets = if ($Only) { @($Only) } else { Get-ChildItem (Join-Path $PSScriptRoot "src") -Filter *.cpp |
        ForEach-Object { $_.BaseName } }

foreach ($t in $targets) {
    $src = Join-Path $PSScriptRoot "src\$t.cpp"
    $dst = Join-Path $out $t
    Write-Host "собираю $t -> $dst"
    & $clang --target=aarch64-linux-android28 -std=c++17 -O2 -Wall -Wextra `
        -fPIE -pie -static-libstdc++ $src -o $dst -ldl
    if ($LASTEXITCODE -ne 0) { throw "сборка $t не удалась" }
}

Get-ChildItem $out | Select-Object Name, Length
