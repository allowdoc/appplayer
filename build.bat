@echo off
setlocal enabledelayedexpansion
REM ==============================================================================
REM appplayer - Universal Android Runner build script (Windows)
REM Dynamic paths: everything is relative to this script's directory.
REM
REM Two-pass build (app-agnostic):
REM   pass 1  CORE engine + framework shims -> bin\      (compiled WITHOUT any
REM           app jar, against compile-stubs\ — never fails because of an app)
REM   pass 2  per-APP override classes    -> bin-app\   (compiled against the
REM           current app.jar; skipped with a warning when an app-incompatible
REM           source cannot resolve its symbols)
REM ==============================================================================
set "RUNNER_DIR=%~dp0"
cd /d "%RUNNER_DIR%"

echo ==================================================================
echo   BUILDING UNIVERSAL ANDROID RUNNER (Windows)
echo   Runner dir: %RUNNER_DIR%
echo ==================================================================

REM ---- Locate JDK (javac) ----
REM Strip literal quotes the user may have stored in JAVA_HOME (a classic cmd
REM trap: set JAVA_HOME="C:\Program Files\..." stores the quote characters).
if defined JAVA_HOME set JAVA_HOME=%JAVA_HOME:"=%
set "JAVAC_BIN="
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\javac.exe" set "JAVAC_BIN=%JAVA_HOME%\bin\javac.exe"
if not defined JAVAC_BIN (
    for /d %%D in ("%ProgramFiles%\Java\jdk-*") do if exist "%%D\bin\javac.exe" set "JAVAC_BIN=%%D\bin\javac.exe"
)
if not defined JAVAC_BIN (
    for /d %%D in ("%ProgramFiles%\Android\Android Studio\jbr") do if exist "%%D\bin\javac.exe" set "JAVAC_BIN=%%D\bin\javac.exe"
)
if not defined JAVAC_BIN (
    for /d %%D in ("%ProgramFiles%\Eclipse Adoptium\jdk-*") do if exist "%%D\bin\javac.exe" set "JAVAC_BIN=%%D\bin\javac.exe"
)
if not defined JAVAC_BIN (
    where javac >nul 2>nul && set "JAVAC_BIN=javac"
)
if not defined JAVAC_BIN (
    echo [-] No JDK found. Install JDK 17+ or set JAVA_HOME.
    exit /b 1
)
REM Safety net: never carry embedded quotes into a command invocation.
set JAVAC_BIN=%JAVAC_BIN:"=%
for %%F in ("%JAVAC_BIN%") do set "JAVA_EXE=%%~dpFjava.exe"
echo [+] Using javac: %JAVAC_BIN%

REM ---- App jar (optional for pass 1, needed for pass 2) ----
set "BASE_JAR=%RUNNER_DIR%app.jar"
if not exist "%BASE_JAR%" if not "%~1"=="" if exist "%~f1" (
    echo [*] No app jar yet - decompiling %~f1 with enjarify...
    pushd "%RUNNER_DIR%tools\enjarify"
    where python >nul 2>nul && python -O -m enjarify.main "%~f1" -o "%BASE_JAR%" -f
    if not exist "%BASE_JAR%" (
        where py >nul 2>nul && py -O -m enjarify.main "%~f1" -o "%BASE_JAR%" -f
    )
    if not exist "%BASE_JAR%" (
        where python3 >nul 2>nul && python3 -O -m enjarify.main "%~f1" -o "%BASE_JAR%" -f
    )
    if not exist "%BASE_JAR%" echo [-] enjarify failed. Install Python 3 ^(as "python" on PATH^).
    popd
)
set "APP_JAR=%BASE_JAR%"

REM ---- Record which APK app.jar came from (fingerprint marker) ----
if exist "%BASE_JAR%" if not "%~1"=="" if exist "%~f1" (
    if not exist "%RUNNER_DIR%data" mkdir "%RUNNER_DIR%data"
    > "%RUNNER_DIR%data\.appjar-src.txt" echo %~f1^|%~z1
)

REM ---- Pass 1: CORE engine + framework shims (compiled WITHOUT app jar) ----
if not exist bin mkdir bin
REM Drop stale per-app override classes from previous builds/APKs.
if exist bin\com\bugsnag rd /s /q bin\com\bugsnag
if exist bin\com\facebook\react rd /s /q bin\com\facebook\react
if exist bin\com\facebook\hermes rd /s /q bin\com\facebook\hermes
if exist bin\com\google\gson rd /s /q bin\com\google\gson
if exist bin\okio rd /s /q bin\okio
dir /s /b src\org src\android src\androidx src\com\android src\com\facebook\jni src\com\facebook\soloader src\com\facebook\react\turbomodule src\com\google\android src\dalvik src\libcore 2>nul | findstr /i "\.java$" > sources.txt
"%JAVAC_BIN%" -encoding UTF-8 -nowarn --release 17 -sourcepath "compile-stubs" -implicit:none ^
  -cp "bin;lib\sqlite-jdbc.jar;lib\runner-agent.jar;lib\android-all-10.jar;lib\dexlib2-2.5.2.jar;lib\guava-27.1-android.jar;lib\asm-9.6.jar;lib\asm-commons-9.6.jar;lib\skija-shared-0.143.18.jar;lib\types-0.2.0.jar" ^
  -d bin @sources.txt
