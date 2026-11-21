@echo off
REM ==============================================================================
REM Build the UNIVERSAL Hermes VM for Windows (bytecode v96 + RN bridge host fns)
REM
REM The bundled tools\hermes.exe is a STOCK build: it cannot host the universal
REM RN bridge. This script builds hermes-universal.exe from the patched sources.
REM Requirements (one of):
REM   - Visual Studio 2019/2022 with C++ workload (MSVC), or
REM   - Build Tools for Visual Studio, or
REM   - CMake 3.13+ (https://cmake.org) — will auto-detect the VS generator.
REM Optional: Git for Windows to apply the patch.
REM
REM Usage:
REM   1) git clone https://github.com/facebook/hermes.git hermes-src
REM      cd hermes-src && git checkout hermes-2024-02-20-RNv0.74.0-999cfd9979b5f57b1269119679ab8cdf60897de9
REM   2) git apply path\to\appplayer\tools\hermes-patch\hermes-universal-rnbridge.diff
REM   3) build_hermes_windows.bat path\to\hermes-src
REM   4) copy the produced hermes.exe over appplayer\tools\hermes.exe
REM ==============================================================================
setlocal enabledelayedexpansion
set "RUNNER_DIR=%~dp0"
set "HERMES_SRC=%~f1"
if "%HERMES_SRC%"=="" (
    echo Usage: build_hermes_windows.bat ^<path-to-patched-hermes-src^>
    echo Steps 1-2 in the header comment must be done first.
    exit /b 1
)

where cmake >nul 2>nul
if errorlevel 1 (
    echo [-] CMake not found on PATH. Install CMake 3.13+ first.
    exit /b 1
)

cd /d "%HERMES_SRC%"
echo [*] Configuring hermes (Release, CLI tools)...
cmake -B build -DCMAKE_BUILD_TYPE=Release -DHERMES_BUILD_APPLE_TV=OFF
if errorlevel 1 exit /b 1
echo [*] Building hermes + hermesc (this takes 10-30 minutes)...
cmake --build build --config Release --target hermes hermesc
if errorlevel 1 exit /b 1
echo [+] Build complete. Copying binaries into the runner...
copy /y "%HERMES_SRC%\build\bin\hermes.exe" "%RUNNER_DIR%..\tools\hermes.exe" >nul
copy /y "%HERMES_SRC%\build\bin\hermesc.exe" "%RUNNER_DIR%..\tools\hermesc.exe" >nul
echo [+] tools\hermes.exe is now the UNIVERSAL RN bridge VM.
echo [+] Recompile the driver and relaunch:
echo     tools\hermesc.exe -emit-binary -out tools\rn\driver.hbc tools\rn\driver.js
endlocal
