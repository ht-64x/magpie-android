# Fetches the optional preloads: Bun for Android and the WorkBuddy provider package.
# They land where the app copies them from at first start.
param([string]$Out = (Join-Path $PSScriptRoot '..\app\src\main\assets\preload'))
$ErrorActionPreference = 'Stop'
$bunDir = Join-Path $Out 'bun'
$pkgDir = Join-Path $Out 'magpie\plugins\node_modules\@magpie-community\opencode-workbuddy-auth'
New-Item -ItemType Directory -Force $bunDir, $pkgDir | Out-Null

$zip = Join-Path $env:TEMP 'bun-android.zip'
if (-not (Test-Path $zip)) {
  Invoke-WebRequest -Uri 'https://github.com/oven-sh/bun/releases/download/bun-v1.4.2/bun-linux-aarch64-android.zip' -OutFile $zip
}
Add-Type -AssemblyName System.IO.Compression.FileSystem
$z = [System.IO.Compression.ZipFile]::OpenRead($zip)
$e = $z.Entries | Where-Object { $_.Name -eq 'bun' } | Select-Object -First 1
if (-not $e) { throw 'bun entry not found' }
[System.IO.Compression.ZipFileExtensions]::ExtractToFile($e, (Join-Path $bunDir 'magpie-bun'), $true)
$z.Dispose()

$tgz = Join-Path $env:TEMP 'wb.tgz'
Invoke-WebRequest -Uri 'https://registry.npmjs.org/@magpie-community/opencode-workbuddy-auth/-/opencode-workbuddy-auth-0.2.37.tgz' -OutFile $tgz
$dir = Join-Path $env:TEMP 'wb-pkg'; Remove-Item $dir -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force $dir | Out-Null
tar -xzf $tgz -C $dir
Copy-Item (Join-Path $dir 'package\*') $pkgDir -Recurse -Force
'preloads ready: ' + $Out
