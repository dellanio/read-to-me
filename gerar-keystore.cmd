@echo off
chcp 65001 >nul
echo ========================================================
echo  ReadToMe - Gerador de Chave de Assinatura (Keystore)
echo ========================================================
echo.

set JAVA_HOME=C:\Ambiente\Java\jdk-21
set KEYTOOL="%JAVA_HOME%\bin\keytool.exe"

if not exist %KEYTOOL% (
    echo [ERRO] keytool nao encontrado em %KEYTOOL%
    pause
    exit /b 1
)

if exist "release.keystore" (
    echo [AVISO] O arquivo release.keystore ja existe nesta pasta!
    echo Para nao sobrescrever sua chave de producao, o processo foi cancelado.
    pause
    exit /b 0
)

echo Digite uma senha segura para o seu Keystore de producao:
set /p KEY_PASS="Senha: "

echo.
echo Gerando release.keystore (validade de 25 anos)...
%KEYTOOL% -genkeypair -v -keystore release.keystore -alias readtome -keyalg RSA -keysize 2048 -validity 10000 -storepass %KEY_PASS% -keypass %KEY_PASS% -dname "CN=ReadToMe, OU=Mobile, O=Dellanio, L=Recife, ST=PE, C=BR"

if %ERRORLEVEL% EQU 0 (
    echo.
    echo [SUCESSO] Keystore release.keystore gerado com sucesso!
    echo Configurando keystore.properties local...
    
    (
        echo storeFile=release.keystore
        echo storePassword=%KEY_PASS%
        echo keyAlias=readtome
        echo keyPassword=%KEY_PASS%
    ) > keystore.properties

    echo.
    echo Arquivo keystore.properties criado. O keystore esta protegido pelo .gitignore.
    echo Agora execute 'gerar-bundle.cmd' para compilar o App Bundle (.aab) para a Play Store!
) else (
    echo.
    echo [ERRO] Falha ao gerar keystore.
)

pause
