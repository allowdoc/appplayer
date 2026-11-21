@echo off
setlocal
set RUNNER_DIR=C:\Users\Noval\.gemini\antigravity\scratch\android-runner
set JAVA_EXE=C:\Program Files\Android\Android Studio\jbr\bin\java.exe
set CP=%RUNNER_DIR%\bin;%RUNNER_DIR%\lib\dexlib2-2.5.2.jar;%RUNNER_DIR%\lib\guava-27.1-android.jar;%RUNNER_DIR%\lib\asm-9.6.jar;%RUNNER_DIR%\lib\runner-agent.jar

"%JAVA_EXE%" -cp "%CP%" org.headless.runner.tool.annotation.AnnotationPipeline "C:\Users\Noval\.gemini\antigravity\scratch\headless-android\base.apk" "%RUNNER_DIR%\app.jar" "%RUNNER_DIR%\app-patched.jar" "%RUNNER_DIR%\data"
endlocal
