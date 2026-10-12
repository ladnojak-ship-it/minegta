# Installs MCOnly.asi into GTA V Legacy (story mode only).
#
#   gta\install.ps1                     install into the Steam copy of GTA V
#   gta\install.ps1 -GameDir "D:\..."  install elsewhere
#   gta\install.ps1 -Remove             take exactly the files it added back out
#
# ScriptHookV.dll and its ASI loader (dinput8.dll) are only copied in if they are not there already:
# another mod's loader is never replaced. ScriptHookV refuses to run online, and this is story mode only:
# never run a modded GTA V near GTA Online (BattlEye).
param(
    [string] $GameDir = '',
    [switch] $Remove,
    [switch] $Force
)

$ErrorActionPreference = 'Stop'

$here = Split-Path -Parent $MyInvocation.MyCommand.Path
$build = Join-Path $here 'build'
$runtime = Join-Path $here 'third_party\runtime'

if (-not $GameDir) {
    $steam = @("${env:ProgramFiles(x86)}\Steam", "${env:ProgramFiles}\Steam") | Where-Object { Test-Path $_ }
    foreach ($path in $steam) {
        foreach ($library in "$path\steamapps\libraryfolders.vdf") {
            if (-not (Test-Path $library)) { continue }
            $libraries = Get-Content $library -Raw
            foreach ($match in [regex]::Matches($libraries, '"path"\s*"(.*?)"')) {
                $candidate = ($match.Groups[1].Value -replace '\\\\', '\') + '\steamapps\common\Grand Theft Auto V'
                if (Test-Path "$candidate\GTA5.exe") { $GameDir = $candidate; break }
            }
        }
        if (-not $GameDir -and (Test-Path "$path\steamapps\common\Grand Theft Auto V\GTA5.exe")) {
            $GameDir = "$path\steamapps\common\Grand Theft Auto V"
        }
        if ($GameDir) { break }
    }
}

if (-not $GameDir) { throw 'GTA V not found: pass -GameDir "D:\SteamLibrary\steamapps\common\Grand Theft Auto V"' }
if (-not (Test-Path "$GameDir\GTA5.exe")) { throw "no GTA5.exe in $GameDir" }

$log = "$GameDir\MCOnly.install.log"
if ($Remove) {
    Remove-Item -Force "$GameDir\MCOnly.asi" -ErrorAction SilentlyContinue
    Write-Host "removed $GameDir\MCOnly.asi"
    return
}

if (-not (Test-Path "$build\MCOnly.asi")) { throw "no MCOnly.asi in $build : run gta\build.bat first" }

# another mod's ASI loader stays put unless -Force
if ((Test-Path "$GameDir\dinput8.dll") -and -not $Force) {
    $sha = (Get-FileHash "$GameDir\dinput8.dll" -Algorithm SHA256).Hash
    $ours = if (Test-Path "$runtime\dinput8.dll") { (Get-FileHash "$runtime\dinput8.dll" -Algorithm SHA256).Hash } else { '' }
    if ($ours -and $sha -ne $ours) {
        throw "another mod's dinput8.dll is in $GameDir (ASI loader). Leave it, or pass -Force to replace it with ours."
    }
}

# the ASI loader that runs MCOnly.asi, and ScriptHookV that registers its script
if (-not (Test-Path "$GameDir\ScriptHookV.dll")) {
    if (-not (Test-Path "$runtime\ScriptHookV.dll")) { throw "ScriptHookV.dll is missing: run gta\fetch_sdk.ps1 -WithRuntime, or copy it yourself" }
    Copy-Item "$runtime\ScriptHookV.dll" $GameDir -Force
}
if (-not (Test-Path "$GameDir\dinput8.dll")) {
    if (-not (Test-Path "$runtime\dinput8.dll")) { throw "dinput8.dll (the ASI loader) is missing: run gta\fetch_sdk.ps1 -WithRuntime" }
    Copy-Item "$runtime\dinput8.dll" $GameDir -Force
}

Copy-Item "$build\MCOnly.asi" $GameDir -Force
Add-Content -Path $log -Value ("$(Get-Date -Format u) installed MCOnly.asi ({0})" -f (Get-FileHash "$build\MCOnly.asi").Hash)
Write-Host "installed MCOnly.asi into $GameDir"
Write-Host "start the passthrough Minecraft mod first, then GTA V in story mode (BattlEye off)."
