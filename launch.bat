@echo off
setlocal enabledelayedexpansion
REM ==============================================================================
REM appplayer - Universal Android Runner launch script (Windows)
REM Usage: launch.bat <path-to-apk> [--ui] [--timeout N]
REM
REM Boot ANY apk: app.jar is decompiled automatically when missing OR when the
REM given APK differs from the one it was decompiled from (fingerprint marker).
REM ==============================================================================
set "RUNNER_DIR=%~dp0"
cd /d "%RUNNER_DIR%"

if "%~1"=="" (
    echo Usage: launch.bat ^<path-to-apk^> [--ui] [--timeout N]
    exit /b 1
)

set "TARGET_APK=%~f1"
set "TARGET_SIZE=%~z1"
if not exist "%TARGET_APK%" (
    echo [-] APK not found: %TARGET_APK%
    echo     Usage: launch.bat C:\path\to\app.apk [--ui] [--timeout N]
    exit /b 1
)
shift

REM ---- Locate java.exe ----
REM Strip literal quotes the user may have stored in JAVA_HOME.
if defined JAVA_HOME set JAVA_HOME=%JAVA_HOME:"=%
set "JAVA_EXE="
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" set "JAVA_EXE=%JAVA_HOME%\bin\java.exe"
if not defined JAVA_EXE (
    for /d %%D in ("%ProgramFiles%\Java\jdk-*") do if exist "%%D\bin\java.exe" set "JAVA_EXE=%%D\bin\java.exe"
)
if not defined JAVA_EXE (
    for /d %%D in ("%ProgramFiles%\Android\Android Studio\jbr") do if exist "%%D\bin\java.exe" set "JAVA_EXE=%%D\bin\java.exe"
)
if not defined JAVA_EXE (
    for /d %%D in ("%ProgramFiles%\Eclipse Adoptium\jdk-*") do if exist "%%D\bin\java.exe" set "JAVA_EXE=%%D\bin\java.exe"
)
if not defined JAVA_EXE (
    where java >nul 2>nul && set "JAVA_EXE=java"
)
if not defined JAVA_EXE (
    echo [-] No Java found. Install JDK/JRE 17+ or set JAVA_HOME.
    exit /b 1
)
set JAVA_EXE=%JAVA_EXE:"=%

REM ---- Engine classes required ----
if not exist "%RUNNER_DIR%bin\org\headless\runner\AppLauncher.class" (
    echo [-] Engine classes missing. Compile once first: build.bat C:\path\to\app.apk
    exit /b 1
)

set "BASE_JAR=%RUNNER_DIR%app.jar"
set "APP_JAR=%RUNNER_DIR%app-patched.jar"
set "APK_MARK=%RUNNER_DIR%data\.appjar-src.txt"

REM ---- Universal input classification ------------------------------------------
REM Android APKs take the full pipeline (enjarify, annotation recovery, overlay).
REM ANY other input (plain Java jar, .class, class directory, .apk without a
REM manifest) is a JAVA application: the launcher itself (UniversalEntryLoader)
REM discovers its entry point and runs it - no decompilation, no per-app work.
set "IS_ANDROID_APK=1"
powershell -NoProfile -Command "try { $z=[System.IO.Compression.ZipFile]::OpenRead('%TARGET_APK%'); $found=$false; foreach($e in $z.Entries){ if($e.FullName -eq 'AndroidManifest.xml'){$found=$true;break} }; $z.Dispose(); Write-Output ($found ? '1' : '0') } catch { Write-Output '1' }" > "%TEMP%\appplayer_isandroid.txt" 2>nul
set /p IS_ANDROID_APK=<"%TEMP%\appplayer_isandroid.txt"
del /f /q "%TEMP%\appplayer_isandroid.txt" 2>nul
if not "%IS_ANDROID_APK%"=="1" goto java_app_mode
goto android_pipeline

