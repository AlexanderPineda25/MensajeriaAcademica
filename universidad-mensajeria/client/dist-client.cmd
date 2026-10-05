@echo off
rem DIST-02: carpeta nativa del cliente JavaFX (Windows x64).
rem Salida: dist/MensajeriaClienteJava/ (client-app.jar + lib/ + javafx/ + client.properties + launch-client.cmd)
rem Uso: client\dist-client.cmd   (se ejecuta desde la raiz universidad-mensajeria)
setlocal EnableDelayedExpansion

cd /d "%~dp0\.."
if not exist "pom.xml" (
    echo [ERROR] Falta pom.xml: ejecutar desde universidad-mensajeria.
    exit /b 1
)

set DIST=dist\MensajeriaClienteJava
set JAVAFX_VERSION=25.0.1

rem `install` y no `package`: el paso 2 (dependency:copy-dependencies)
rem resuelve dto/codec/client-interfaces desde el repo local, y `package`
rem no los deja alli. Sin install, falla en cualquier maquina limpia.
echo [1/5] Compilando cliente...
call mvn -pl client/client-app -am install -DskipTests
if errorlevel 1 (
    echo [ERROR] Fallo la compilacion del cliente.
    exit /b 1
)

echo [2/5] Copiando dependencias runtime a %DIST%\lib ...
if exist "%DIST%" rmdir /s /q "%DIST%"
mkdir "%DIST%\lib" 2>nul
call mvn -pl client/client-app dependency:copy-dependencies "-DoutputDirectory=%CD%\%DIST%\lib" -DincludeScope=runtime -q
if errorlevel 1 (
    echo [ERROR] Fallo dependency:copy-dependencies.
    exit /b 1
)

echo [3/5] Copiando JAR + nativos JavaFX win + configuracion...
copy /y "client\client-app\target\client-app-1.0.0-SNAPSHOT.jar" "%DIST%\client-app.jar" >nul
if errorlevel 1 (
    echo [ERROR] No se hallo client-app-1.0.0-SNAPSHOT.jar.
    exit /b 1
)
rem Los nativos win (los *-win.jar) ya los resuelve dependency:copy-dependencies
rem en lib\; de ahi se copian a javafx\ (module-path del launcher). No se
rem buscan en %USERPROFILE%\.m2: el glob entrecomillado de `for` nunca
rem expande comodines y hacia fallar la distribucion en maquinas limpias.
mkdir "%DIST%\javafx" 2>nul
set FXCOUNT=0
for %%F in ("%DIST%\lib\*-win.jar") do (
    copy /y "%%F" "%DIST%\javafx\" >nul
    set /a FXCOUNT+=1
)
if !FXCOUNT! EQU 0 (
    echo [ERROR] No se hallaron nativos JavaFX win %JAVAFX_VERSION% en %DIST%\lib.
    exit /b 1
)
rem El javafx-*.jar sin clasificador son modulos vacios (302 B): en module-path
rem no aportan y confunden el arranque; solo se usan los *-win.jar.
copy /y "client\client-app\src\main\resources\client.properties" "%DIST%\client.properties" >nul
copy /y "client\launch-client.cmd" "%DIST%\launch-client.cmd" >nul
> "%DIST%\version.txt" echo MensajeriaClienteJava 1.0.0 JavaFX-%JAVAFX_VERSION% %DATE% %TIME%

echo [4/5] Verificando distribucion...
dir /b "%DIST%" | findstr /c:"client-app.jar" /c:"client.properties" /c:"launch-client.cmd" >nul
if errorlevel 1 (
    echo [ERROR] Faltan archivos en %DIST%.
    exit /b 1
)
dir /b "%DIST%\lib\*.jar" | findstr ".jar" >nul
if errorlevel 1 (
    echo [ERROR] %DIST%\lib sin jars.
    exit /b 1
)

echo [5/5] Contenido de %DIST%:
dir "%DIST%" | findstr /v "^ "
echo [OK] Distribucion lista en %DIST% (javafx: !FXCOUNT! jars, edita client.properties con la IP LAN).
rem El listado con findstr puede devolver 1: el exito real es haber llegado aqui.
exit /b 0
