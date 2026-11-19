@echo off
setlocal
set JAVA_EXE=C:\Program Files\Android\Android Studio\jbr\bin\java.exe
set RUNNER_DIR=C:\Users\Noval\.gemini\antigravity\scratch\android-runner
cd /d "%RUNNER_DIR%"
set BIN_DIR=%RUNNER_DIR%\bin
set JAR_FILE=%RUNNER_DIR%\lib\android-all-10.jar
set AGENT_JAR=%RUNNER_DIR%\lib\runner-agent.jar
set SQLITE_JAR=%RUNNER_DIR%\lib\sqlite-jdbc.jar
set DEXLIB_JAR=%RUNNER_DIR%\lib\dexlib2-2.5.2.jar
set GUAVA_JAR=%RUNNER_DIR%\lib\guava-27.1-android.jar
set ASM_JAR=%RUNNER_DIR%\lib\asm-9.6.jar
set ASM_COMMONS_JAR=%RUNNER_DIR%\lib\asm-commons-9.6.jar
set ENJARIFY_BAT=%RUNNER_DIR%\tools\enjarify\enjarify.bat

set TARGET_APK=C:\Users\Noval\.gemini\antigravity\scratch\headless-android\base.apk
set EXTRA_ARGS=

:parse_args
if "%~1"=="" goto done_args
set "arg=%~1"
if "%arg:~0,1%"=="-" (
    set "EXTRA_ARGS=%EXTRA_ARGS% %1"
) else (
    if /i "%arg:~-4%"==".apk" (
        set "TARGET_APK=%1"
    ) else (
        set "EXTRA_ARGS=%EXTRA_ARGS% %1"
    )
)
shift
goto parse_args
:done_args

set BASE_JAR=%RUNNER_DIR%\app.jar
set PATCHED_JAR=%RUNNER_DIR%\app-patched.jar

REM --- Generic auto-rebuild: any APK that has no decompiled jar yet gets one. ---
REM This is driven purely by file presence / timestamps, never by package name,
REM so it works identically for any target APK, not just the one under test.
if not exist "%BASE_JAR%" goto need_enjarify
for %%F in ("%TARGET_APK%") do set "APK_MTIME=%%~tF"
for %%F in ("%BASE_JAR%") do set "JAR_MTIME=%%~tF"
goto check_patched
:need_enjarify
echo [*] [AutoRebuild] app.jar missing - running enjarify to decompile the target APK...
if not exist "%ENJARIFY_BAT%" (
    echo [-] [AutoRebuild] enjarify.bat not found at %ENJARIFY_BAT% - cannot regenerate app.jar.
    echo [-] Run enjarify manually, then re-run launch.bat.
    goto after_rebuild
)
call "%ENJARIFY_BAT%" "%TARGET_APK%" -o "%BASE_JAR%" -f
if not exist "%BASE_JAR%" (
    echo [-] [AutoRebuild] enjarify did not produce app.jar. Check the output above for errors.
    goto after_rebuild
)
echo [+] [AutoRebuild] app.jar regenerated from %TARGET_APK%.

:check_patched
REM Annotation-recovery pass: safe to call unconditionally: AnnotationPipeline
REM itself no-ops and reuses app-patched.jar when it is already newer than the APK.
echo [*] [AutoRebuild] Ensuring annotations are recovered into app-patched.jar...
"%JAVA_EXE%" -cp "%BIN_DIR%;%DEXLIB_JAR%;%GUAVA_JAR%;%ASM_JAR%;%ASM_COMMONS_JAR%;%AGENT_JAR%;%JAR_FILE%" org.headless.runner.tool.annotation.AnnotationPipeline "%TARGET_APK%" "%BASE_JAR%" "%PATCHED_JAR%" "%RUNNER_DIR%\data"
:after_rebuild

if exist "%RUNNER_DIR%\lib\android-all-10-framed.jar" set JAR_FILE=%RUNNER_DIR%\lib\android-all-10-framed.jar

set APP_JAR=%PATCHED_JAR%
if not exist "%APP_JAR%" set APP_JAR=%BASE_JAR%
if not exist "%APP_JAR%" (
    echo [-] [AutoRebuild] Neither app-patched.jar nor app.jar exist. Cannot launch.
    endlocal
    exit /b 1
)

set CLASSPATH=%BIN_DIR%;%AGENT_JAR%;%JAR_FILE%;%SQLITE_JAR%;%DEXLIB_JAR%;%GUAVA_JAR%;%ASM_JAR%;%APP_JAR%

echo ==================================================================
echo   LAUNCHING NATIVE ANDROID RUNNER (ZERO VM / ZERO QEMU)
echo   Target: %TARGET_APK%
echo ==================================================================
"%JAVA_EXE%" -Xverify:none -XX:+UseSerialGC -XX:TieredStopAtLevel=1 -Xss512k -XX:ReservedCodeCacheSize=32m -cp "%CLASSPATH%" org.headless.runner.AppLauncher "%TARGET_APK%" %EXTRA_ARGS%
endlocal