:java_app_mode
echo [*] [UniversalEntry] Java application input (no AndroidManifest.xml) - skipping APK pipeline.
set "OVERLAY_JAR="
set "RUNTIME_JAR="
set "JAR_FILE=%RUNNER_DIR%lib\android-all-10.jar"
if exist "%RUNNER_DIR%lib\android-all-10-framed.jar" set "JAR_FILE=%RUNNER_DIR%lib\android-all-10-framed.jar"
set "CLASSPATH=bin;%RUNNER_DIR%lib\runner-agent.jar"
set "CLASSPATH=%CLASSPATH%;%JAR_FILE%;%RUNNER_DIR%lib\sqlite-jdbc.jar;%RUNNER_DIR%lib\dexlib2-2.5.2.jar;%RUNNER_DIR%lib\guava-27.1-android.jar;%RUNNER_DIR%lib\asm-9.6.jar;%RUNNER_DIR%lib\asm-commons-9.6.jar"
for %%K in ("%RUNNER_DIR%lib\kotlin-stdlib-*.jar") do if exist "%%~K" set "CLASSPATH=%CLASSPATH%;%%~K"
for %%K in ("%RUNNER_DIR%lib\kotlin-reflect-*.jar") do if exist "%%~K" set "CLASSPATH=%CLASSPATH%;%%~K"
if exist "%RUNNER_DIR%lib\skija-shared-0.143.18.jar" set "CLASSPATH=%CLASSPATH%;%RUNNER_DIR%lib\skija-shared-0.143.18.jar;%RUNNER_DIR%lib\types-0.2.0.jar"
if exist "%RUNNER_DIR%lib\skija-windows-x64-0.143.18.jar" set "CLASSPATH=%CLASSPATH%;%RUNNER_DIR%lib\skija-windows-x64-0.143.18.jar"
goto launch_vm

:android_pipeline
REM ---- Auto-rebuild: decompile when app.jar is missing OR from a DIFFERENT apk ----
set "REBUILT=0"
if exist "%BASE_JAR%" (
    if exist "%APK_MARK%" (
        set /p MARK_DATA=<"%APK_MARK%"
        if /i not "!MARK_DATA!"=="%TARGET_APK%!TARGET_SIZE!" (
            echo [*] [AutoRebuild] APK changed since last decompile - rebuilding app.jar...
            goto decompile
        )
    ) else (
        echo [!] [AutoRebuild] app.jar provenance unknown ^(no marker^) - delete app.jar to force a rebuild if this is a different APK.
    )
    goto have_base_jar
) else (
    goto decompile
)

:decompile
echo [*] [AutoRebuild] Decompiling "%TARGET_APK%" via enjarify...
pushd "%RUNNER_DIR%tools\enjarify"
where python >nul 2>nul && python -O -m enjarify.main "%TARGET_APK%" -o "%BASE_JAR%" -f
if not exist "%BASE_JAR%" (
    where py >nul 2>nul && py -O -m enjarify.main "%TARGET_APK%" -o "%BASE_JAR%" -f
)
if not exist "%BASE_JAR%" (
    where python3 >nul 2>nul && python3 -O -m enjarify.main "%TARGET_APK%" -o "%BASE_JAR%" -f
)
popd
if not exist "%BASE_JAR%" (
    echo [-] enjarify failed. Install Python 3 ^(as "python" on PATH^) or place app.jar next to launch.bat.
    exit /b 1
)
if not exist "%RUNNER_DIR%data" mkdir "%RUNNER_DIR%data"
> "%APK_MARK%" echo %TARGET_APK%^|%TARGET_SIZE%
REM Invalidate everything derived from the previous apk.
if exist "%RUNNER_DIR%app-patched.jar" del /f /q "%RUNNER_DIR%app-patched.jar"
if exist "%RUNNER_DIR%app-patched.jar.frames.ok" del /f /q "%RUNNER_DIR%app-patched.jar.frames.ok"
if exist "%RUNNER_DIR%yoga-native-restored.jar" del /f /q "%RUNNER_DIR%yoga-native-restored.jar"
if exist "%RUNNER_DIR%yoga-native-restored.jar.frames.ok" del /f /q "%RUNNER_DIR%yoga-native-restored.jar.frames.ok"
if exist "%RUNNER_DIR%bin-app" rd /s /q "%RUNNER_DIR%bin-app"
echo [*] [AutoRebuild] Per-app overrides invalidated. Run build.bat "%TARGET_APK%" to rebuild them (optional).
set "REBUILT=1"