if errorlevel 1 (
    echo [-] Core engine compilation failed.
    exit /b 1
)
echo [+] Core engine compiled (app-independent).

REM ---- Pass 2: per-app overrides -> bin-app (optional, warns on failure) ----
REM Each override group compiles INDEPENDENTLY: one app-incompatible group is
REM skipped without taking away the others (e.g. a bridge class this app needs).
if exist bin-app rd /s /q bin-app
if exist "%APP_JAR%" (
    set "OVERRIDE_CP=bin;lib\sqlite-jdbc.jar;lib\runner-agent.jar;lib\android-all-10.jar;lib\dexlib2-2.5.2.jar;lib\guava-27.1-android.jar;lib\asm-9.6.jar;lib\asm-commons-9.6.jar;lib\skija-shared-0.143.18.jar;lib\types-0.2.0.jar;%APP_JAR%"
    if exist app-overrides-errors.txt del /f /q app-overrides-errors.txt
    md bin-app 2>nul
    for %%D in ("com\bugsnag" "com\facebook\hermes" "com\facebook\react" "com\google\gson" "okio") do (
        if exist "src\%%~D" (
            dir /b /s "src\%%~D\*.java" > app-src.tmp 2>nul
            REM Unique temp dir per group: a leftover locked binapp-tmp from a
            previous run made javac -d fail for EVERY group and they all
            looked like "does not fit THIS apk".
            set "GRP_TMP=%TEMP%\binapp-tmp-%%RANDOM%%"
            if exist "!GRP_TMP!" rd /s /q "!GRP_TMP!"
            md "!GRP_TMP!" 2>nul
            "%JAVAC_BIN%" -encoding UTF-8 -nowarn --release 17 -cp "%OVERRIDE_CP%;bin-app" -d "!GRP_TMP!" @app-src.tmp 2>>app-overrides-errors.txt
            if errorlevel 1 (
                echo [!] Override group "%%~D" does not fit THIS apk - skipping it.
                REM Show the first real error lines so failures are diagnosable:
                powershell -NoProfile -Command "Get-Content '%RUNNER_DIR%app-overrides-errors.txt' -ErrorAction SilentlyContinue | Select-String -Pattern 'error:' | Select-Object -First 4 | ForEach-Object { Write-Host ('    ' + $_.Line) }" 2>nul
            ) else (
                xcopy /e /i /q /y "!GRP_TMP!" "bin-app" >nul
                echo [+] Override group "%%~D" compiled into bin-app.
            )
            rd /s /q "!GRP_TMP!" 2>nul
        )
    )
    dir /b /s "bin-app\*.class" >nul 2>nul && (
        echo [+] App-specific overrides ready in bin-app.
    ) || (
        echo [!] No app-specific overrides compiled ^(universal mode^). Details: app-overrides-errors.txt
    )
) else (
    echo [!] No app.jar yet - skipping per-app overrides. Run build.bat ^<apk^> to build them.
)

REM ---- Re-native Yoga JNI bridge classes + verify stack-map frames ----
if exist "%APP_JAR%" (
    "%JAVA_EXE%" -cp "bin;bin-app;lib\asm-9.6.jar;lib\asm-commons-9.6.jar;lib\android-all-10.jar;%APP_JAR%" org.headless.runner.tool.rn.RestoreNativeMethods "%APP_JAR%" lib/android-all-10.jar yoga-native-restored.jar
    echo [+] Yoga native-method overlay ready.
)
if exist "%RUNNER_DIR%yoga-native-restored.jar" (
    "%JAVA_EXE%" -cp "bin;lib\asm-9.6.jar;lib\asm-commons-9.6.jar" org.headless.runner.tool.BytecodeFrameNormalizer "%RUNNER_DIR%yoga-native-restored.jar"
)

REM ---- Compile RN driver to Hermes bytecode v96 ----
if exist "%RUNNER_DIR%tools\hermesc.exe" (
    "%RUNNER_DIR%tools\hermesc.exe" -emit-binary -out "%RUNNER_DIR%tools\rn\driver.hbc" "%RUNNER_DIR%tools\rn\driver.js"
    echo [+] RN driver compiled to Hermes bytecode.
) else (
    if not exist "%RUNNER_DIR%tools\rn\driver.hbc" (
        echo [-] No hermesc.exe and no prebuilt driver.hbc. Aborting.
        exit /b 1
    )
    echo [!] hermesc.exe missing - using prebuilt driver.hbc.
)

echo ==================================================================
echo   BUILD OK - launch.bat re-decompiles automatically when you
echo   pass a NEW apk:  launch.bat C:\path\to\any.apk [--ui]
echo ==================================================================
endlocal
