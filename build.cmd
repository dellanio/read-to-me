@echo off
setlocal
set "JAVA_HOME=C:\Ambiente\Java\jdk-21"
set "GRADLE_USER_HOME=C:\Ambiente\Caches\Gradle"
set "ANDROID_HOME=C:\Ambiente\Android\Sdk"
set "PATH=%JAVA_HOME%\bin;%ANDROID_HOME%\platform-tools;%PATH%"

if "%~1"=="" (
    call C:\Ambiente\gradle-8.11.1\bin\gradle.bat assembleDebug
) else (
    call C:\Ambiente\gradle-8.11.1\bin\gradle.bat %*
)
endlocal