:have_base_jar
REM ---- Annotation recovery + stack-map frame repair (separate JVM, before launch) ----
REM enjarify emits Java 8 (v52) classes WITHOUT stack-map frames; JDK 13+ removed
REM the old-verifier failover and JDK 21+/25+ ignore -Xverify:none, so every class
REM must have frames recomputed BEFORE the app JVM starts. Always run the pipeline:
REM it reuses app-patched.jar when its APK-identity marker says it is current.
echo [*] [AutoRebuild] Verifying annotations + stack-map frames for the app jar...
"%JAVA_EXE%" -cp "bin;lib\runner-agent.jar;lib\android-all-10.jar;lib\dexlib2-2.5.2.jar;lib\guava-27.1-android.jar;lib\asm-9.6.jar;lib\asm-commons-9.6.jar;%BASE_JAR%" org.headless.runner.tool.annotation.AnnotationPipeline "%TARGET_APK%" "%BASE_JAR%" "%APP_JAR%" "%RUNNER_DIR%data" || echo [-] Annotation pipeline failed; continuing with unpatched app.jar.

REM ---- Ensure the Yoga native-method overlay exists (and is frame-verified) ----
set "OVERLAY_JAR=%RUNNER_DIR%yoga-native-restored.jar"
if not exist "%OVERLAY_JAR%" if exist "%APP_JAR%" (
    echo [*] [AutoRebuild] Regenerating yoga-native-restored.jar...
    "%JAVA_EXE%" -cp "bin;lib\asm-9.6.jar;lib\asm-commons-9.6.jar;lib\android-all-10.jar;%APP_JAR%" org.headless.runner.tool.rn.RestoreNativeMethods "%APP_JAR%" lib\android-all-10.jar "%OVERLAY_JAR%" "%TARGET_APK%"
)
if exist "%OVERLAY_JAR%" (
    "%JAVA_EXE%" -cp "bin;lib\asm-9.6.jar;lib\asm-commons-9.6.jar" org.headless.runner.tool.BytecodeFrameNormalizer "%OVERLAY_JAR%"
)

REM ---- Choose the jar for the app classpath (patched preferred) ----
if exist "%APP_JAR%" (
    set "RUNTIME_JAR=%APP_JAR%"
) else (
    set "RUNTIME_JAR=%BASE_JAR%"
)

REM ---- Classpath: shims first, then Yoga overlay (must shadow the framework's
REM ---- native graphics methods), then framework, then app, then SKIA renderer ----
set "JAR_FILE=%RUNNER_DIR%lib\android-all-10.jar"
if exist "%RUNNER_DIR%lib\android-all-10-framed.jar" set "JAR_FILE=%RUNNER_DIR%lib\android-all-10-framed.jar"

set "CLASSPATH=bin;%RUNNER_DIR%lib\runner-agent.jar"
if exist "%OVERLAY_JAR%" set "CLASSPATH=%CLASSPATH%;%OVERLAY_JAR%"
set "CLASSPATH=%CLASSPATH%;%JAR_FILE%;%RUNNER_DIR%lib\sqlite-jdbc.jar;%RUNNER_DIR%lib\dexlib2-2.5.2.jar;%RUNNER_DIR%lib\guava-27.1-android.jar;%RUNNER_DIR%lib\asm-9.6.jar;%RUNNER_DIR%lib\asm-commons-9.6.jar"
REM The APK itself is on the classpath so apps can load their PACKAGED RESOURCES
REM via classloader (joda-time tz data, .properties bundles, etc.) - same as
REM Android's PathClassLoader exposing APK entries as resources. classes.dex
REM inside is ignored by the JVM (not a .class file).
if exist "%TARGET_APK%" set "CLASSPATH=%CLASSPATH%;%TARGET_APK%"
if exist "%RUNNER_DIR%bin-app" set "CLASSPATH=%CLASSPATH%;%RUNNER_DIR%bin-app"
REM Official Kotlin runtime shadowing the app's dex-converted copies: the
REM dex->jar kotlin-reflect loses primitive-typeOf support (KotlinReflectionInternalError
REM "Unresolved class: float" in every Expo prop converter), so the environment
REM provides a working kotlin-reflect/kotlin-stdlib (env-level, app-agnostic).
for %%K in ("%RUNNER_DIR%lib\kotlin-stdlib-*.jar") do if exist "%%~K" set "CLASSPATH=%CLASSPATH%;%%~K"
for %%K in ("%RUNNER_DIR%lib\kotlin-reflect-*.jar") do if exist "%%~K" set "CLASSPATH=%CLASSPATH%;%%~K"
set "CLASSPATH=%CLASSPATH%;%RUNTIME_JAR%"
REM Skia renderer (Google Skia via Skija). Optional: falls back to Java2D player.
if exist "%RUNNER_DIR%lib\skija-shared-0.143.18.jar" set "CLASSPATH=%CLASSPATH%;%RUNNER_DIR%lib\skija-shared-0.143.18.jar;%RUNNER_DIR%lib\types-0.2.0.jar"
if exist "%RUNNER_DIR%lib\skija-windows-x64-0.143.18.jar" set "CLASSPATH=%CLASSPATH%;%RUNNER_DIR%lib\skija-windows-x64-0.143.18.jar"
if exist "%RUNNER_DIR%lib\skija-linux-x64-0.143.18.jar" set "CLASSPATH=%CLASSPATH%;%RUNNER_DIR%lib\skija-linux-x64-0.143.18.jar"

