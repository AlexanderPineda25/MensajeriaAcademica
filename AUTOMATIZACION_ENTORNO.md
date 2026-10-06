# Automatización del entorno del servidor

La automatización de Windows prepara el toolchain de desarrollo del servidor desde una cuenta
estándar. Descarga JDK y Maven en el perfil del usuario cuando faltan o no cumplen la versión
necesaria, verifica sus checksums y guarda `JAVA_HOME` y `PATH` en el ámbito **Usuario**. No
necesita elevar permisos y no cambia variables de máquina.

## Uso

Abre PowerShell normal, desde la carpeta `universidad-mensajeria`:

```powershell
# Solo diagnóstico: no descarga ni cambia variables
.\herramientas\preparar-entorno-servidor.ps1 -SoloVerificar

# Comprueba e instala JDK 21+ / Maven 3.9.x si hacen falta
.\herramientas\preparar-entorno-servidor.ps1

# Además, levanta el MySQL del compose si Docker Desktop ya está activo para tu usuario
.\herramientas\preparar-entorno-servidor.ps1 -IniciarMySql
```

También se puede indicar otra carpeta propia para las instalaciones:

```powershell
.\herramientas\preparar-entorno-servidor.ps1 -DirectorioHerramientas 'D:\Herramientas\Mensajeria'
```

La carpeta predeterminada es `%LOCALAPPDATA%\Mensajeria\herramientas`. El JDK descargado es
Eclipse Temurin 21 para Windows x64; Maven usa la distribución binaria oficial Apache 3.9.16.
La automatización consulta el release estable de Temurin 21 y verifica su SHA-256; Maven 3.9.16
se valida con el SHA-512 publicado por Apache. Las herramientas quedan aisladas del Java/Maven
instalado para todo el equipo. Si las variables de usuario cambian, cierra y vuelve a abrir la
terminal para que Windows las recargue.

## Qué comprueba

- Que hay un **JDK** 21 o superior, incluido `javac`; un JRE solo no sirve para compilar.
- Que Maven pertenece a la serie 3.9. Una versión 3.9 existente se conserva; si falta o es una
  versión incompatible, usa Maven 3.9.16 en la carpeta del usuario.
- Que Docker Compose v2 y el motor Docker están disponibles para esta sesión.
- Que responde un puerto TCP de MySQL en la dirección configurada en
  `server/servidor-app/src/main/resources/application.properties`.

El script no instala ni reemplaza Java/Maven de sistema, no ejecuta builds ni tests, no instala
drivers de base de datos por separado y no toca el firewall. Maven resuelve las dependencias Java
del proyecto en `%USERPROFILE%\.m2` al compilar.

## Límite de permisos

JDK y Maven son archivos portables y se pueden preparar bajo `%LOCALAPPDATA%` sin administrador.
Docker Desktop y WSL requieren instalación y, según la política del equipo, habilitación de
virtualización/servicios con permisos de administrador. La automatización solo los detecta; no
intenta elevarse ni saltarse políticas. Si ya están disponibles y el motor está iniciado,
`-IniciarMySql` ejecuta `docker compose up -d mysql` desde el monorepo. Si Docker no está
disponible, se puede usar una instancia MySQL accesible en la URL configurada, o pedir al equipo
de soporte que habilite Docker/WSL.

Que el puerto TCP responda confirma conectividad básica, no credenciales, esquema ni salud de
MySQL. La aplicación verifica credenciales y aplica migraciones Flyway al iniciar.

## Requisitos y errores frecuentes

- Windows x64, PowerShell y conexión a Internet para descargar JDK/Maven.
- Ejecuta desde `universidad-mensajeria`, que contiene el `pom.xml` reactor y `docker-compose.yml`.
- Si la red corporativa bloquea Adoptium o Apache, configura acceso a esos dominios con soporte;
  el script falla sin instalar un archivo cuyo checksum no coincida.
- Si PowerShell indica que una política de la organización impide ejecutar scripts, pide al
  administrador del equipo que autorice este archivo. No hace falta cambiar la política del
  computador para instalar JDK o Maven.
- Un resultado MySQL sin respuesta no invalida JDK/Maven. Indica que hay que iniciar MySQL o
  ajustar la URL de base de datos antes de arrancar el servidor.

Para conocer los requisitos completos de cada cliente y de distribución, continúa en
[INSTALACION.md](INSTALACION.md). Para el servidor desplegado sin compilar, consulta
[DESPLIEGUE.md](DESPLIEGUE.md).
