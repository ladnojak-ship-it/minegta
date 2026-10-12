# Fetches the ScriptHookV SDK (headers + import library) into gta/third_party/shv.
#
# ScriptHookV is not redistributed here, so the build downloads it the way the game would: from
# dev-c.com, with browser headers. It never writes anything into the game folder.
param(
    # keep the runtime DLLs too (ScriptHookV.dll and its dinput8.dll ASI loader), for gta/install.ps1
    [switch] $WithRuntime
)

$ErrorActionPreference = 'Stop'

$here = Split-Path -Parent $MyInvocation.MyCommand.Path
$dest = Join-Path $here 'third_party\shv'
New-Item -ItemType Directory -Force -Path $dest | Out-Null

$agent = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36'
$headers = @{ 'Accept' = 'text/html'; 'Accept-Language' = 'en-US' }

$page = Invoke-WebRequest -Uri 'https://www.dev-c.com/gtav/scripthookv/' -UserAgent $agent -Headers $headers -TimeoutSec 60
$links = [regex]::Matches($page.Content, 'href="(/files/ScriptHookV_[^"]+\.zip)"') |
    ForEach-Object { $_.Groups[1].Value } | Sort-Object -Unique

$sdk = $links | Where-Object { $_ -match 'SDK' } | Select-Object -First 1
if (-not $sdk) { throw "no ScriptHookV SDK on https://www.dev-c.com/gtav/scripthookv/ (found: $($links -join ', '))" }

$temp = Join-Path $env:TEMP 'minegta-shv'
Remove-Item -Recurse -Force $temp -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path $temp | Out-Null

$sdkZip = Join-Path $temp 'sdk.zip'
Write-Host "downloading $sdk"
Invoke-WebRequest -Uri ("https://www.dev-c.com" + $sdk) -UserAgent $agent -Headers @{ 'Referer' = 'https://www.dev-c.com/gtav/scripthookv/' } -OutFile $sdkZip -TimeoutSec 120

Expand-Archive -LiteralPath $sdkZip -DestinationPath $temp -Force
Push-Location $temp
try {
    $sdkRoot = (Get-ChildItem -Recurse -Directory -Filter 'inc' | Select-Object -First 1).FullName
    foreach ($header in @('main.h', 'types.h', 'nativeCaller.h', 'natives.h')) {
        $found = Get-ChildItem -Recurse -Path $temp -Filter $header -ErrorAction SilentlyContinue | Select-Object -First 1
        if ($found) { Copy-Item $found.FullName $dest -Force }
        else { Write-Warning "no $header in the SDK" }
    }
    $lib = Get-ChildItem -Recurse -Path $temp -Filter 'ScriptHookV.lib' -ErrorAction SilentlyContinue | Select-Object -First 1
    if (-not $lib) { throw 'no ScriptHookV.lib in the SDK' }
    Copy-Item $lib.FullName $dest -Force
}
finally {
    Pop-Location
}

Write-Host "ScriptHookV SDK in $dest :" ((Get-ChildItem $dest | ForEach-Object { $_.Name }) -join ', ')

if ($WithRuntime) {
    $runtimeZip = $links | Where-Object { $_ -notmatch 'SDK' } | Select-Object -First 1
    if (-not $runtimeZip) { Write-Warning 'no ScriptHookV runtime zip on the page: get ScriptHookV.dll and dinput8.dll by hand'; return }
    $zip = Join-Path $temp 'runtime.zip'
    Write-Host "downloading $runtimeZip"
    Invoke-WebRequest -Uri ("https://www.dev-c.com" + $runtimeZip) -UserAgent $agent -Headers @{ 'Referer' = 'https://www.dev-c.com/gtav/scripthookv/' } -OutFile $zip -TimeoutSec 120
    $runDir = Join-Path $here 'third_party\runtime'
    New-Item -ItemType Directory -Force -Path $runDir | Out-Null
    Expand-Archive -LiteralPath $zip -DestinationPath $temp -Force
    foreach ($dll in @('ScriptHookV.dll', 'dinput8.dll')) {
        $found = Get-ChildItem -Recurse -Path $temp -Filter $dll -ErrorAction SilentlyContinue | Select-Object -First 1
        if ($found) { Copy-Item $found.FullName $runDir -Force }
    }
    Write-Host "runtime in $runDir :" ((Get-ChildItem $runDir | ForEach-Object { $_.Name }) -join ', ')
}