REM ---- JVM args file (options first, application + user args last) ----
REM ----------------------------------------------------------------
REM JVM launch via @argfile - immune to cmd quoting/expansion bugs.
REM The old single-line invocation broke on some machines (JVM received
REM no application argument and printed "Usage: java ..."). An argfile
REM cannot suffer that failure: each argument is written once, verbatim.
REM
REM Verifier note (JDK 25): -Xverify:none / -noverify are deprecated AND
REM ignored by JDK 21+ for rewritten bytecode. The diagnostic flags below
REM ARE honored and let the FrameNormalizer-produced app-patched.jar load.
REM ----------------------------------------------------------------
set "ARGFILE=%RUNNER_DIR%data\java-launch.args"
if not exist "%RUNNER_DIR%data" mkdir "%RUNNER_DIR%data"
del /f /q "%ARGFILE%" 2>nul

REM ---- Argfile-safe (forward-slash) copies of every path ------------------
REM CRITICAL: the JVM argfile tokenizer treats backslash as an escape
REM character inside quoted tokens and DROPS it ("\U" -> "U", "\N" -> "N").
REM A Windows path written to the argfile as C:\Users\... arrives as
REM C:Users... (drive-relative), which then resolves against the runner
REM directory and points at a nonexistent file ("APK not found"). Forward
REM slashes are token-safe and accepted by every JDK path API on Windows.
set "RUNNER_DIR_FS=%RUNNER_DIR:\=/%"
set "TARGET_APK_FS=%TARGET_APK:\=/%"
set "CLASSPATH_FS=%CLASSPATH:\=/%"

>>"%ARGFILE%" echo -XX:+UnlockDiagnosticVMOptions
>>"%ARGFILE%" echo -XX:-BytecodeVerificationLocal
>>"%ARGFILE%" echo -XX:-BytecodeVerificationRemote
>>"%ARGFILE%" echo -XX:+UseSerialGC
>>"%ARGFILE%" echo -XX:TieredStopAtLevel=1
>>"%ARGFILE%" echo -Xss4m
>>"%ARGFILE%" echo -XX:MaxMetaspaceSize=512m
>>"%ARGFILE%" echo -XX:ReservedCodeCacheSize=64m
>>"%ARGFILE%" echo -javaagent:lib/runner-agent.jar
>>"%ARGFILE%" echo "-Djava.library.path=%RUNNER_DIR_FS%native/win-x86_64"
>>"%ARGFILE%" echo "-Dheadless.runner.dir=%RUNNER_DIR_FS%."
>>"%ARGFILE%" echo "-Dheadless.runner.apk=%TARGET_APK_FS%"
>>"%ARGFILE%" echo -cp
>>"%ARGFILE%" echo "%CLASSPATH_FS%"

REM ---- Collect remaining args (each one straight into the argfile, after the
REM ---- main class + apk below; java treats everything after the application
REM ---- as application arguments) ----
>>"%ARGFILE%" echo org.headless.runner.AppLauncher
>>"%ARGFILE%" echo "%TARGET_APK_FS%"
:parse_args
if "%~1"=="" goto done_args
set "USER_ARG=%~1"
set "USER_ARG=%USER_ARG:\=/%"
>>"%ARGFILE%" echo %USER_ARG%
shift
goto parse_args
:done_args


:launch_vm
echo ==================================================================
echo   LAUNCHING NATIVE ANDROID RUNNER (ZERO VM / ZERO QEMU)
echo   Target: %TARGET_APK%
echo ==================================================================
echo [+] JVM args file: %ARGFILE% (bytecode verification handled via diagnostic flags)
"%JAVA_EXE%" @"%ARGFILE%"
endlocal
