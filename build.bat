@echo off
setlocal enabledelayedexpansion
set JAVAC="C:\Program Files\Android\Android Studio\jbr\bin\javac.exe"
cd /d "C:\Users\Noval\.gemini\antigravity\scratch\android-runner"

if not exist bin mkdir bin
dir /s /b src\*.java > sources.txt
if exist app-patched.jar (
    set APP_JAR=app-patched.jar
) else (
    set APP_JAR=app.jar
)
%JAVAC% -encoding UTF-8 -cp "bin;lib\sqlite-jdbc.jar;lib\runner-agent.jar;lib\android-all-10.jar;lib\dexlib2-2.5.2.jar;lib\guava-27.1-android.jar;lib\asm-9.6.jar;lib\asm-commons-9.6.jar;%APP_JAR%" -d bin @sources.txt
echo Build finished with exit code %ERRORLEVEL%
endlocal
