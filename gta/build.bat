@echo off
rem Builds MCOnly.asi (a ScriptHookV script plus a D3D11 presenter that draws Minecraft's picture
rem instead of GTA's, with no ReShade) with MSVC into gta\build.
rem
rem   gta\build.bat                build with the newest Visual Studio C++ x64 tools
rem   VCVARS=C:\...\vcvars64.bat   pick another toolchain
rem
rem Run gta\fetch_sdk.ps1 first (ScriptHookV SDK headers + ScriptHookV.lib).
setlocal
set HERE=%~dp0
if not defined VCVARS for /f "usebackq delims=" %%i in (`"%ProgramFiles(x86)%\Microsoft Visual Studio\Installer\vswhere.exe" -latest -products * -requires Microsoft.VisualStudio.Component.VC.Tools.x86.x64 -property installationPath`) do set "VCVARS=%%i\VC\Auxiliary\Build\vcvars64.bat"
if not exist "%VCVARS%" (
  echo No Visual Studio with the C++ x64 tools was found.
  echo Install "Desktop development with C++" (Visual Studio 2022 or its Build Tools), or set VCVARS to your vcvars64.bat
  exit /b 1
)
if not exist "%HERE%third_party\shv\ScriptHookV.lib" (
  echo third_party is missing: run gta\fetch_sdk.ps1 first
  exit /b 1
)
if not exist "%HERE%build" mkdir "%HERE%build"
call "%VCVARS%" >nul || exit /b 1
cl /nologo /LD /O2 /EHsc /std:c++20 /MT /W3 /DWIN32_LEAN_AND_MEAN /DNOMINMAX ^
  /I "%HERE%third_party\shv" ^
  "%HERE%src\script.cpp" "%HERE%src\ws.cpp" "%HERE%src\present.cpp" ^
  /Fo"%HERE%build\\" /Fe"%HERE%build\MCOnly.asi" ^
  /link "%HERE%third_party\shv\ScriptHookV.lib" ws2_32.lib user32.lib d3d11.lib d3dcompiler.lib
if errorlevel 1 (
  echo build failed
  exit /b 1
)
echo built "%HERE%build\MCOnly.asi"
