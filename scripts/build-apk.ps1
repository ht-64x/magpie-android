# Builds magpie-lite.apk with the SDK tools only (aapt2 + javac + d8 + apksigner).
# No Gradle, no AndroidX: the app is framework-only on purpose.
param(
    [string]$Sdk = $(if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { Join-Path $env:LOCALAPPDATA "Android\Sdk" }),
    [string]$BuildTools = "34.0.0",
    [string]$CoreSo = (Join-Path $PSScriptRoot "..\magpie-src\magpie-main\dist\magpie-android-arm64")
    [string]$Out = (Join-Path $PSScriptRoot "..\build\magpie-lite.apk")
)
$ErrorActionPreference = "Stop"
$here = $PSScriptRoot
$bt = Join-Path (Join-Path $Sdk "build-tools") $BuildTools
$jar = Join-Path (Join-Path (Join-Path $Sdk "platforms") "android-34") "android.jar"
foreach ($f in @($bt, $jar)) { if (-not (Test-Path $f)) { throw "missing: $f" } }
if (-not (Test-Path $CoreSo)) { throw "core not built: $CoreSo" }

$build = Join-Path $here "build"
foreach ($d in @($build, (Join-Path $build "gen"), (Join-Path $build "classes"), (Join-Path $build "dex"), (Join-Path $here "jniLibs\arm64-v8a"))) {
    New-Item -ItemType Directory -Force $d | Out-Null
}

Write-Host "1/7 aapt2 compile"
& (Join-Path $bt "aapt2.exe") compile --dir (Join-Path $here "res") -o (Join-Path $build "res.zip")
if ($LASTEXITCODE -ne 0) { throw "aapt2 compile failed" }

Write-Host "2/7 aapt2 link"
$base = Join-Path $build "base.apk"
& (Join-Path $bt "aapt2.exe") link -o $base -I $jar --manifest (Join-Path $here "AndroidManifest.xml") `
    (Join-Path $build "res.zip") --java (Join-Path $build "gen") --min-sdk-version 26 --target-sdk-version 34 --version-code 1 --version-name 0.1-lite
if ($LASTEXITCODE -ne 0) { throw "aapt2 link failed" }

Write-Host "3/7 javac"
$sources = @(Get-ChildItem -Path (Join-Path $here "src") -Recurse -Filter *.java | ForEach-Object { $_.FullName })
$sources += @(Get-ChildItem -Path (Join-Path $build "gen") -Recurse -Filter *.java | ForEach-Object { $_.FullName })
& javac -encoding UTF-8 -source 11 -target 11 -classpath $jar -d (Join-Path $build "classes") @sources 2>&1 | Out-String | Write-Host
if ($LASTEXITCODE -ne 0) { throw "javac failed" }

Write-Host "4/7 d8"
$classes = @(Get-ChildItem -Path (Join-Path $build "classes") -Recurse -Filter *.class | ForEach-Object { $_.FullName })
"classes: $($classes.Count)" | Write-Host
& (Join-Path $bt "d8.bat") --lib $jar --min-api 26 --output (Join-Path $build "dex") @classes 2>&1 | Out-String | Write-Host
if ($LASTEXITCODE -ne 0) { throw "d8 failed" }

Write-Host "5/7 package dex + native lib"
$soDir = Join-Path $here "jniLibs\arm64-v8a"
Copy-Item $CoreSo (Join-Path $soDir "libmagpiecore.so") -Force
$unsigned = Join-Path $build "unsigned.apk"
Copy-Item $base $unsigned -Force
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [System.IO.Compression.ZipFile]::Open($unsigned, "Update")
[System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip, (Join-Path $build "dex\classes.dex"), "classes.dex", "Optimal") | Out-Null
Get-ChildItem $soDir -File | ForEach-Object {
    [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip, $_.FullName, "lib/arm64-v8a/" + $_.Name, "Optimal") | Out-Null
}
$zip.Dispose()
# aapt2 -A writes backslashes on Windows, which AssetManager cannot open:
# add assets here with forward slashes instead.
$assetRoot = Join-Path $here "app\src\main\assets"
if (Test-Path $assetRoot) {
    $zip2 = [System.IO.Compression.ZipFile]::Open($unsigned, "Update")
    Get-ChildItem $assetRoot -Recurse -File | ForEach-Object {
        $rel = $_.FullName.Substring($assetRoot.Length + 1).Replace('\', '/')
        [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip2, $_.FullName, "assets/" + $rel, "Optimal") | Out-Null
    }
    $zip2.Dispose()
}

Write-Host "6/7 zipalign"
$aligned = Join-Path $build "aligned.apk"
& (Join-Path $bt "zipalign.exe") -f -p 4 $unsigned $aligned
if ($LASTEXITCODE -ne 0) { throw "zipalign failed" }

Write-Host "7/7 sign"
$ks = Join-Path $here "debug.keystore"
if (-not (Test-Path $ks)) {
    $keytool = Join-Path $env:JAVA_HOME "bin\keytool.exe"
    if (-not (Test-Path $keytool)) { $keytool = "keytool" }
    & $keytool -genkeypair -keystore $ks -storepass android -keypass android -alias androiddebugkey `
        -dname "CN=Android Debug,O=Android,C=US" -keyalg RSA -keysize 2048 -validity 10000 2>&1 | Out-String | Write-Host
}
& (Join-Path $bt "apksigner.bat") sign --ks $ks --ks-pass pass:android --key-pass pass:android --out $Out $aligned
if ($LASTEXITCODE -ne 0) { throw "apksigner failed" }

$size = [math]::Round((Get-Item $Out).Length / 1MB, 1)
Write-Host ""
Write-Host "APK: $Out ($size MB)"
& (Join-Path $bt "apksigner.bat") verify --print-certs $Out | Select-Object -First 3 | Write-Host


