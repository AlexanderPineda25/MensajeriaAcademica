@echo off
rem Atajo para construir la distribucion del servidor.
rem Delega a dist\distribuir-servidor.cmd desde la raiz universidad-mensajeria.
cd /d "%~dp0\.."
call dist\distribuir-servidor.cmd
exit /b %ERRORLEVEL%
