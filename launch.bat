@echo off
setlocal
set JAVA_EXE=C:\Program Files\Android\Android Studio\jbr\bin\java.exe
set RUNNER_DIR=C:\Users\Noval\.gemini\antigravity\scratch\android-runner
set BIN_DIR=%RUNNER_DIR%\bin
set JAR_FILE=%RUNNER_DIR%\lib\android-all-10.jar
set AGENT_JAR=%RUNNER_DIR%\lib\runner-agent.jar
set APP_JAR=%RUNNER_DIR%\app-patched.jar
if not exist "%APP_JAR%" set APP_JAR=%RUNNER_DIR%\app.jar
set SQLITE_JAR=%RUNNER_DIR%\lib\sqlite-jdbc.jar
set DEXLIB_JAR=%RUNNER_DIR%\lib\dexlib2-2.5.2.jar
set GUAVA_JAR=%RUNNER_DIR%\lib\guava-27.1-android.jar
set ASM_JAR=%RUNNER_DIR%\lib\asm-9.6.jar
set CLASSPATH=%BIN_DIR%;%AGENT_JAR%;%JAR_FILE%;%SQLITE_JAR%;%DEXLIB_JAR%;%GUAVA_JAR%;%ASM_JAR%;%APP_JAR%

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

echo ==================================================================
echo   LAUNCHING NATIVE ANDROID RUNNER (ZERO VM / ZERO QEMU)
echo   Target: %TARGET_APK%
echo ==================================================================
"%JAVA_EXE%" -Xverify:none -XX:+UseSerialGC -XX:TieredStopAtLevel=1 -Xss512k -XX:ReservedCodeCacheSize=32m -cp "%CLASSPATH%" org.headless.runner.AppLauncher "%TARGET_APK%" %EXTRA_ARGS%
endlocal
