@echo off
chcp 65001 >nul
echo ========================================================
echo  ReadToMe - Compilador de Android App Bundle (.aab)
echo ========================================================
echo.

set JAVA_HOME=C:\Ambiente\Java\jdk-21
set ANDROID_HOME=C:\Ambiente\Android\Sdk

echo Compilando bundle de producao (bundleRelease)...
call C:\Ambiente\gradle-8.11.1\bin\gradle.bat bundleRelease

if %ERRORLEVEL% EQU 0 (
    echo.
    echo ========================================================
    echo  [SUCESSO] Bundle .aab gerado com sucesso!
    echo ========================================================
    echo.
    echo Arquivo pronto para upload no Google Play Console:
    echo app\build\outputs\bundle\release\app-release.aab
    echo.
    if exist "app\build\outputs\bundle\release\app-release.aab" (
        explorer /select,"app\build\outputs\bundle\release\app-release.aab"
    )
) else (
    echo.
    echo [ERRO] Falha na compilacao do bundle.
)

pause
